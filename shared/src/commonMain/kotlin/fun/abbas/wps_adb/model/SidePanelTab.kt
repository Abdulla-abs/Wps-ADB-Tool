package `fun`.abbas.wps_adb.model

enum class AppLogMonitorState {
    IDLE,
    MONITORING,
}

sealed class SidePanelTab {
    abstract val id: String
    abstract val title: String
    abstract val device: Device

    data class AppLog(
        override val id: String,
        override val title: String,
        override val device: Device,
        val apkPath: String,
        val apkFileName: String,
        val packageName: String? = null,
        val launchActivity: String? = null,
        val appLabel: String? = null,
        val monitorState: AppLogMonitorState = AppLogMonitorState.IDLE,
        val autoScrollEnabled: Boolean = true,
        val logFilterQuery: String = "",
        val logFilterLevels: Set<LogLevel> = LogLevel.entries.toSet(),
        val logs: List<AdbLog> = emptyList(),
    ) : SidePanelTab()

    data class Mirror(
        override val id: String,
        override val title: String,
        override val device: Device,
        val sessionState: MirrorSessionState = MirrorSessionState.IDLE,
        val errorMessage: String? = null,
        val connectionOptions: ScrcpyConnectionOptions = ScrcpyConnectionOptions(),
    ) : SidePanelTab()
}
