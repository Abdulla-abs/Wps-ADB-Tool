package `fun`.abbas.wps_adb.model

enum class ApkInstallToastKind {
    INSTALL,
    ALREADY_INSTALLED,
    PARSE_FAILURE,
}

data class ApkInstallToast(
    val apkFileName: String,
    val deviceName: String,
    val success: Boolean,
    val kind: ApkInstallToastKind = ApkInstallToastKind.INSTALL,
    val id: Long = 0,
)
