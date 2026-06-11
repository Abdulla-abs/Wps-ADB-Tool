package `fun`.abbas.wps_adb.data

import java.io.File

object ExecutableLocator {
    fun resolveAdbPath(configured: String): String =
        resolve(configured, "adb") { discoverAdbPath() }

    fun resolveScrcpyPath(configured: String): String =
        resolve(configured, "scrcpy") { discoverScrcpyPath() }

    fun discoverAdbPath(): String? =
        findOnPath("adb") ?: discoverAdbFromSdk()

    fun discoverScrcpyPath(): String? =
        findOnPath("scrcpy")

    fun findOnPath(executableName: String): String? {
        val pathEnv = System.getenv("PATH")?.trim().orEmpty()
        if (pathEnv.isEmpty()) return null
        val fileName = executableFileName(executableName)
        val separator = if (File.separatorChar == '\\') ";" else ":"
        return pathEnv.split(separator)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { File(it, fileName) }
            .firstOrNull { it.isFile }
            ?.absolutePath
    }

    private fun resolve(configured: String, defaultName: String, discover: () -> String?): String {
        val trimmed = configured.trim()
        when {
            trimmed.isEmpty() || trimmed == defaultName -> return discover() ?: defaultName
            else -> {
                val file = File(trimmed)
                if (file.exists()) return file.absolutePath
                return discover() ?: defaultName
            }
        }
    }

    private fun discoverAdbFromSdk(): String? {
        val sdkRoot = AndroidSdkToolLocator.discoverSdkRoot() ?: return null
        val adb = File(sdkRoot, "platform-tools/${executableFileName("adb")}")
        return adb.takeIf { it.isFile }?.absolutePath
    }

    private fun executableFileName(name: String): String =
        if (File.separatorChar == '\\') "$name.exe" else name
}
