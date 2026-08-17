package `fun`.abbas.wps_adb.data

/** Returns whether the configured path points to a usable ADB / scrcpy executable. */
expect suspend fun validateAdbExecutablePath(configuredPath: String): Boolean

expect suspend fun validateScrcpyExecutablePath(configuredPath: String): Boolean
