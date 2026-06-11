package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ScrcpyCommandBuilder
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class ScrcpySession(
    val process: Process,
    var intentionalStop: Boolean = false,
)

typealias ScrcpyProcessStarter = (command: List<String>, environment: Map<String, String>) -> Process

class JvmScrcpyMirrorService(
    private val scrcpyPathProvider: () -> String,
    private val adbPathProvider: () -> String,
    private val processStarter: ScrcpyProcessStarter = JvmScrcpyMirrorService::defaultProcessStarter,
) : ScrcpyMirrorService {
    private val sessions = ConcurrentHashMap<String, ScrcpySession>()
    @Volatile
    private var exitListener: ((tabId: String, exitCode: Int, intentionalStop: Boolean) -> Unit)? = null

    override fun isAvailable(): Boolean = checkVersion(resolveScrcpyPath(scrcpyPathProvider()))

    override fun start(
        tabId: String,
        serial: String,
        deviceName: String,
        options: ScrcpyConnectionOptions,
    ): ScrcpyStartResult {
        if (isRunning(tabId)) {
            return ScrcpyStartResult(true, "Already running")
        }
        if (!isAvailable()) {
            return ScrcpyStartResult(false, "scrcpy not found — configure path in Settings")
        }

        val command = buildCommand(
            scrcpyPath = resolveScrcpyPath(scrcpyPathProvider()),
            serial = serial,
            deviceName = deviceName,
            options = options,
        )
        val environment = mapOf("ADB" to ExecutableLocator.resolveAdbPath(adbPathProvider()))

        return try {
            val process = processStarter(command, environment)
            sessions[tabId] = ScrcpySession(process = process)
            watchProcessExit(tabId, process)
            if (!process.isAlive) {
                val output = readProcessOutput(process)
                sessions.remove(tabId)
                val exitCode = process.exitValue()
                return ScrcpyStartResult(
                    success = false,
                    message = formatExitMessage(exitCode, output),
                )
            }
            ScrcpyStartResult(true, "scrcpy started")
        } catch (e: Exception) {
            sessions.remove(tabId)
            ScrcpyStartResult(false, e.message ?: "Failed to start scrcpy")
        }
    }

    override fun stop(tabId: String) {
        val session = sessions.remove(tabId) ?: return
        session.intentionalStop = true
        destroyProcess(session.process)
    }

    override fun stopAll() {
        val tabIds = sessions.keys.toList()
        tabIds.forEach(::stop)
    }

    override fun isRunning(tabId: String): Boolean =
        sessions[tabId]?.process?.isAlive == true

    override fun setExitListener(listener: ((tabId: String, exitCode: Int, intentionalStop: Boolean) -> Unit)?) {
        exitListener = listener
    }

    private fun watchProcessExit(tabId: String, process: Process) {
        process.onExit().thenAccept {
            val session = sessions.remove(tabId)
            val intentional = session?.intentionalStop == true
            val exitCode = process.exitValue()
            exitListener?.invoke(tabId, exitCode, intentional)
        }
    }

    private fun destroyProcess(process: Process) {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(PROCESS_DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
        }
    }

    companion object {
        const val WINDOW_TITLE_PREFIX = "WpsAdbTool - "
        private const val PROCESS_DESTROY_TIMEOUT_MS = 3_000L
        private const val OUTPUT_TAIL_CHARS = 200

        fun buildCommand(
            scrcpyPath: String,
            serial: String,
            deviceName: String,
            options: ScrcpyConnectionOptions = ScrcpyConnectionOptions(),
        ): List<String> = buildList {
            add(scrcpyPath)
            add("-s")
            add(serial)
            add("--window-title")
            add("$WINDOW_TITLE_PREFIX$deviceName")
            addAll(ScrcpyCommandBuilder.connectionArgs(options))
        }

        fun resolveScrcpyPath(configured: String): String =
            ExecutableLocator.resolveScrcpyPath(configured)

        fun checkVersion(scrcpyPath: String): Boolean =
            try {
                val process = ProcessBuilder(scrcpyPath, "--version")
                    .redirectErrorStream(true)
                    .start()
                process.waitFor() == 0
            } catch (_: Exception) {
                false
            }

        fun defaultProcessStarter(command: List<String>, environment: Map<String, String>): Process =
            ProcessBuilder(command)
                .apply { environment().putAll(environment) }
                .redirectErrorStream(true)
                .start()

        fun readProcessOutput(process: Process): String =
            runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("")

        fun formatExitMessage(exitCode: Int, output: String): String {
            val tail = output.trim().takeLast(OUTPUT_TAIL_CHARS)
            return if (tail.isBlank()) {
                "scrcpy exited with code $exitCode"
            } else {
                "scrcpy exited with code $exitCode: $tail"
            }
        }
    }
}
