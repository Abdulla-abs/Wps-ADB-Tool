package `fun`.abbas.wps_adb.data

object JvmEasyActionCommands {
    fun clearAppCache(packageName: String): List<String> =
        listOf("shell", "cmd", "package", "clear-app-cache", "--user", "0", packageName)

    fun clearAppCacheFallback(packageName: String): List<String> =
        listOf("shell", "pm", "clear", "--cache-only", packageName)
}
