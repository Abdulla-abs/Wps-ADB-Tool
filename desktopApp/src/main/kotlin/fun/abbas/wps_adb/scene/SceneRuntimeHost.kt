package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.bridge.CefBridgeTransport
import `fun`.abbas.wps_adb.bridge.CefJsBridge
import `fun`.abbas.wps_adb.bridge.SceneBridgeHostController
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeTransport
import `fun`.abbas.wps_adb.data.scene.bridge.CURRENT_BRIDGE_PROTOCOL_VERSION
import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneBridgeChannel
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeChannel
import `fun`.abbas.wps_adb.data.scene.DefaultSceneImporter
import `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository
import `fun`.abbas.wps_adb.data.scene.SceneImporter
import `fun`.abbas.wps_adb.data.scene.SceneImportResult
import `fun`.abbas.wps_adb.data.scene.SceneStore
import `fun`.abbas.wps_adb.data.scene.SceneValidationError
import `fun`.abbas.wps_adb.data.scene.runtime.SceneRuntimeController
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import `fun`.abbas.wps_adb.spike.renderer.CefHostManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

private const val RENDERER_READY_TIMEOUT_MS = 45_000L

sealed interface SceneDeleteResult {
    val isDeleted: Boolean

    data object Success : SceneDeleteResult {
        override val isDeleted: Boolean = true
    }

    data class NotDeleted(val reason: String? = null) : SceneDeleteResult {
        override val isDeleted: Boolean = false
    }

    data class DeletedFallbackFailed(val error: String) : SceneDeleteResult {
        override val isDeleted: Boolean = true
    }
}

/**
 * Orchestrates JCEF browser lifecycle, [CefBridgeTransport] wiring, and the single [SceneBridgeHostController].
 *
 * Responsibilities:
 * - Creates and manages embedded JCEF browser and HTTP server.
 *   (NOTE: In Commit 1 minimal slice, CefHostManager serves transitional spike-renderer resources
 *   until Commit 9 bundles the production renderer-runtime build).
 * - Bridges raw JS IPC events to [CefBridgeTransport].
 * - Configures [SceneBridgeChannel] and connects to [SceneBridgeHostController].
 * - Exposes UI-ready [SceneRuntimeState] and AWT [Component] for Compose embedding.
 * - Safe lifecycle teardown: Non-cancellable graceful closing of channel -> transport -> browser.
 */
class SceneRuntimeHost(
    parentScope: CoroutineScope,
    val sceneRuntimeController: SceneRuntimeController? = null,
    val sceneRepository: `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository? = null,
    val initialActiveSceneId: String? = null,
    val onActiveSceneIdChanged: ((String) -> Unit)? = null,
    private val customTransport: BridgeTransport? = null,
    private val cefHostManagerProvider: (() -> CefHostManager)? = null,
    val resourceRoot: String = "scene-runtime",
    val scenesRoot: java.io.File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val rendererReadyTimeoutMs: Long = RENDERER_READY_TIMEOUT_MS,
) {
    private val hostJob = SupervisorJob(parentScope.coroutineContext[kotlinx.coroutines.Job])
    val hostScope = CoroutineScope(parentScope.coroutineContext + hostJob)
    private val cleanupJob = SupervisorJob()
    private val cleanupScope = CoroutineScope(cleanupJob + ioDispatcher)

    private val isDisposed = AtomicBoolean(false)
    private val rendererAttempt = AtomicLong(0L)
    private val viewportMountedOnce = AtomicBoolean(false)
    private val rendererControlMutex = Mutex()
    @Volatile private var rendererInitJob: Job? = null

    private val _state = MutableStateFlow(SceneRuntimeState())
    val state: StateFlow<SceneRuntimeState> = _state.asStateFlow()

    private val _availableScenes = MutableStateFlow<List<SceneOption>>(emptyList())
    val availableScenes: StateFlow<List<SceneOption>> = _availableScenes.asStateFlow()

    val sceneImporter: SceneImporter? = (sceneRepository as? SceneStore)?.let { DefaultSceneImporter(it) }

    fun refreshScenes() {
        val repo = sceneRepository ?: return
        val list = try {
            repo.listScenes().map { SceneOption(it.id, it.name) }
        } catch (_: Throwable) {
            emptyList()
        }
        _availableScenes.value = list
    }

    private val sceneLifecycleMutex = Mutex()
    private var activeEpoch: Long = 0L
    private var isSwitchingScene: Boolean = false

    suspend fun selectScene(sceneId: String): Boolean = sceneLifecycleMutex.withLock {
        internalSelectSceneLocked(sceneId)
    }

    private suspend fun internalSelectSceneLocked(sceneId: String): Boolean {
        val repository = sceneRepository ?: return false
        val currentId = _state.value.activeSceneId
        if (currentId == sceneId) return true

        val previousEpoch = synchronized(this) { activeEpoch }
        // 1. Gate incoming events from old scene
        synchronized(this) {
            isSwitchingScene = true
            activeEpoch++
        }

        var committed = false
        try {
            // 2. Wait for pending writes of old scene to flush to disk
            if (currentId != null) {
                persistenceCoordinator?.flushScene(currentId)
            }

            // 3. Load target scene from repository
            val scene = withContext(ioDispatcher) {
                repository.loadScene(sceneId)
            }

            // 4. Update controller and UI state
            sceneRuntimeController?.setScene(scene)
            _state.update { it.copy(activeSceneId = scene.id) }
            synchronized(this) { isSwitchingScene = false }
            onActiveSceneIdChanged?.invoke(scene.id)
            committed = true
            return true
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            System.err.println("[SceneRuntimeHost] Failed to switch to scene '$sceneId': ${t.message}")
            return false
        } finally {
            if (!committed) {
                withContext(NonCancellable) {
                    synchronized(this@SceneRuntimeHost) {
                        isSwitchingScene = false
                        activeEpoch = previousEpoch
                    }
                }
            }
        }
    }

    fun setInteractionMode(mode: `fun`.abbas.wps_adb.model.scene.SceneInteractionMode) {
        _state.update { it.copy(interactionMode = mode) }
        hostScope.launch {
            hostController.setInteractionMode(mode)
        }
    }

    suspend fun importScene(sourceFile: java.io.File, sceneName: String? = null): SceneImportResult = sceneLifecycleMutex.withLock {
        val importer = sceneImporter
            ?: return@withLock SceneImportResult.Failure(
                error = SceneValidationError.StorageFailure("SceneImporter is not available"),
                message = "Scene repository does not support importing",
            )
        val result = withContext(ioDispatcher) {
            importer.importEnvironment(sourceFile, sceneName)
        }
        if (result is SceneImportResult.Success) {
            refreshScenes()
            internalSelectSceneLocked(result.scene.id)
        }
        return@withLock result
    }

    suspend fun deleteScene(sceneId: String): SceneDeleteResult = sceneLifecycleMutex.withLock {
        val repo = sceneRepository ?: return@withLock SceneDeleteResult.NotDeleted("Scene repository is not available")
        val isActive = _state.value.activeSceneId == sceneId

        val previousEpoch = synchronized(this) { activeEpoch }
        // If deleting active scene, gate events
        if (isActive) {
            synchronized(this) {
                isSwitchingScene = true
                activeEpoch++
            }
        }

        var committed = false
        try {
            val fallbackScene = if (isActive) {
                val remaining = withContext(ioDispatcher) {
                    repo.listScenes().filterNot { it.id == sceneId }
                }
                if (remaining.isNotEmpty()) {
                    remaining.first()
                } else {
                    val def = createDefaultScene()
                    try {
                        withContext(ioDispatcher) { repo.saveScene(def) }
                        def
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        System.err.println("[SceneRuntimeHost] Failed to save default fallback scene: ${t.message}")
                        null
                    }
                }
            } else {
                null
            }

            if (isActive && fallbackScene == null) {
                System.err.println("[SceneRuntimeHost] Aborting deletion of active scene '$sceneId': unable to prepare fallback scene")
                return@withLock SceneDeleteResult.NotDeleted("Unable to prepare fallback scene")
            }

            // Once fallback is resolved, perform disk deletion and fallback activation
            // as an atomic lifecycle commit under NonCancellable.
            val result = withContext(NonCancellable) {
                val res = withContext(ioDispatcher) {
                    repo.deleteScene(sceneId)
                }
                if (!res) {
                    return@withContext SceneDeleteResult.NotDeleted("Repository failed to delete scene '$sceneId'")
                }

                persistenceCoordinator?.discardScene(sceneId)
                refreshScenes()
                if (isActive) {
                    val targetFallback = requireNotNull(fallbackScene)
                    val activated = try {
                        internalSelectSceneLocked(targetFallback.id)
                    } catch (t: Throwable) {
                        System.err.println("[SceneRuntimeHost] Failed to activate fallback scene '${targetFallback.id}': ${t.message}")
                        false
                    }
                    if (!activated) {
                        val emergencyActivated = try {
                            val def = createDefaultScene()
                            withContext(ioDispatcher) { repo.saveScene(def) }
                            internalSelectSceneLocked(def.id)
                        } catch (t: Throwable) {
                            System.err.println("[SceneRuntimeHost] Failed to create emergency default scene: ${t.message}")
                            false
                        }
                        if (!emergencyActivated) {
                            val errorMessage = "Active scene '$sceneId' was deleted, but failed to activate fallback scene '${targetFallback.id}'"
                            sceneRuntimeController?.setScene(null)
                            _state.update {
                                it.copy(
                                    activeSceneId = null,
                                    phase = SceneRuntimePhase.FAILED,
                                    failure = SceneRuntimeFailure(
                                        stage = SceneRuntimePhase.LOADING_SCENE,
                                        category = SceneRuntimeFailureCategory.SCENE_LOAD,
                                        userMessage = errorMessage,
                                        diagnosticId = UUID.randomUUID().toString(),
                                    ),
                                )
                            }
                            synchronized(this@SceneRuntimeHost) { isSwitchingScene = false }
                            committed = true
                            return@withContext SceneDeleteResult.DeletedFallbackFailed(errorMessage)
                        }
                    }
                }
                committed = true
                SceneDeleteResult.Success
            }
            return@withLock result
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            System.err.println("[SceneRuntimeHost] Failed to delete scene '$sceneId': ${t.message}")
            return@withLock SceneDeleteResult.NotDeleted(t.message)
        } finally {
            if (isActive && !committed) {
                withContext(NonCancellable) {
                    synchronized(this@SceneRuntimeHost) {
                        isSwitchingScene = false
                        activeEpoch = previousEpoch
                    }
                }
            }
        }
    }

    suspend fun importAsset(sceneId: String, sourceFile: java.io.File, assetName: String? = null): SceneImportResult = sceneLifecycleMutex.withLock {
        val importer = sceneImporter
            ?: return@withLock SceneImportResult.Failure(
                error = SceneValidationError.StorageFailure("SceneImporter is not available"),
                message = "Scene repository does not support importing",
            )
        val result = withContext(ioDispatcher) {
            importer.importAsset(sceneId, sourceFile, assetName)
        }
        if (result is SceneImportResult.Success) {
            if (_state.value.activeSceneId == sceneId) {
                sceneRuntimeController?.updateScene(result.scene)
            }
            refreshScenes()
        }
        return@withLock result
    }

    suspend fun deleteAsset(sceneId: String, assetId: String): Boolean = sceneLifecycleMutex.withLock {
        val repo = sceneRepository ?: return@withLock false
        persistenceCoordinator?.cancelTransformSave(sceneId, assetId)
        return@withLock try {
            val updated = withContext(ioDispatcher) {
                repo.deleteAsset(sceneId, assetId)
            }
            if (_state.value.activeSceneId == sceneId) {
                sceneRuntimeController?.updateScene(updated)
            }
            refreshScenes()
            true
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            System.err.println("[SceneRuntimeHost] Failed to delete asset '$assetId' from scene '$sceneId': ${t.message}")
            false
        }
    }

    var browserComponent: Component? = null
        private set

    private var cefHostManager: CefHostManager? = null
    private var bridgeAdapter: CefHostManagerBridgeAdapter? = null

    val persistenceCoordinator: ScenePersistenceCoordinator? = sceneRepository?.let {
        ScenePersistenceCoordinator(
            repository = it,
            parentScope = hostScope,
            ioDispatcher = ioDispatcher,
        )
    }

    val channel: SceneBridgeChannel
    val hostController: SceneBridgeHostController

    init {
        val transport: BridgeTransport
        if (customTransport != null) {
            transport = customTransport
        } else {
            val adapter = CefHostManagerBridgeAdapter()
            bridgeAdapter = adapter
            transport = CefBridgeTransport(cefBridge = adapter)
        }

        channel = DefaultSceneBridgeChannel(
            transport = transport,
            scope = hostScope,
        )

        hostController = SceneBridgeHostController(
            channel = channel,
            scope = hostScope,
            eventValidator = { eventSceneId, eventEpoch ->
                synchronized(this) {
                    if (isSwitchingScene) return@synchronized false
                    val currentSceneId = _state.value.activeSceneId ?: return@synchronized false
                    if (eventSceneId == null || eventSceneId != currentSceneId) {
                        return@synchronized false
                    }
                    if (eventEpoch == null || eventEpoch != activeEpoch) {
                        return@synchronized false
                    }
                    true
                }
            },
            epochProvider = {
                synchronized(this) { activeEpoch }
            },
            onTransformChanged = { eventSceneId, eventEpoch, objectId, transform ->
                val targetSceneId = synchronized(this) {
                    if (isSwitchingScene) return@synchronized null
                    val currentSceneId = _state.value.activeSceneId ?: return@synchronized null
                    if (eventSceneId == null || eventSceneId != currentSceneId) return@synchronized null
                    if (eventEpoch == null || eventEpoch != activeEpoch) return@synchronized null
                    currentSceneId
                }
                if (targetSceneId != null) {
                    persistenceCoordinator?.scheduleTransformSave(targetSceneId, objectId, transform)
                }
            },
            onCameraChanged = { eventSceneId, eventEpoch, camera ->
                val targetSceneId = synchronized(this) {
                    if (isSwitchingScene) return@synchronized null
                    val currentSceneId = _state.value.activeSceneId ?: return@synchronized null
                    if (eventSceneId == null || eventSceneId != currentSceneId) return@synchronized null
                    if (eventEpoch == null || eventEpoch != activeEpoch) return@synchronized null
                    currentSceneId
                }
                if (targetSceneId != null) {
                    persistenceCoordinator?.scheduleCameraSave(targetSceneId, camera)
                }
            },
            onSceneSynchronized = { _ ->
                if (!isDisposed.get() && channel.state.value == BridgeConnectionState.READY) {
                    setRuntimePhase(SceneRuntimePhase.READY, rendererAttempt.get())
                }
            },
        )

        // Observe connection state changes
        hostScope.launch {
            channel.state.collect { connState ->
                var reportedFailure: SceneRuntimeFailure? = null
                _state.update { current ->
                    val failure = if (
                        connState == BridgeConnectionState.ERROR &&
                        !isDisposed.get() &&
                        current.phase !in setOf(SceneRuntimePhase.DISPOSING, SceneRuntimePhase.DISPOSED)
                    ) {
                        current.failure ?: SceneRuntimeFailure(
                            stage = current.phase,
                            category = SceneRuntimeFailureCategory.BRIDGE_HANDSHAKE,
                            userMessage = "3D 渲染器连接中断。请重试，或返回设备墙。",
                            diagnosticId = UUID.randomUUID().toString(),
                        ).also { reportedFailure = it }
                    } else {
                        current.failure
                    }
                    val phase = when {
                        isDisposed.get() -> current.phase
                        connState == BridgeConnectionState.READY && current.phase in setOf(
                            SceneRuntimePhase.WAITING_BRIDGE,
                            SceneRuntimePhase.READY,
                        ) -> SceneRuntimePhase.LOADING_SCENE
                        connState == BridgeConnectionState.ERROR && current.phase !in setOf(SceneRuntimePhase.DISPOSING, SceneRuntimePhase.DISPOSED) -> SceneRuntimePhase.FAILED
                        else -> current.phase
                    }
                    current.copy(connectionState = connState, phase = phase, failure = failure)
                }
                reportedFailure?.let {
                    SceneRuntimeLog.failure(it, IllegalStateException("Bridge entered ERROR state"))
                }
            }
        }

        // Initialize active scene and bind controller
        if (sceneRuntimeController != null) {
            hostController.bind(sceneRuntimeController)

            hostScope.launch(ioDispatcher) {
                val initialScene = if (sceneRepository != null) {
                    val configuredScene = initialActiveSceneId?.let { id ->
                        try {
                            sceneRepository.loadScene(id)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    if (configuredScene != null) {
                        configuredScene
                    } else {
                        val scenes = sceneRepository.listScenes()
                        if (scenes.isNotEmpty()) {
                            scenes.first()
                        } else {
                            try {
                                val defaultScene = createDefaultScene()
                                sceneRepository.saveScene(defaultScene)
                                defaultScene
                            } catch (_: Throwable) {
                                createDefaultScene()
                            }
                        }
                    }
                } else {
                    sceneRuntimeController.activeScene.value ?: createDefaultScene()
                }

                refreshScenes()

                withContext(mainDispatcher) {
                    sceneRuntimeController.setScene(initialScene)
                    _state.update { it.copy(activeSceneId = initialScene.id) }

                    if (initialActiveSceneId != initialScene.id) {
                        onActiveSceneIdChanged?.invoke(initialScene.id)
                    }
                }
            }

            hostScope.launch {
                sceneRuntimeController.activeScene.collect { scene ->
                    val newId = scene?.id
                    _state.update { it.copy(activeSceneId = newId) }
                    if (newId != null) {
                        onActiveSceneIdChanged?.invoke(newId)
                    }
                }
            }
        } else {
            // Standalone test / fallback setup
            val initialScene = createDefaultScene()
            _state.update { it.copy(activeSceneId = initialScene.id) }
            hostScope.launch {
                hostController.onSceneStateChanged(
                    ResolvedSceneState(scene = initialScene, bindings = emptyList())
                )
            }
        }

        // Start channel listening
        hostScope.launch {
            channel.connect()
        }

        // If not using custom transport, initialize JCEF on background thread
        if (customTransport == null) {
            initializeJcefBrowser()
        } else {
            _state.update { it.copy(phase = SceneRuntimePhase.WAITING_BRIDGE) }
        }
    }

    private fun initializeJcefBrowser() {
        val attempt = rendererAttempt.incrementAndGet()
        rendererInitJob = hostScope.launch { runRendererAttempt(attempt, reconnectChannel = false) }
    }

    private suspend fun runRendererAttempt(
        attempt: Long,
        reconnectChannel: Boolean,
        managerToReuse: CefHostManager? = null,
    ) {
        var stage = SceneRuntimePhase.INITIALIZING_CEF
        var manager: CefHostManager? = null
        try {
            setRuntimePhase(stage, attempt, clearFailure = true)
            val resolvedScenesRoot = withContext(ioDispatcher) {
                scenesRoot
                    ?: (sceneRepository as? SceneStore)?.getScenesRoot()
                    ?: `fun`.abbas.wps_adb.data.AppDataPaths.defaultScenesRoot()
            }
            val activeManager = managerToReuse ?: withContext(ioDispatcher) {
                cefHostManagerProvider?.invoke() ?: CefHostManager(
                    resourceRoot = resourceRoot,
                    scenesRoot = resolvedScenesRoot,
                    onPageLoadError = { code -> reportPageLoadFailure(attempt, code) },
                ) { rawMessage -> bridgeAdapter?.handleIncomingJsMessage(rawMessage) }
            }
            if (!isAttemptActive(attempt)) {
                withContext(ioDispatcher) { activeManager.dispose() }
                return
            }

            manager = activeManager
            cefHostManager = activeManager
            activeManager.setPageLoadErrorListener { code -> reportPageLoadFailure(attempt, code) }
            bridgeAdapter?.bind(activeManager)
            if (reconnectChannel) channel.connect()

            stage = SceneRuntimePhase.INITIALIZING_CEF
            withContext(ioDispatcher) { activeManager.ensureCefAppInitialized() }
            checkAttemptActive(attempt)

            stage = SceneRuntimePhase.CREATING_BROWSER
            setRuntimePhase(stage, attempt)
            val comp = withContext(mainDispatcher) {
                val component = activeManager.createBrowserOnEdt()
                browserComponent = component
                setRuntimePhase(SceneRuntimePhase.WAITING_BRIDGE, attempt)
                component
            }
            if (!isAttemptActive(attempt)) {
                withContext(ioDispatcher) { manager.dispose() }
                return
            }
            stage = SceneRuntimePhase.WAITING_BRIDGE
            val completedState = withTimeout(rendererReadyTimeoutMs) {
                state.first {
                    it.phase == SceneRuntimePhase.READY ||
                        it.phase == SceneRuntimePhase.FAILED ||
                        !isAttemptActive(attempt)
                }
            }
            checkAttemptActive(attempt)
            check(completedState.phase == SceneRuntimePhase.READY) {
                "Renderer bridge entered ${completedState.connectionState} before scene synchronization"
            }
        } catch (t: Throwable) {
            if (t is CancellationException && t !is TimeoutCancellationException) throw t
            if (!isAttemptActive(attempt)) return
            val category = when (stage) {
                SceneRuntimePhase.INITIALIZING_CEF -> SceneRuntimeFailureCategory.CEF_INITIALIZATION
                SceneRuntimePhase.CREATING_BROWSER -> SceneRuntimeFailureCategory.BROWSER_CREATION
                SceneRuntimePhase.WAITING_BRIDGE -> SceneRuntimeFailureCategory.BRIDGE_HANDSHAKE
                SceneRuntimePhase.LOADING_SCENE -> SceneRuntimeFailureCategory.SCENE_LOAD
                else -> SceneRuntimeFailureCategory.UNKNOWN
            }
            val failure = _state.value.failure ?: SceneRuntimeFailure(
                stage = stage,
                category = category,
                userMessage = when (category) {
                    SceneRuntimeFailureCategory.CEF_INITIALIZATION -> "3D 渲染器初始化失败。请重试，或返回设备墙。"
                    SceneRuntimeFailureCategory.BROWSER_CREATION -> "3D 浏览器创建失败。请重试，或返回设备墙。"
                    SceneRuntimeFailureCategory.BRIDGE_HANDSHAKE -> "3D 渲染器连接超时或失败。请重试，或返回设备墙。"
                    SceneRuntimeFailureCategory.SCENE_LOAD -> "场景加载失败。场景数据已保留，请重试或切换场景。"
                    else -> "3D 运行时启动失败。请重试，或返回设备墙。"
                },
                diagnosticId = UUID.randomUUID().toString(),
            )
            if (_state.value.failure == null) SceneRuntimeLog.failure(failure, t)
            withContext(mainDispatcher) {
                if (isAttemptActive(attempt)) {
                    browserComponent = null
                    _state.update { it.copy(phase = SceneRuntimePhase.FAILED, failure = failure) }
                }
            }
        }
    }

    suspend fun retryRenderer(): Boolean = rendererControlMutex.withLock {
        if (customTransport != null || isDisposed.get()) return@withLock false
        rendererInitJob?.cancelAndJoin()
        if (isDisposed.get()) return@withLock false

        val reusableManager = cefHostManager
        try {
            channel.disconnect()
        } catch (t: Throwable) {
            SceneRuntimeLog.cleanupFailure("bridge-channel", t)
        }
        withContext(mainDispatcher) { browserComponent = null }

        val attempt = rendererAttempt.incrementAndGet()
        _state.update { current ->
            if (isDisposed.get()) current else current.copy(phase = SceneRuntimePhase.INITIALIZING_CEF, failure = null)
        }
        rendererInitJob = hostScope.launch {
            runRendererAttempt(attempt, reconnectChannel = true, managerToReuse = reusableManager)
        }
        true
    }

    /** A detached JCEF native component must not be reparented into a new SwingPanel. */
    suspend fun prepareViewportMount(): Boolean {
        if (isDisposed.get()) return false
        if (viewportMountedOnce.compareAndSet(false, true)) return true
        return retryRenderer()
    }

    private fun isAttemptActive(attempt: Long): Boolean =
        !isDisposed.get() && rendererAttempt.get() == attempt

    private fun checkAttemptActive(attempt: Long) {
        if (!isAttemptActive(attempt)) throw CancellationException("Scene renderer attempt is no longer active")
    }

    private fun setRuntimePhase(
        phase: SceneRuntimePhase,
        attempt: Long,
        clearFailure: Boolean = false,
    ) {
        if (!isAttemptActive(attempt)) return
        val previous = _state.value.phase
        _state.update { current ->
            if (!isAttemptActive(attempt)) current
            else current.copy(phase = phase, failure = if (clearFailure) null else current.failure)
        }
        if (previous != phase) SceneRuntimeLog.transition(previous, phase, attempt)
    }

    private fun reportPageLoadFailure(attempt: Long, errorCode: String) {
        if (!isAttemptActive(attempt)) return
        hostScope.launch(mainDispatcher) {
            if (!isAttemptActive(attempt) || _state.value.phase !in setOf(
                    SceneRuntimePhase.CREATING_BROWSER,
                    SceneRuntimePhase.WAITING_BRIDGE,
                )
            ) return@launch
            val failure = SceneRuntimeFailure(
                stage = _state.value.phase,
                category = SceneRuntimeFailureCategory.PAGE_LOAD,
                userMessage = "3D 渲染页面加载失败。请重试，或返回设备墙。",
                diagnosticId = UUID.randomUUID().toString(),
            )
            SceneRuntimeLog.failure(failure, IllegalStateException("CEF page load error $errorCode"))
            _state.update { current ->
                if (!isAttemptActive(attempt)) current else current.copy(
                    phase = SceneRuntimePhase.FAILED,
                    failure = failure,
                )
            }
        }
    }

    private fun createDefaultScene(): DeviceScene {
        return DeviceScene(
            id = "scene_default",
            name = "Default 3D Scene",
            camera = SceneCamera(
                position = SceneVector3(0.0, 4.0, 9.0),
                target = SceneVector3(0.0, 1.0, 0.0),
                fov = 45.0,
            ),
            assets = emptyList(),
            bindings = emptyList(),
            bindableObjectIds = `fun`.abbas.wps_adb.model.scene.DEFAULT_BINDABLE_OBJECT_IDS,
        )
    }

    /**
     * Asynchronous graceful teardown ensuring channel disconnection and transport closure
     * before cancelling background coroutines.
     */
    suspend fun close() {
        if (!isDisposed.compareAndSet(false, true)) {
            return
        }
        rendererAttempt.incrementAndGet()
        rendererInitJob?.cancel()
        _state.update { it.copy(phase = SceneRuntimePhase.DISPOSING) }
        withContext(NonCancellable) {
            rendererControlMutex.withLock {
                rendererInitJob?.join()
                sceneLifecycleMutex.withLock {
                    SceneRuntimeLog.transition(SceneRuntimePhase.DISPOSING, SceneRuntimePhase.DISPOSED, rendererAttempt.get())
                    synchronized(this@SceneRuntimeHost) {
                        isSwitchingScene = true
                        activeEpoch++
                    }
                    try {
                        hostController.dispose()
                    } catch (t: Throwable) {
                        SceneRuntimeLog.cleanupFailure("bridge-controller", t)
                    }
                    try {
                        persistenceCoordinator?.close()
                    } catch (t: Throwable) {
                        SceneRuntimeLog.cleanupFailure("persistence-coordinator", t)
                    }
                    try {
                        channel.disconnect()
                    } catch (t: Throwable) {
                        SceneRuntimeLog.cleanupFailure("bridge-channel", t)
                    }
                    try {
                        cefHostManager?.dispose()
                    } catch (t: Throwable) {
                        SceneRuntimeLog.cleanupFailure("cef-manager", t)
                    } finally {
                        cefHostManager = null
                        bridgeAdapter = null
                        browserComponent = null
                        _state.update { it.copy(phase = SceneRuntimePhase.DISPOSED) }
                        hostJob.cancel()
                        cleanupJob.cancel()
                    }
                }
            }
        }
    }

    /**
     * Synchronous disposal for non-suspending callers (e.g. Window onCloseRequest).
     * Thread-safe and idempotent. Does NOT block the calling thread — cleanup
     * runs asynchronously on [ioDispatcher] to avoid EDT deadlock.
     */
    fun dispose() {
        if (isDisposed.get()) return
        // Cleanup must remain schedulable even if the UI parent scope is already cancelled.
        cleanupScope.launch { close() }
    }

    /**
     * Internal adapter between [CefHostManager] and [CefJsBridge].
     */
    class CefHostManagerBridgeAdapter : CefJsBridge {
        private var receiveListener: ((String) -> Unit)? = null
        private var hostManager: CefHostManager? = null

        fun bind(manager: CefHostManager?) {
            this.hostManager = manager
        }

        fun handleIncomingJsMessage(rawMessage: String) {
            try {
                val json = JSONObject(rawMessage)
                val type = json.optString("type", "")

                if (type == "SCENE_READY") {
                    // Inject window.cefBridge if needed
                    val injectBridgeJs = """
                        if (typeof window.cefBridge === 'undefined') {
                            window.cefBridge = {
                                send: function(raw) {
                                    if (typeof window.cefQuery === 'function') {
                                        window.cefQuery({ request: raw, persistent: false, onSuccess: function(){}, onFailure: function(){} });
                                    }
                                },
                                onMessage: function(cb) {
                                    window.__cefMessageListener = cb;
                                    return function() { window.__cefMessageListener = null; };
                                }
                            };
                        }
                    """.trimIndent()
                    hostManager?.executeJavaScript(injectBridgeJs)

                    // Synthesize formal RENDERER_READY handshake for DefaultSceneBridgeChannel
                    val readyPayload = JSONObject().apply {
                        put("type", "RENDERER_READY")
                        put("version", CURRENT_BRIDGE_PROTOCOL_VERSION)
                        put("timestamp", System.currentTimeMillis())
                        put("payload", JSONObject().apply {
                            put("protocolVersion", CURRENT_BRIDGE_PROTOCOL_VERSION)
                            put("rendererVersion", "1.0")
                        })
                    }.toString()
                    receiveListener?.invoke(readyPayload)
                    return
                }

                // Pass through other messages (including RENDERER_READY from renderer-runtime)
                receiveListener?.invoke(rawMessage)
            } catch (_: Throwable) {
                // Pass raw message to listener for error isolation in serializer
                receiveListener?.invoke(rawMessage)
            }
        }

        override fun send(message: String) {
            val quoted = JSONObject.quote(message)
            val js = "window.__cefMessageListener && window.__cefMessageListener($quoted);"
            hostManager?.executeJavaScript(js)
        }

        override fun setReceiveListener(listener: ((String) -> Unit)?) {
            this.receiveListener = listener
        }
    }
}
