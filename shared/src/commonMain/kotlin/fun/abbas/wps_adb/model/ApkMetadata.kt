package `fun`.abbas.wps_adb.model

data class ApkMetadata(
    val packageName: String,
    val appLabel: String? = null,
    val versionName: String? = null,
    val launchActivity: String? = null,
)
