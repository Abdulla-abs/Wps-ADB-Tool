package `fun`.abbas.wps_adb.model

import `fun`.abbas.wps_adb.data.DecompileProjectNames

data class RecentDecompileProject(
    val apkPath: String,
    val workspacePath: String,
    val packageName: String,
    val apkFileName: String,
    val lastOpenedAtMillis: Long,
    val appLabel: String? = null,
) {
    fun displayName(): String = DecompileProjectNames.displayName(appLabel, packageName, apkFileName)
}
