package `fun`.abbas.wps_adb.model

data class ApkInstallToast(
    val apkFileName: String,
    val deviceName: String,
    val success: Boolean,
    val id: Long = 0,
)
