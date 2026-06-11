package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmAdbRunner
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmAdbRunnerGlobalLogcatTest {
    @Test
    fun `global logcat command includes threadtime and serial`() {
        val command = JvmAdbRunner.globalLogcatCommand(
            adbPath = "C:/adb/adb.exe",
            serial = "emulator-5554",
        )
        assertEquals(
            listOf("C:/adb/adb.exe", "-s", "emulator-5554", "logcat", "-v", "threadtime"),
            command,
        )
    }
}
