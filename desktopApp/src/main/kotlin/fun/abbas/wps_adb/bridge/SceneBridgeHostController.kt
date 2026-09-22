package `fun`.abbas.wps_adb.bridge

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneVisualProjector
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeChannel
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeMessage
import `fun`.abbas.wps_adb.data.scene.bridge.SceneVisualProjector
import `fun`.abbas.wps_adb.data.scene.bridge.toDescriptor
import `fun`.abbas.wps_adb.data.scene.runtime.SceneRuntimeController
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Host orchestration controller bridging the Scene Domain to the [SceneBridgeChannel].
 *
 * Responsibilities:
 * - Listens for domain scene state changes ([ResolvedSceneState]).
 * - Caches [latestState] to avoid sending stale intermediate frames while the renderer is loading.
 * - On [BridgeConnectionState.READY], initializes the scene and dispatches the latest visual snapshot.
 * - While [BridgeConnectionState.READY], projects state changes into [SceneBridgeMessage.SyncState] frames.
 *
 * Boundary Guarantees:
 * - Does NOT import or reference Cef, JCEF, or Three.js.
 * - Projector does NOT call the channel directly; only HostController controls message dispatching.
 */
class SceneBridgeHostController(
    private val channel: SceneBridgeChannel,
    private val projector: SceneVisualProjector = DefaultSceneVisualProjector(),
    private val scope: CoroutineScope,
) {

    private val stateMutex = Mutex()
    private var latestState: ResolvedSceneState? = null
    private var selectedObjectId: String? = null
    private var lastSentSceneId: String? = null
    private var boundRuntimeController: SceneRuntimeController? = null

    init {
        scope.launch {
            channel.state.collect { connectionState ->
                if (connectionState == BridgeConnectionState.READY) {
                    onRendererReady()
                }
            }
        }
    }

    /**
     * Handles domain scene state updates.
     * Caches the latest state and, if the renderer is READY, synchronizes it to the bridge channel.
     */
    suspend fun onSceneStateChanged(state: ResolvedSceneState?) {
        stateMutex.withLock {
            latestState = state
            if (channel.state.value == BridgeConnectionState.READY && state != null) {
                syncSceneState(state)
            }
        }
    }

    private var runtimeBindingJob: Job? = null

    /**
     * Updates object selection and notifies the renderer if connection is READY.
     */
    suspend fun selectObject(objectId: String?, focusCamera: Boolean = false) {
        stateMutex.withLock {
            selectedObjectId = objectId
            if (channel.state.value == BridgeConnectionState.READY) {
                channel.send(
                    SceneBridgeMessage.SelectionChange(
                        selectedObjectId = objectId,
                        focusCamera = focusCamera,
                    )
                )
            }
        }
    }

    /**
     * Sends camera command to reset or reposition the camera.
     */
    suspend fun resetCamera(camera: SceneCamera? = null) {
        stateMutex.withLock {
            val cam = camera ?: latestState?.scene?.camera ?: SceneCamera()
            boundRuntimeController?.updateRuntimeCamera(cam)
            if (channel.state.value == BridgeConnectionState.READY) {
                channel.send(
                    SceneBridgeMessage.CameraCommand(
                        position = cam.position,
                        target = cam.target,
                        fov = cam.fov,
                    )
                )
            }
        }
    }

    /**
     * Binds this controller to both resolved state and selection synchronization
     * of a [SceneRuntimeController], listening to inbound [SceneBridgeMessage.ObjectClicked]
     * and [SceneBridgeMessage.CameraChanged] events.
     */
    fun bind(runtimeController: SceneRuntimeController): Job {
        runtimeBindingJob?.cancel()
        boundRuntimeController = runtimeController

        val bindingJob = SupervisorJob(scope.coroutineContext[Job])
        val bindingScope = CoroutineScope(scope.coroutineContext + bindingJob)

        bindingScope.launch {
            runtimeController.resolvedState.collect { state ->
                onSceneStateChanged(state)
            }
        }

        bindingScope.launch {
            runtimeController.selectedObjectId.collect { objectId ->
                stateMutex.withLock {
                    if (selectedObjectId != objectId) {
                        selectedObjectId = objectId
                        if (channel.state.value == BridgeConnectionState.READY) {
                            channel.send(SceneBridgeMessage.SelectionChange(objectId, false))
                        }
                    }
                }
            }
        }

        bindingScope.launch {
            channel.incoming.collect { message ->
                when (message) {
                    is SceneBridgeMessage.ObjectClicked -> {
                        println("[SceneBridgeHostController] Received ObjectClicked: ${message.objectId}")
                        runtimeController.selectObject(message.objectId)
                        selectObject(message.objectId)
                    }
                    is SceneBridgeMessage.CameraChanged -> {
                        runtimeController.updateRuntimeCamera(
                            SceneCamera(
                                position = message.position,
                                target = message.target,
                                fov = message.fov,
                            )
                        )
                    }
                    else -> {}
                }
            }
        }

        runtimeBindingJob = bindingJob
        return bindingJob
    }

    /**
     * Binds this controller to an arbitrary [ResolvedSceneState] flow.
     */
    fun bind(stateFlow: StateFlow<ResolvedSceneState?>): Job {
        return scope.launch {
            stateFlow.collect { state ->
                onSceneStateChanged(state)
            }
        }
    }

    fun dispose() {
        runtimeBindingJob?.cancel()
        runtimeBindingJob = null
        boundRuntimeController = null
    }



    private suspend fun onRendererReady() {
        stateMutex.withLock {
            val state = latestState ?: return
            println("[SceneBridgeHostController] Renderer READY received. Initializing scene: ${state.scene.id}")
            syncInitialScene(state)
        }
    }

    private suspend fun syncInitialScene(state: ResolvedSceneState) {
        channel.send(
            SceneBridgeMessage.InitScene(
                sceneDescriptor = state.scene.toDescriptor(),
            )
        )
        println("[SceneBridgeHostController] InitScene sent for scene: ${state.scene.id}")
        lastSentSceneId = state.scene.id

        val snapshot = projector.project(state, selectedObjectId)
        channel.send(
            SceneBridgeMessage.SyncState(
                snapshot = snapshot,
            )
        )
    }

    private suspend fun syncSceneState(state: ResolvedSceneState) {
        if (state.scene.id != lastSentSceneId) {
            syncInitialScene(state)
            return
        }

        val snapshot = projector.project(state, selectedObjectId)
        channel.send(
            SceneBridgeMessage.SyncState(
                snapshot = snapshot,
            )
        )
    }
}
