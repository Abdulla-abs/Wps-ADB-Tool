package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.LogLevel
import java.io.File
import java.net.URI

internal class JvmDeviceKitLayoutDump(
    private val runner: JvmAdbRunner,
    private val installApk: suspend (deviceId: String, apkPath: String) -> ApkInstallResult,
    private val addLog: (LogLevel, String, String, String?) -> Unit,
) {
    suspend fun dumpLayoutXml(deviceId: String, serial: String): String? {
        if (!ensureInstalled(deviceId, serial)) return null
        val output = runner.runWithTimeout(
            args = listOf(
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "waitUntilIdle",
                "0",
                "$DEVICEKIT_PACKAGE/.ViewTreeDump",
            ),
            serial = serial,
            timeoutMs = 30_000,
        ).output
        val json = parseInstrumentationJson(output) ?: return null
        return DeviceKitJsonToXml.convert(json)
    }

    private suspend fun ensureInstalled(deviceId: String, serial: String): Boolean {
        if (isInstalled(serial)) return true
        val apkFile = downloadApk() ?: run {
            addLog(LogLevel.E, "EasyAction", "Failed to download layout dump helper APK", deviceId)
            return false
        }
        addLog(LogLevel.I, "EasyAction", "Installing layout dump helper ($DEVICEKIT_PACKAGE)...", deviceId)
        val result = installApk(deviceId, apkFile.absolutePath)
        if (!result.success) {
            addLog(LogLevel.E, "EasyAction", "Layout dump helper install failed: ${result.message}", deviceId)
            return false
        }
        return isInstalled(serial)
    }

    private fun isInstalled(serial: String): Boolean {
        val result = runner.run(listOf("shell", "pm", "path", DEVICEKIT_PACKAGE), serial = serial)
        return result.success && result.output.contains("package:")
    }

    private fun downloadApk(): File? {
        val cacheFile = File(System.getProperty("java.io.tmpdir"), "wps-adb-devicekit.apk")
        if (cacheFile.exists() && cacheFile.length() > 0L) return cacheFile
        return try {
            URI(DEVICEKIT_APK_URL).toURL().openStream().use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            cacheFile.takeIf { it.length() > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseInstrumentationJson(output: String): String? {
        val prefix = "INSTRUMENTATION_STATUS: json="
        val line = output.lineSequence().firstOrNull { it.contains(prefix) } ?: return null
        val start = line.indexOf(prefix) + prefix.length
        return line.substring(start).trim().takeIf { it.startsWith("{") }
    }

    companion object {
        private const val DEVICEKIT_PACKAGE = "com.mobilenext.devicekit"
        private const val DEVICEKIT_APK_URL =
            "https://github.com/mobile-next/devicekit-android/releases/download/1.2.2/mobilenext-devicekit.apk"
    }
}
