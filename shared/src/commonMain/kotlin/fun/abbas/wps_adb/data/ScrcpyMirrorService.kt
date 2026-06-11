package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions

data class ScrcpyStartResult(
    val success: Boolean,
    val message: String,
)

interface ScrcpyMirrorService {
    fun isAvailable(): Boolean
    fun start(
        tabId: String,
        serial: String,
        deviceName: String,
        options: ScrcpyConnectionOptions,
    ): ScrcpyStartResult
    fun stop(tabId: String)
    fun stopAll()
    fun isRunning(tabId: String): Boolean
    fun setExitListener(listener: ((tabId: String, exitCode: Int, intentionalStop: Boolean) -> Unit)?)
}

class NoOpScrcpyMirrorService : ScrcpyMirrorService {
    override fun isAvailable(): Boolean = false

    override fun start(
        tabId: String,
        serial: String,
        deviceName: String,
        options: ScrcpyConnectionOptions,
    ): ScrcpyStartResult =
        ScrcpyStartResult(false, "Desktop + scrcpy required")

    override fun stop(tabId: String) = Unit

    override fun stopAll() = Unit

    override fun isRunning(tabId: String): Boolean = false

    override fun setExitListener(listener: ((tabId: String, exitCode: Int, intentionalStop: Boolean) -> Unit)?) = Unit
}
