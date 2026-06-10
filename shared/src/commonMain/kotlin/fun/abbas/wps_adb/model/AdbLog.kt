package `fun`.abbas.wps_adb.model

data class AdbLog(
    val id: String,
    val timestamp: String,
    val tag: String,
    val level: LogLevel,
    val message: String,
    val deviceId: String? = null,
)
