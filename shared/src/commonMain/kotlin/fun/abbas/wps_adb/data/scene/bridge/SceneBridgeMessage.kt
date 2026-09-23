package `fun`.abbas.wps_adb.data.scene.bridge

import `fun`.abbas.wps_adb.model.scene.SceneInteractionMode
import `fun`.abbas.wps_adb.model.scene.SceneVector3

const val CURRENT_BRIDGE_PROTOCOL_VERSION: Int = 1

/**
 * Standard protocol error codes for [SceneBridgeMessage.RendererError].
 */
object BridgeErrorCodes {
    const val PROTOCOL_VERSION_MISMATCH = "PROTOCOL_VERSION_MISMATCH"
    const val INVALID_MESSAGE = "INVALID_MESSAGE"
    const val INVALID_PAYLOAD = "INVALID_PAYLOAD"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}

/**
 * Base sealed interface for all messages crossing the Scene Bridge.
 */
sealed interface SceneBridgeMessage {
    val version: Int get() = CURRENT_BRIDGE_PROTOCOL_VERSION
    val timestamp: Long

    // ==========================================
    // Host -> Renderer (Downlink Commands & Sync)
    // ==========================================

    /**
     * Initializes the 3D scene environment, assets, and camera.
     * Wire type: SCENE_INIT
     */
    data class InitScene(
        val sceneDescriptor: SceneDescriptor,
        val epoch: Long = 0L,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Synchronizes full visual state snapshot of bound devices and active selection.
     * Wire type: STATE_SYNC
     */
    data class SyncState(
        val snapshot: SceneVisualSnapshot,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Incremental update for a single device binding to avoid full snapshot overhead.
     * Wire type: BINDING_UPDATE
     */
    data class UpdateBinding(
        val device: DeviceVisualDescriptor,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Updates selection state independently without rebuilding device descriptors.
     * Wire type: SELECTION_CHANGE
     */
    data class SelectionChange(
        val selectedObjectId: String?,
        val focusCamera: Boolean = false,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Controls camera target or position from host UI.
     * Wire type: CAMERA_COMMAND
     */
    data class CameraCommand(
        val position: SceneVector3,
        val target: SceneVector3,
        val fov: Double? = null,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sets active interaction mode (VIEW, BINDING, EDITING) in the renderer.
     * Wire type: SET_INTERACTION_MODE
     */
    data class SetInteractionMode(
        val mode: SceneInteractionMode,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sets object transform (position, rotation, scale) in the renderer from host UI.
     * Wire type: SET_OBJECT_TRANSFORM
     */
    data class SetObjectTransform(
        val objectId: String,
        val position: SceneVector3,
        val rotation: SceneVector3,
        val scale: SceneVector3,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    // ==========================================
    // Renderer -> Host (Uplink Events)
    // ==========================================

    /**
     * Sent when the JS Runtime has initialized and bridge contract version is confirmed.
     * Wire type: RENDERER_READY
     */
    data class RendererReady(
        override val version: Int = CURRENT_BRIDGE_PROTOCOL_VERSION,
        val protocolVersion: Int = CURRENT_BRIDGE_PROTOCOL_VERSION,
        val rendererVersion: String = "1.0",
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sent when the renderer camera transforms (OrbitControls end / pan / orbit / zoom).
     * Wire type: CAMERA_CHANGED
     */
    data class CameraChanged(
        val position: SceneVector3,
        val target: SceneVector3,
        val fov: Double,
        val sceneId: String? = null,
        val epoch: Long? = null,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sent when a 3D object was clicked / picked by raycasting in the canvas.
     * Wire type: OBJECT_CLICKED
     */
    data class ObjectClicked(
        val objectId: String,
        val screenX: Float = 0f,
        val screenY: Float = 0f,
        val isCtrlPressed: Boolean = false,
        val isShiftPressed: Boolean = false,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sent when mouse enters or leaves a 3D object bounding box.
     * Wire type: OBJECT_HOVERED
     */
    data class ObjectHovered(
        val objectId: String?,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sent when an object transform is modified via 3D gizmos in the viewport.
     * Wire type: OBJECT_TRANSFORM_CHANGED
     */
    data class ObjectTransformChanged(
        val objectId: String,
        val position: SceneVector3,
        val rotation: SceneVector3,
        val scale: SceneVector3,
        val sceneId: String? = null,
        val epoch: Long? = null,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sent when renderer encounters an error.
     * Wire type: RENDERER_ERROR
     */
    data class RendererError(
        val code: String,
        val message: String,
        val category: String? = null,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Fallback representation for unknown or future message types to ensure non-crashing forward compatibility.
     */
    data class Unknown(
        val rawType: String,
        val rawPayload: String = "",
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage
}

/**
 * Backward compatibility type alias for [SceneBridgeMessage.SelectionChange].
 */
typealias UpdateSelection = SceneBridgeMessage.SelectionChange
