package `fun`.abbas.wps_adb.data

import java.io.File

object AndroidSdkToolLocator {
    fun discoverSdkRoot(): File? = resolveSdkRootFromEnvironment()

    fun resolveSdkRoot(adbPath: String): File? =
        resolveSdkRootFromAdbPath(adbPath) ?: resolveSdkRootFromEnvironment()

    private fun resolveSdkRootFromAdbPath(adbPath: String): File? {
        val trimmed = adbPath.trim()
        if (trimmed.isEmpty() || trimmed == "adb") return null
        val adbFile = File(trimmed)
        if (!adbFile.isAbsolute || !adbFile.exists()) return null
        val platformTools = adbFile.parentFile ?: return null
        if (platformTools.name != "platform-tools") return null
        return platformTools.parentFile?.takeIf { it.isDirectory }
    }

    private fun resolveSdkRootFromEnvironment(): File? {
        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
            .mapNotNull { System.getenv(it)?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .map(::File)
            .firstOrNull { it.isDirectory }
            ?.let { return it }

        val localAppData = System.getenv("LOCALAPPDATA")?.trim().orEmpty()
        if (localAppData.isNotEmpty()) {
            val windowsSdk = File(localAppData, "Android/Sdk")
            if (windowsSdk.isDirectory) return windowsSdk
        }
        return null
    }

    fun resolveBuildToolsBinary(adbPath: String, binaryName: String): String? {
        val sdkRoot = resolveSdkRoot(adbPath) ?: return null
        val buildToolsDir = File(sdkRoot, "build-tools")
        if (!buildToolsDir.isDirectory) return null
        val versionDirs = buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            .orEmpty()
        val executableName = if (File.separatorChar == '\\') "$binaryName.exe" else binaryName
        for (dir in versionDirs) {
            val candidate = File(dir, executableName)
            if (candidate.isFile) return candidate.absolutePath
        }
        return null
    }

    fun resolveAapt(adbPath: String): String? = resolveBuildToolsBinary(adbPath, "aapt")

    fun resolveAapt2(adbPath: String): String? = resolveBuildToolsBinary(adbPath, "aapt2")

    fun resolveApkanalyzer(adbPath: String): String? {
        resolveBuildToolsBinary(adbPath, "apkanalyzer")?.let { return it }
        val sdkRoot = resolveSdkRoot(adbPath) ?: return null
        val cmdlineTools = File(sdkRoot, "cmdline-tools")
        if (!cmdlineTools.isDirectory) return null
        val versionDirs = cmdlineTools.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            .orEmpty()
        val executableName = if (File.separatorChar == '\\') "apkanalyzer.bat" else "apkanalyzer"
        for (dir in versionDirs) {
            val candidate = File(dir, "bin/$executableName")
            if (candidate.isFile) return candidate.absolutePath
        }
        return null
    }
}
