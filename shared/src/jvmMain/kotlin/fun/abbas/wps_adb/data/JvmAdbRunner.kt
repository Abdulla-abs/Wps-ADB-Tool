package `fun`.abbas.wps_adb.data

import java.io.File

data class AdbProcessResult(
    val exitCode: Int,
    val output: String,
) {
    val success: Boolean get() = exitCode == 0
}

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

    private fun resolveAdbPath(): String {
        val configured = adbPathProvider().trim()
        if (configured.isEmpty() || configured == "adb") return "adb"
        val file = File(configured)
        return when {
            file.isAbsolute && file.exists() -> file.absolutePath
            !file.isAbsolute && file.exists() -> file.absolutePath
            else -> "adb"
        }
    }

    companion object {
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
