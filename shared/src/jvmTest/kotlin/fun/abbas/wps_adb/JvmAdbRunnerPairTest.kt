package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmAdbRunner
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmAdbRunnerPairTest {

    @Test
    fun pairCommandArgs_buildsCorrectCommand() {
        val args = JvmAdbRunner.pairCommandArgs("192.168.0.5:12345", "abc123")
        assertEquals(listOf("pair", "192.168.0.5:12345", "abc123"), args)
    }
}
