package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState

import `fun`.abbas.wps_adb.model.scene.SceneInteractionMode

/**
 * Observable UI and runtime state for the 3D Scene View and Host.
 */
data class SceneRuntimeState(
    val connectionState: BridgeConnectionState = BridgeConnectionState.DISCONNECTED,
    val isInitializing: Boolean = true,
    val initError: String? = null,
    val activeSceneId: String? = null,
    val selectedObjectId: String? = null,
    val interactionMode: SceneInteractionMode = SceneInteractionMode.VIEW,
)
