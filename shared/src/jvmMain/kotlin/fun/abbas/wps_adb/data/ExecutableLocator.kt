package `fun`.abbas.wps_adb.data

import java.io.File

object ExecutableLocator {
    fun resolveAdbPath(configured: String): String =
        resolveConfiguredExecutable(configured, ADB_DEFAULT_NAME)

    fun resolveRunnableAdbPath(configured: String): String? {
        val trimmed = configured.trim()
        if (isAdbConfigured(trimmed)) {
            val file = File(trimmed)
            if (file.isFile) return file.absolutePath
        }
        return discoverAdbPath()
    }

    fun resolveScrcpyPath(configured: String): String =
        resolveConfiguredExecutable(configured, SCRCPY_DEFAULT_NAME)

    fun isAdbConfigured(configured: String): Boolean =
        isExecutableConfigured(configured, ADB_DEFAULT_NAME)

    fun isScrcpyConfigured(configured: String): Boolean =
        isExecutableConfigured(configured, SCRCPY_DEFAULT_NAME)

    /** Blank or placeholder means "not configured" — do not search PATH/SDK. */
    fun isExecutableConfigured(configured: String, defaultName: String): Boolean {
        val trimmed = configured.trim()
        return trimmed.isNotEmpty() && !trimmed.equals(defaultName, ignoreCase = true)
    }

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

    private fun resolveConfiguredExecutable(configured: String, defaultName: String): String {
        val trimmed = configured.trim()
        if (!isExecutableConfigured(trimmed, defaultName)) return ""
        val file = File(trimmed)
        if (file.isFile) return file.absolutePath
        return trimmed
    }

    private fun discoverAdbFromSdk(): String? {
        val sdkRoot = AndroidSdkToolLocator.discoverSdkRoot() ?: return null
        val adb = File(sdkRoot, "platform-tools/${executableFileName("adb")}")
        return adb.takeIf { it.isFile }?.absolutePath
    }

    private fun executableFileName(name: String): String =
        if (File.separatorChar == '\\') "$name.exe" else name

    const val ADB_DEFAULT_NAME = "adb"
    const val SCRCPY_DEFAULT_NAME = "scrcpy"
}
