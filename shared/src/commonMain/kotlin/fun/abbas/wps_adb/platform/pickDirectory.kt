package `fun`.abbas.wps_adb.platform

expect suspend fun pickDirectory(
    initialPath: String? = null,
    dialogTitle: String = "Select directory",
): String?
