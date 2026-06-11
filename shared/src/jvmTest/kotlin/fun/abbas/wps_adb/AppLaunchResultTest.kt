package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AppLaunchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLaunchResultTest {
    @Test
    fun isSuccessful_acceptsZeroExitCode() {
        assertTrue(AppLaunchResult.isSuccessful(0, "Starting: Intent { act=android.intent.action.MAIN }"))
    }

    @Test
    fun isSuccessful_acceptsMonkeyEventsInjectedDespiteNonZeroExit() {
        assertTrue(AppLaunchResult.isSuccessful(253, "Events injected: 1"))
    }

    @Test
    fun isSuccessful_rejectsMonkeyWithoutActivities() {
        assertFalse(
            AppLaunchResult.isSuccessful(
                0,
                "** No activities found to run, monkey aborted",
            ),
        )
    }

    @Test
    fun resolveActivityComponent_returnsLastComponentLine() {
        val output = """
            priority=0 preferredOrder=0 match=0x00000000 specificIndex=-1 isDefault=true
            com.example.demo/.MainActivity
        """.trimIndent()

        assertEquals("com.example.demo/.MainActivity", AppLaunchResult.resolveActivityComponent(output))
    }

    @Test
    fun resolveActivityComponent_parsesVerbosePmOutput() {
        val output = """
            Activity #0:
              name=com.wanpishiky.android/com.jsj.tvgamecollections.GameActivity
              packageName=com.wanpishiky.android
        """.trimIndent()

        assertEquals(
            "com.wanpishiky.android/com.jsj.tvgamecollections.GameActivity",
            AppLaunchResult.resolveActivityComponent(output),
        )
    }

    @Test
    fun normalizeComponent_prefixesPackageWhenSlashMissing() {
        assertEquals(
            "com.wanpishiky.android/com.jsj.tvgamecollections.GameActivity",
            AppLaunchResult.normalizeComponent(
                "com.wanpishiky.android",
                "com.jsj.tvgamecollections.GameActivity",
            ),
        )
    }

    @Test
    fun normalizeComponent_keepsExistingComponentFormat() {
        assertEquals(
            "com.example.demo/.MainActivity",
            AppLaunchResult.normalizeComponent("com.example.demo", "com.example.demo/.MainActivity"),
        )
    }

    @Test
    fun isSuccessful_rejectsUnableToResolve() {
        assertFalse(
            AppLaunchResult.isSuccessful(
                0,
                "Unable to resolve activity for Intent { act=android.intent.action.MAIN }",
            ),
        )
    }
}
