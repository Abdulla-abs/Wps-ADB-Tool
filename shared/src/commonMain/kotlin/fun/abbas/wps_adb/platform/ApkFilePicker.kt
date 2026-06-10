package `fun`.abbas.wps_adb.platform

/** Opens a platform file picker for APK selection. Returns absolute path or null if cancelled. */
expect suspend fun pickApkFile(): String?
