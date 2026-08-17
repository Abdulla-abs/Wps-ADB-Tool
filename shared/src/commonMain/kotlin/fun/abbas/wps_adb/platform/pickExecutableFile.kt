package `fun`.abbas.wps_adb.platform

/** Opens a platform file picker for an executable. Returns absolute path or null if cancelled. */
expect suspend fun pickExecutableFile(
    initialPath: String? = null,
    dialogTitle: String = "Select executable",
): String?
