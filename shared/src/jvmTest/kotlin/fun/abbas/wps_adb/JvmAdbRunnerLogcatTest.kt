package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmAdbRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmAdbRunnerLogcatTest {
    @Test
    fun supportsNativeLogcatPidFilter_requiresApi29() {
        assertEquals(29, JvmAdbRunner.MIN_SDK_FOR_LOGCAT_PID_FILTER)
        assertFalse(JvmAdbRunner.supportsNativeLogcatPidFilter(23))
        assertFalse(JvmAdbRunner.supportsNativeLogcatPidFilter(28))
        assertTrue(JvmAdbRunner.supportsNativeLogcatPidFilter(29))
        assertTrue(JvmAdbRunner.supportsNativeLogcatPidFilter(34))
        assertFalse(JvmAdbRunner.supportsNativeLogcatPidFilter(null))
    }
}
