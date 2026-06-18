package `fun`.abbas.wps_adb.model

data class ApkReinstallPrompt(
    val deviceId: String,
    val deviceName: String,
    val apkPath: String,
    val apkFileName: String,
    val packageName: String,
    val id: Long = 0,
)
