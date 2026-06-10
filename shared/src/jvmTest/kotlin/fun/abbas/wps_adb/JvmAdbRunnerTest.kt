package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmAdbRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmAdbRunnerTest {
    @Test
    fun run_usesPathFromProviderWhenConfiguredPathMissing() {
        val runner = JvmAdbRunner { "/usr/local/bin/adb" }
        val result = runner.run(listOf("version"))
        assertTrue(result.success, "Should fall back to PATH adb when configured path is missing")
        assertTrue(result.output.contains("Android Debug Bridge"), "Unexpected output: ${result.output}")
    }

    @Test
    fun run_parsesDevicesOutput() {
        val runner = JvmAdbRunner { "adb" }
        val result = runner.run(listOf("devices", "-l"))
        assertTrue(result.success, "adb devices failed: ${result.output}")
    }
}
