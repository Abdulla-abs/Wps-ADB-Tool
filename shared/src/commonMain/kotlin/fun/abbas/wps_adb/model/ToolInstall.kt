package `fun`.abbas.wps_adb.model

enum class ToolKind {
    ADB,
    SCRCPY,
}

enum class ToolInstallPhase {
    DOWNLOADING,
    EXTRACTING,
    CONFIGURING,
    DONE,
    FAILED,
}

data class ToolInstallProgress(
    val kind: ToolKind,
    val phase: ToolInstallPhase,
    /** 0f..1f while in progress; null when idle/cleared */
    val fraction: Float = 0f,
    val message: String = "",
    val errorMessage: String? = null,
)
