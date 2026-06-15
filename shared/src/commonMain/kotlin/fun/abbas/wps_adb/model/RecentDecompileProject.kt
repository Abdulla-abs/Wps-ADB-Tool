package `fun`.abbas.wps_adb.model

data class RecentDecompileProject(
    val apkPath: String,
    val workspacePath: String,
    val packageName: String,
    val apkFileName: String,
    val lastOpenedAtMillis: Long,
)
