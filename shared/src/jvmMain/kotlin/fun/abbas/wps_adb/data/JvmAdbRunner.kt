package `fun`.abbas.wps_adb.data

import java.io.File

data class AdbProcessResult(
    val exitCode: Int,
    val output: String,
) {
    val success: Boolean get() = exitCode == 0
}

data class LogcatSession(
    val process: Process,
    val filterPidClientSide: Boolean,
    val pid: Int,
)

class JvmAdbRunner(
    private val adbPathProvider: () -> String = { "adb" },
) {
    fun isAvailable(): Boolean = run(listOf("version")).success

    fun captureScreenshot(serial: String, outputFile: File): Boolean {
        val command = buildList {
            add(resolveAdbPath())
            add("-s")
            add(serial)
            addAll(listOf("exec-out", "screencap", "-p"))
        }
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val bytes = process.inputStream.use { it.readBytes() }
            val exitCode = process.waitFor()
            if (exitCode != 0 || bytes.size < 8) return false
            if (bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte()) return false
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(bytes)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resolveProcessId(serial: String, packageName: String): Int? {
        resolveProcessIdViaPgrep(serial, packageName)?.let { return it }
        resolveProcessIdViaPs(serial, packageName)?.let { return it }
        ProcessIdResolver.parseSinglePid(
            run(listOf("shell", "pidof", "-s", packageName), serial).output,
        )?.let { return it }
        ProcessIdResolver.parseSinglePid(
            run(listOf("shell", "pidof", packageName), serial).output,
        )?.let { return it }
        return null
    }

    private fun resolveProcessIdViaPgrep(serial: String, packageName: String): Int? {
        val result = run(listOf("shell", "pgrep", "-f", packageName), serial)
        return ProcessIdResolver.parseSinglePid(result.output)
    }

    private fun resolveProcessIdViaPs(serial: String, packageName: String): Int? {
        val modernPs = run(listOf("shell", "ps", "-A"), serial)
        ProcessIdResolver.parseFromPs(modernPs.output, packageName)?.let { return it }
        val legacyPs = run(listOf("shell", "ps"), serial)
        return ProcessIdResolver.parseFromPs(legacyPs.output, packageName)
    }

    fun resolveDeviceSdkInt(serial: String): Int? =
        run(listOf("shell", "getprop", "ro.build.version.sdk"), serial)
            .output
            .trim()
            .toIntOrNull()

    fun supportsNativeLogcatPidFilter(serial: String): Boolean =
        supportsNativeLogcatPidFilter(resolveDeviceSdkInt(serial))

    fun startLogcat(serial: String, pid: Int): LogcatSession {
        val useNativePidFilter = supportsNativeLogcatPidFilter(serial)
        val command = buildList {
            add(resolveAdbPath())
            add("-s")
            add(serial)
            add("logcat")
            if (useNativePidFilter) {
                add("--pid=$pid")
            } else {
                add("-v")
                add("threadtime")
            }
        }
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        return LogcatSession(
            process = process,
            filterPidClientSide = !useNativePidFilter,
            pid = pid,
        )
    }

    fun startGlobalLogcat(serial: String): LogcatSession {
        val command = globalLogcatCommand(resolveAdbPath(), serial)
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        return LogcatSession(process = process, filterPidClientSide = false, pid = 0)
    }

    fun launchApp(
        serial: String,
        packageName: String,
        launchActivity: String? = null,
    ): AdbProcessResult {
        val attempts = mutableListOf<AdbProcessResult>()

        fun record(result: AdbProcessResult): AdbProcessResult? {
            attempts += result
            return result.takeIf { AppLaunchResult.isSuccessful(it.exitCode, it.output) }
        }

        AppLaunchResult.normalizeComponent(packageName, launchActivity)?.let { component ->
            record(run(listOf("shell", "am", "start", "-n", component), serial))?.let { return it }
        }

        val resolveResult = run(
            listOf("shell", "cmd", "package", "resolve-activity", "--brief", packageName),
            serial,
        )
        AppLaunchResult.resolveActivityComponent(resolveResult.output)?.let { component ->
            record(run(listOf("shell", "am", "start", "-n", component), serial))?.let { return it }
        }

        val pmResolveResult = run(
            listOf("shell", "pm", "resolve-activity", "--brief", packageName),
            serial,
        )
        val pmBriefComponent = AppLaunchResult.resolveActivityComponent(pmResolveResult.output)
        val cmdBriefComponent = AppLaunchResult.resolveActivityComponent(resolveResult.output)
        pmBriefComponent?.let { component ->
            if (component != cmdBriefComponent) {
                record(run(listOf("shell", "am", "start", "-n", component), serial))?.let { return it }
            }
        }

        val pmResolveFullResult = run(
            listOf(
                "shell",
                "pm",
                "resolve-activity",
                "-a",
                "android.intent.action.MAIN",
                "-c",
                "android.intent.category.LAUNCHER",
                packageName,
            ),
            serial,
        )
        AppLaunchResult.resolveActivityComponent(pmResolveFullResult.output)?.let { component ->
            if (component != cmdBriefComponent && component != pmBriefComponent) {
                record(run(listOf("shell", "am", "start", "-n", component), serial))?.let { return it }
            }
        }

        record(
            run(
                listOf(
                    "shell",
                    "am",
                    "start",
                    "-a",
                    "android.intent.action.MAIN",
                    "-c",
                    "android.intent.category.LAUNCHER",
                    "-p",
                    packageName,
                ),
                serial,
            ),
        )?.let { return it }

        record(
            run(
                listOf(
                    "shell",
                    "monkey",
                    "-p",
                    packageName,
                    "-c",
                    "android.intent.category.LAUNCHER",
                    "1",
                ),
                serial,
            ),
        )?.let { return it }

        return attempts.lastOrNull() ?: AdbProcessResult(-1, "No launch attempt made")
    }

    fun uninstallApp(serial: String, packageName: String): AdbProcessResult {
        val attempts = mutableListOf<AdbProcessResult>()

        fun record(result: AdbProcessResult): AdbProcessResult? {
            attempts += result
            return result.takeIf { AppUninstallResult.isSuccessful(it.exitCode, it.output) }
        }

        record(run(listOf("uninstall", packageName), serial))?.let { return it }
        record(run(listOf("shell", "pm", "uninstall", packageName), serial))?.let { return it }
        record(run(listOf("shell", "pm", "uninstall", "--user", "0", packageName), serial))?.let { return it }

        return attempts.lastOrNull() ?: AdbProcessResult(-1, "No uninstall attempt made")
    }

    fun installApk(file: File, serial: String): AdbProcessResult {
        return if (file.extension.equals("apk", ignoreCase = true)) {
            run(listOf("install", "-r", file.absolutePath), serial)
        } else {
            installViaPushAndPm(file, serial)
        }
    }

    private fun installViaPushAndPm(file: File, serial: String): AdbProcessResult {
        val remotePath = "/data/local/tmp/wps_adb_${System.nanoTime()}.apk"
        val push = run(listOf("push", file.absolutePath, remotePath), serial)
        if (!push.success) {
            return AdbProcessResult(
                exitCode = push.exitCode,
                output = "Push failed: ${push.output}",
            )
        }
        val install = run(listOf("shell", "pm", "install", "-r", remotePath), serial)
        run(listOf("shell", "rm", "-f", remotePath), serial)
        return install
    }

    fun pair(endpoint: String, pairingCode: String): AdbProcessResult =
        run(pairCommandArgs(endpoint, pairingCode))

    fun run(args: List<String>, serial: String? = null): AdbProcessResult {
        val command = buildList {
            add(resolveAdbPath())
            if (serial != null) {
                add("-s")
                add(serial)
            }
            addAll(args)
        }
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            AdbProcessResult(process.waitFor(), output.trim())
        } catch (e: Exception) {
            AdbProcessResult(-1, e.message ?: "Failed to execute adb")
        }
    }

    private fun resolveAdbPath(): String =
        ExecutableLocator.resolveAdbPath(adbPathProvider())

    companion object {
        const val MIN_SDK_FOR_LOGCAT_PID_FILTER = 29

        fun globalLogcatCommand(adbPath: String, serial: String): List<String> =
            listOf(adbPath, "-s", serial, "logcat", "-v", "threadtime")

        fun pairCommandArgs(endpoint: String, pairingCode: String): List<String> =
            listOf("pair", endpoint, pairingCode)

        fun supportsNativeLogcatPidFilter(sdkInt: Int?): Boolean =
            sdkInt != null && sdkInt >= MIN_SDK_FOR_LOGCAT_PID_FILTER

        fun isAvailable(path: String = "adb"): Boolean {
            return try {
                val file = File(path)
                val command = if (file.isAbsolute && file.exists()) file.absolutePath else path
                ProcessBuilder(command, "version")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor() == 0
            } catch (_: Exception) {
                false
            }
        }
    }
}
