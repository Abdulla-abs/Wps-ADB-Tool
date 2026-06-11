package `fun`.abbas.wps_adb.model

data class ApkInstallResult(
    val success: Boolean,
    val message: String,
    val apkPath: String,
    val apkFileName: String,
    val metadata: ApkMetadata? = null,
)
