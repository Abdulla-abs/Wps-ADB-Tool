package `fun`.abbas.wps_adb.data

data class DeviceShellStartResult(
    val success: Boolean,
    val message: String,
)

interface DeviceShellService {
    fun isAvailable(): Boolean
    fun start(sessionId: String, serial: String): DeviceShellStartResult
    fun stop(sessionId: String)
    fun stopAll()
    fun isRunning(sessionId: String): Boolean
    fun createTerminalComponent(sessionId: String): Any?
    fun notifyTerminalMounted(sessionId: String)
    fun setExitListener(listener: ((sessionId: String, exitCode: Int) -> Unit)?)
}

class NoOpDeviceShellService : DeviceShellService {
    override fun isAvailable(): Boolean = false

    override fun start(sessionId: String, serial: String): DeviceShellStartResult =
        DeviceShellStartResult(false, "Desktop + ADB required")

    override fun stop(sessionId: String) = Unit

    override fun stopAll() = Unit

    override fun isRunning(sessionId: String): Boolean = false

    override fun createTerminalComponent(sessionId: String): Any? = null

    override fun notifyTerminalMounted(sessionId: String) = Unit

    override fun setExitListener(listener: ((sessionId: String, exitCode: Int) -> Unit)?) = Unit
}

expect fun createDeviceShellService(adbPathProvider: () -> String): DeviceShellService
