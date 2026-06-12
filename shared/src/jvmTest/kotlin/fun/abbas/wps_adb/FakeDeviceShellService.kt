package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.DeviceShellService
import `fun`.abbas.wps_adb.data.DeviceShellStartResult

class FakeDeviceShellService : DeviceShellService {
    var startCount = 0
    var stopCount = 0

    override fun isAvailable(): Boolean = true

    override fun start(sessionId: String, serial: String): DeviceShellStartResult {
        startCount++
        return DeviceShellStartResult(true, "Shell started")
    }

    override fun stop(sessionId: String) {
        stopCount++
    }

    override fun stopAll() = Unit

    override fun isRunning(sessionId: String): Boolean = true

    override fun createTerminalComponent(sessionId: String): Any? = null

    var mountedCount = 0

    override fun notifyTerminalMounted(sessionId: String) {
        mountedCount++
    }

    override fun setExitListener(listener: ((sessionId: String, exitCode: Int) -> Unit)?) = Unit
}
