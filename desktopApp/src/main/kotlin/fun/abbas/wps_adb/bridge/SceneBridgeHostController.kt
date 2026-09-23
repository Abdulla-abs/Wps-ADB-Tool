package `fun`.abbas.wps_adb.bridge

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneVisualProjector
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeChannel
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeMessage
import `fun`.abbas.wps_adb.data.scene.bridge.SceneCameraDescriptor
import `fun`.abbas.wps_adb.data.scene.bridge.SceneVisualProjector
import `fun`.abbas.wps_adb.data.scene.bridge.toDescriptor
import `fun`.abbas.wps_adb.data.scene.runtime.SceneRuntimeController
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneInteractionMode
import `fun`.abbas.wps_adb.model.scene.SceneTransform
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
    private val eventValidator: (sceneId: String?, epoch: Long?) -> Boolean = { _, _ -> true },
    private val epochProvider: () -> Long = { 0L },
    private val onTransformChanged: suspend (sceneId: String?, epoch: Long?, objectId: String, transform: SceneTransform) -> Unit = { _, _, _, _ -> },
    private val onCameraChanged: suspend (sceneId: String?, epoch: Long?, camera: SceneCamera) -> Unit = { _, _, _ -> },
) {

    private val stateMutex = Mutex()
    private var latestState: ResolvedSceneState? = null
    private var selectedObjectId: String? = null
    private var lastSentSceneId: String? = null
    private var lastSentEpoch: Long? = null
    private var lastSentSceneContentKey: SceneContentKey? = null
    private var boundRuntimeController: SceneRuntimeController? = null
    private var currentMode: SceneInteractionMode = SceneInteractionMode.VIEW

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
                        val activeSceneId = runtimeController.activeScene.value?.id
                        if (message.sceneId != null && activeSceneId != null && message.sceneId != activeSceneId) {
                            return@collect
                        }
                        if (!eventValidator(message.sceneId, message.epoch)) {
                            return@collect
                        }
                        val cam = SceneCamera(
                            position = message.position,
                            target = message.target,
                            fov = message.fov,
                        )
                        runtimeController.updateRuntimeCamera(cam)
                        onCameraChanged(message.sceneId, message.epoch, cam)
                    }
                    is SceneBridgeMessage.ObjectTransformChanged -> {
                        val activeSceneId = runtimeController.activeScene.value?.id
                        if (message.sceneId != null && activeSceneId != null && message.sceneId != activeSceneId) {
                            return@collect
                        }
                        if (!eventValidator(message.sceneId, message.epoch)) {
                            return@collect
                        }
                        val transform = SceneTransform(
                            position = message.position,
                            rotation = message.rotation,
                            scale = message.scale,
                        )
                        runtimeController.updateRuntimeTransform(message.objectId, transform)
                        onTransformChanged(message.sceneId, message.epoch, message.objectId, transform)
                    }
                    else -> {}
                }
            }
        }

        runtimeBindingJob = bindingJob
        return bindingJob
    }

    suspend fun setInteractionMode(mode: SceneInteractionMode) {
        stateMutex.withLock {
            currentMode = mode
            if (channel.state.value == BridgeConnectionState.READY) {
                channel.send(SceneBridgeMessage.SetInteractionMode(mode = mode))
            }
        }
    }

    suspend fun setObjectTransform(objectId: String, transform: SceneTransform) {
        stateMutex.withLock {
            if (channel.state.value == BridgeConnectionState.READY) {
                channel.send(
                    SceneBridgeMessage.SetObjectTransform(
                        objectId = objectId,
                        position = transform.position,
                        rotation = transform.rotation,
                        scale = transform.scale,
                    )
                )
            }
        }
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

    private suspend fun syncInitialScene(
        state: ResolvedSceneState,
        preserveRuntimeCamera: Boolean = false,
    ) {
        var descriptor = state.scene.toDescriptor()
        val runtimeCamera = boundRuntimeController?.runtimeCamera?.value
        if (preserveRuntimeCamera && runtimeCamera != null) {
            descriptor = descriptor.copy(
                camera = SceneCameraDescriptor(
                    position = runtimeCamera.position,
                    target = runtimeCamera.target,
                    fov = runtimeCamera.fov,
                ),
            )
        }
        val currentEpoch = epochProvider()
        channel.send(
            SceneBridgeMessage.InitScene(
                sceneDescriptor = descriptor,
                epoch = currentEpoch,
            )
        )
        println("[SceneBridgeHostController] InitScene sent for scene: ${state.scene.id} (epoch=$currentEpoch)")
        lastSentSceneId = state.scene.id
        lastSentEpoch = currentEpoch
        lastSentSceneContentKey = SceneContentKey.from(state.scene)

        val snapshot = projector.project(state, selectedObjectId)
        channel.send(
            SceneBridgeMessage.SyncState(
                snapshot = snapshot,
            )
        )

        if (currentMode != SceneInteractionMode.VIEW) {
            channel.send(SceneBridgeMessage.SetInteractionMode(mode = currentMode))
        }
    }

    private suspend fun syncSceneState(state: ResolvedSceneState) {
        val contentKey = SceneContentKey.from(state.scene)
        val currentEpoch = epochProvider()
        if (state.scene.id != lastSentSceneId || currentEpoch != lastSentEpoch) {
            syncInitialScene(state)
            return
        }
        if (contentKey != lastSentSceneContentKey) {
            // Rebuild renderer content when environment/assets change, while preserving
            // the live camera. Transform-only updates use SET_OBJECT_TRANSFORM instead.
            syncInitialScene(state, preserveRuntimeCamera = true)
            return
        }

        val snapshot = projector.project(state, selectedObjectId)
        channel.send(
            SceneBridgeMessage.SyncState(
                snapshot = snapshot,
            )
        )
    }

    private data class SceneContentKey(
        val sceneId: String,
        val name: String,
        val environmentFileName: String?,
        val assets: List<AssetContentKey>,
        val bindableObjectIds: List<String>,
    ) {
        companion object {
            fun from(scene: DeviceScene) = SceneContentKey(
                sceneId = scene.id,
                name = scene.name,
                environmentFileName = scene.environment?.fileName,
                assets = scene.assets.map { AssetContentKey(it.id, it.fileName, it.name) },
                bindableObjectIds = scene.bindableObjectIds,
            )
        }
    }

    private data class AssetContentKey(
        val id: String,
        val fileName: String,
        val name: String,
    )
}
