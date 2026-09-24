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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean

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
) {
    private val hostJob = SupervisorJob(parentScope.coroutineContext[kotlinx.coroutines.Job])
    val hostScope = CoroutineScope(parentScope.coroutineContext + hostJob)

    private val isDisposed = AtomicBoolean(false)

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
                                    initError = errorMessage,
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
        )

        // Observe connection state changes
        hostScope.launch {
            channel.state.collect { connState ->
                _state.update { it.copy(connectionState = connState) }
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
            _state.update { it.copy(isInitializing = false) }
        }
    }

    private fun initializeJcefBrowser() {
        hostScope.launch(ioDispatcher) {
            try {
                val resolvedScenesRoot = scenesRoot
                    ?: (sceneRepository as? SceneStore)?.getScenesRoot()
                    ?: `fun`.abbas.wps_adb.data.AppDataPaths.defaultScenesRoot()

                val mgr = cefHostManagerProvider?.invoke() ?: CefHostManager(
                    resourceRoot = resourceRoot,
                    scenesRoot = resolvedScenesRoot,
                ) { rawMessage ->
                    bridgeAdapter?.handleIncomingJsMessage(rawMessage)
                }

                cefHostManager = mgr
                bridgeAdapter?.bind(mgr)

                // Phase 1: Heavy CEF initialization (may download/extract binaries) on IO
                mgr.ensureCefAppInitialized()

                // Phase 2: Browser creation MUST run on EDT — JCEF heavyweight AWT
                // components created off the EDT produce a native HWND with broken
                // parenting/z-order, which steals all input from the Compose canvas.
                val comp = withContext(mainDispatcher) {
                    mgr.createBrowserOnEdt()
                }

                withContext(mainDispatcher) {
                    browserComponent = comp
                    _state.update { it.copy(isInitializing = false) }
                    println("[SceneRuntimeHost] JCEF Chromium browser initialized successfully")
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.printStackTrace()
                withContext(mainDispatcher) {
                    _state.update {
                        it.copy(
                            isInitializing = false,
                            initError = t.message ?: t.toString()
                        )
                    }
                }
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
        withContext(NonCancellable) {
            sceneLifecycleMutex.withLock {
                println("[SceneRuntimeHost] Disposing SceneRuntimeHost (graceful close)...")
                synchronized(this@SceneRuntimeHost) {
                    isSwitchingScene = true
                    activeEpoch++
                }
                hostController.dispose()
                try {
                    persistenceCoordinator?.close()
                } catch (_: Throwable) {
                }

                try {
                    channel.disconnect()
                } catch (_: Throwable) {
                }

                try {
                    cefHostManager?.dispose()
                    cefHostManager = null
                } catch (t: Throwable) {
                    t.printStackTrace()
                }

                browserComponent = null
                bridgeAdapter = null
                hostJob.cancel()
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
        // Fire-and-forget: close() uses NonCancellable internally so cleanup
        // completes even after hostScope is cancelled by the caller.
        hostScope.launch(ioDispatcher) { close() }
    }

    /**
     * Internal adapter between [CefHostManager] and [CefJsBridge].
     */
    class CefHostManagerBridgeAdapter : CefJsBridge {
        private var receiveListener: ((String) -> Unit)? = null
        private var hostManager: CefHostManager? = null

        fun bind(manager: CefHostManager) {
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
