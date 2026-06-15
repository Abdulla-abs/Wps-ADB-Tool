package `fun`.abbas.wps_adb.model

data class AppSettings(
    val adbPath: String = "adb",
    val scrcpyPath: String = "scrcpy",
    val scrcpyConnection: ScrcpyConnectionOptions = ScrcpyConnectionOptions(),
    val minPort: Int = 5555,
    val maxPort: Int = 5585,
    val scanIntervalSec: Int = 15,
    val parallelThreads: Int = 4,
    val logRetention: Int = 2500,
    val autoApproveKey: Boolean = true,
    val diagnosticTelemetry: Boolean = false,
    val dataCacheDir: String = "",
)
