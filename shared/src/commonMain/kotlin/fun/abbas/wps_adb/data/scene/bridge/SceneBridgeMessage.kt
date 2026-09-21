package `fun`.abbas.wps_adb.data.scene.bridge

import `fun`.abbas.wps_adb.model.scene.SceneVector3

/**
 * Base sealed interface for all messages crossing the Scene Bridge.
 */
sealed interface SceneBridgeMessage {
    val version: Int get() = 1
    val timestamp: Long

    // ==========================================
    // Host -> Renderer (Downlink Commands & Sync)
    // ==========================================

    /**
     * Initializes the 3D scene environment, assets, and camera.
     */
    data class InitScene(
        val sceneDescriptor: SceneDescriptor,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Synchronizes full visual state snapshot of bound devices and active selection.
     */
    data class SyncState(
        val snapshot: SceneVisualSnapshot,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Incremental update for a single device binding to avoid full snapshot overhead.
     */
    data class UpdateBinding(
        val device: DeviceVisualDescriptor,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Updates selection state independently without rebuilding device descriptors.
     */
    data class UpdateSelection(
        val selectedObjectId: String?,
        val focusCamera: Boolean = false,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Controls camera target or position from host UI.
     */
    data class CameraCommand(
        val position: SceneVector3,
        val target: SceneVector3,
        val fov: Double? = null,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    // ==========================================
    // Renderer -> Host (Uplink Events)
    // ==========================================

    /**
     * Sent when the WebGL renderer has initialized and capabilities are queried.
     */
    data class RendererReady(
        val webglVendor: String,
        val webglRenderer: String,
        val maxTextureSize: Int,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sent when a 3D object was clicked / picked by raycasting in the canvas.
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
     */
    data class ObjectHovered(
        val objectId: String?,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sent when an object transform is modified via 3D gizmos in the viewport.
     */
    data class ObjectTransformChanged(
        val objectId: String,
        val position: SceneVector3,
        val rotation: SceneVector3,
        val scale: SceneVector3,
        override val timestamp: Long = 0L,
    ) : SceneBridgeMessage

    /**
     * Sent when renderer encounters an error (e.g., shader compile error, WebGL context lost).
     */
    data class RendererError(
        val code: String,
        val message: String,
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
