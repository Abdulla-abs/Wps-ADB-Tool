package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.bridge.CefBridgeTransport
import `fun`.abbas.wps_adb.bridge.CefJsBridge
import `fun`.abbas.wps_adb.bridge.SceneBridgeHostController
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeTransport
import `fun`.abbas.wps_adb.data.scene.bridge.CURRENT_BRIDGE_PROTOCOL_VERSION
import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneBridgeChannel
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeChannel
import `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository
import `fun`.abbas.wps_adb.data.scene.SceneStore
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlinx.coroutines.CoroutineDispatcher
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean

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

    fun refreshScenes() {
        val repo = sceneRepository ?: return
        val list = try {
            repo.listScenes().map { SceneOption(it.id, it.name) }
        } catch (_: Throwable) {
            emptyList()
        }
        _availableScenes.value = list
    }

    fun selectScene(sceneId: String): Boolean {
        val repository = sceneRepository ?: return false
        val scene = try {
            repository.loadScene(sceneId)
        } catch (_: Throwable) {
            return false
        }
        sceneRuntimeController?.setScene(scene)
        _state.update { it.copy(activeSceneId = scene.id) }
        onActiveSceneIdChanged?.invoke(scene.id)
        return true
    }

    var browserComponent: Component? = null
        private set

    private var cefHostManager: CefHostManager? = null
    private var bridgeAdapter: CefHostManagerBridgeAdapter? = null

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

            sceneRuntimeController.setScene(initialScene)
            _state.update { it.copy(activeSceneId = initialScene.id) }

            if (initialActiveSceneId != initialScene.id) {
                onActiveSceneIdChanged?.invoke(initialScene.id)
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

                val comp = mgr.initializeBrowser()
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
            println("[SceneRuntimeHost] Disposing SceneRuntimeHost (graceful close)...")
            hostController.dispose()
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

    /**
     * Synchronous disposal for non-suspending callers (e.g. Window onCloseRequest).
     * Guaranteed to be thread-safe and idempotent.
     */
    fun dispose() {
        if (isDisposed.get()) return
        runBlocking(ioDispatcher) {
            close()
        }
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
