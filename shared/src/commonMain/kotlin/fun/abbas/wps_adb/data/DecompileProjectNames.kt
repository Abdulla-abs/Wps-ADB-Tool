package `fun`.abbas.wps_adb.data

object DecompileProjectNames {
    fun displayName(appLabel: String?, packageName: String, apkFileName: String? = null): String {
        appLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (ApkPackageNames.isLikelyAppPackage(packageName.lowercase())) return packageName
        apkFileName?.let { name ->
            name.removeSuffix(".apk").removeSuffix(".APK").trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return packageName
    }
}
