package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.ui.device.CarbonJediTermSettingsProvider
import com.jediterm.terminal.ui.JediTermWidget
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

internal data class ShellSession(
    val process: PtyProcess,
    val hostPanel: TerminalHostPanel,
    val widget: JediTermWidget,
    var sessionStarted: Boolean = false,
    var intentionalStop: Boolean = false,
)

class JvmDeviceShellService(
    private val adbPathProvider: () -> String,
) : DeviceShellService {
    private val sessions = ConcurrentHashMap<String, ShellSession>()
    @Volatile
    private var exitListener: ((sessionId: String, exitCode: Int) -> Unit)? = null

    override fun isAvailable(): Boolean = JvmAdbRunner.isAvailable()

    override fun start(sessionId: String, serial: String): DeviceShellStartResult {
        if (isRunning(sessionId)) {
            return DeviceShellStartResult(true, "Already running")
        }
        if (!isAvailable()) {
            return DeviceShellStartResult(false, "ADB not available")
        }

        return try {
            val adbPath = ExecutableLocator.resolveAdbPath(adbPathProvider())
            val command = buildShellCommand(adbPath, serial)
            val environment = HashMap(System.getenv())
            environment["ADB"] = adbPath
            environment["TERM"] = "xterm-256color"

            val process = PtyProcessBuilder()
                .setCommand(command)
                .setEnvironment(environment)
                .setRedirectErrorStream(true)
                .start()

            val widget = JediTermWidget(CarbonJediTermSettingsProvider())
            widget.ttyConnector = AdbShellTtyConnector(process)
            val hostPanel = TerminalHostPanel(widget)

            sessions[sessionId] = ShellSession(
                process = process,
                hostPanel = hostPanel,
                widget = widget,
            )
            watchProcessExit(sessionId, process)

            if (!process.isAlive) {
                sessions.remove(sessionId)
                widget.close()
                return DeviceShellStartResult(
                    success = false,
                    message = "adb shell exited with code ${process.exitValue()}",
                )
            }
            DeviceShellStartResult(true, "Shell started")
        } catch (e: Exception) {
            sessions.remove(sessionId)
            DeviceShellStartResult(false, e.message ?: "Failed to start shell")
        }
    }

    override fun stop(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        session.intentionalStop = true
        runCatching { session.widget.close() }
        destroyProcess(session.process)
    }

    override fun stopAll() {
        sessions.keys.toList().forEach(::stop)
    }

    override fun isRunning(sessionId: String): Boolean =
        sessions[sessionId]?.process?.isAlive == true

    override fun createTerminalComponent(sessionId: String): Any? =
        sessions[sessionId]?.hostPanel

    override fun notifyTerminalMounted(sessionId: String) {
        val session = sessions[sessionId] ?: return
        if (!session.sessionStarted) {
            session.widget.start()
            session.sessionStarted = true
        }
        SwingUtilities.invokeLater {
            JediTermSizeSync.syncNow(session.widget)
            session.hostPanel.requestTerminalFocus()
        }
    }

    override fun setExitListener(listener: ((sessionId: String, exitCode: Int) -> Unit)?) {
        exitListener = listener
    }

    private fun watchProcessExit(sessionId: String, process: Process) {
        process.onExit().thenAccept {
            val session = sessions.remove(sessionId)
            runCatching { session?.widget?.close() }
            if (session?.intentionalStop != true) {
                val exitCode = process.exitValue()
                exitListener?.invoke(sessionId, exitCode)
            }
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
        private const val PROCESS_DESTROY_TIMEOUT_MS = 3_000L

        fun buildShellCommand(adbPath: String, serial: String): Array<String> =
            arrayOf(adbPath, "-s", serial, "shell")
    }
}
