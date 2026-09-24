package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.model.scene.SceneInteractionMode

enum class SceneRuntimePhase {
    DISABLED,
    INITIALIZING_CEF,
    CREATING_BROWSER,
    WAITING_BRIDGE,
    LOADING_SCENE,
    READY,
    FAILED,
    DISPOSING,
    DISPOSED,
}

enum class SceneRuntimeFailureCategory {
    CEF_INITIALIZATION,
    BROWSER_CREATION,
    PAGE_LOAD,
    BRIDGE_HANDSHAKE,
    SCENE_LOAD,
    RESOURCE_CLEANUP,
    UNKNOWN,
}

data class SceneRuntimeFailure(
    val stage: SceneRuntimePhase,
    val category: SceneRuntimeFailureCategory,
    val userMessage: String,
    val diagnosticId: String,
)

data class SceneRuntimeState(
    val phase: SceneRuntimePhase = SceneRuntimePhase.DISABLED,
    val connectionState: BridgeConnectionState = BridgeConnectionState.DISCONNECTED,
    val failure: SceneRuntimeFailure? = null,
    val activeSceneId: String? = null,
    val selectedObjectId: String? = null,
    val interactionMode: SceneInteractionMode = SceneInteractionMode.VIEW,
) {
    val isInitializing: Boolean
        get() = phase in setOf(
            SceneRuntimePhase.INITIALIZING_CEF,
            SceneRuntimePhase.CREATING_BROWSER,
            SceneRuntimePhase.WAITING_BRIDGE,
            SceneRuntimePhase.LOADING_SCENE,
        )

    /** Kept as a read-only compatibility property for existing callers. */
    val initError: String?
        get() = failure?.userMessage
}
