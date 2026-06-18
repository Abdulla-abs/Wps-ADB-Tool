package `fun`.abbas.wps_adb.model

sealed class DeviceWallRoute {
    data object Grid : DeviceWallRoute()
    data class Shell(val deviceId: String) : DeviceWallRoute()
}

enum class ShellTransitionKind {
    SHARED_ELEMENT,
    SLIDE,
}

data class DeviceShellSession(
    val deviceId: String,
    val sessionState: DeviceShellSessionState = DeviceShellSessionState.IDLE,
    val errorMessage: String? = null,
    val isScreenRecording: Boolean = false,
    val terminalSurfaceReady: Boolean = false,
    val developerOptionStates: Map<EasyActionKind, Boolean> = emptyMap(),
)
