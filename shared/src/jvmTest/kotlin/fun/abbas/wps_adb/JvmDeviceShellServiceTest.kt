package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmDeviceShellService
import kotlin.test.Test
import kotlin.test.assertContentEquals

class JvmDeviceShellServiceTest {
    @Test
    fun buildShellCommand_includesSerialAndShellSubcommand() {
        val command = JvmDeviceShellService.buildShellCommand(
            adbPath = "/opt/adb",
            serial = "emulator-5554",
        )
        assertContentEquals(arrayOf("/opt/adb", "-s", "emulator-5554", "shell"), command)
    }
}
