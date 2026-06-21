package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ForegroundPackageParser
import kotlin.test.Test
import kotlin.test.assertEquals

class ForegroundPackageParserTest {
    @Test
    fun parse_prefersTopResumedActivity() {
        val activity = "  topResumedActivity=ActivityRecord{abc u0 com.example.app/.MainActivity t123}"
        val window = "  mCurrentFocus=Window{def u0 com.other.app/.OtherActivity}"
        assertEquals(
            "com.example.app",
            ForegroundPackageParser.parse(activity, window, ""),
        )
    }

    @Test
    fun parse_usesCurrentFocusWhenNoResumedActivity() {
        val window = "  mCurrentFocus=Window{def u0 com.example.app/.MainActivity}"
        assertEquals(
            "com.example.app",
            ForegroundPackageParser.parse("", window, ""),
        )
    }

    @Test
    fun parse_skipsSystemUiFocusWhenActivityAvailable() {
        val activity = "  ResumedActivity: ActivityRecord{abc u0 com.example.app/.MainActivity t1}"
        val window = "  mCurrentFocus=Window{def u0 com.android.systemui/.StatusBar}"
        assertEquals(
            "com.example.app",
            ForegroundPackageParser.parse(activity, window, ""),
        )
    }
}
