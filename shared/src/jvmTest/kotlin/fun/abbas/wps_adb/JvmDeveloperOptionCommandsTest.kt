package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmDeveloperOptionCommands
import `fun`.abbas.wps_adb.model.EasyActionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmDeveloperOptionCommandsTest {
    @Test
    fun applyEnabled_showRefreshRate_usesSurfaceFlingerServiceCall() {
        val commands = JvmDeveloperOptionCommands.applyEnabled(EasyActionKind.TOGGLE_SHOW_REFRESH_RATE, true)
        assertEquals(
            listOf(listOf("shell", "service", "call", "SurfaceFlinger", "1034", "i32", "1")),
            commands,
        )
    }

    @Test
    fun applyEnabled_gpuOverdraw_usesShowModeAndPokesActivity() {
        val commands = JvmDeveloperOptionCommands.applyEnabled(EasyActionKind.TOGGLE_GPU_OVERDRAW, true)
        assertEquals(
            listOf(
                listOf("shell", "setprop", "debug.hwui.overdraw", "show"),
                listOf("shell", "service", "call", "activity", "1599295570"),
            ),
            commands,
        )
    }

    @Test
    fun applyEnabled_layoutBounds_usesDebugLayoutPropAndPokesActivity() {
        val commands = JvmDeveloperOptionCommands.applyEnabled(EasyActionKind.TOGGLE_SHOW_LAYOUT_BOUNDS, true)
        assertEquals(
            listOf(
                listOf("shell", "setprop", "debug.layout", "true"),
                listOf("shell", "service", "call", "activity", "1599295570"),
            ),
            commands,
        )
    }

    @Test
    fun applyEnabled_dontKeepActivities_updatesSettingsAndActivityService() {
        val commands = JvmDeveloperOptionCommands.applyEnabled(EasyActionKind.TOGGLE_DONT_KEEP_ACTIVITIES, true)
        assertEquals(
            listOf(
                listOf("shell", "settings", "put", "global", "always_finish_activities", "1"),
                listOf("shell", "service", "call", "activity", "43", "i32", "1"),
                listOf("shell", "service", "call", "activity", "1599295570"),
            ),
            commands,
        )
    }

    @Test
    fun parseEnabled_readsParcelAndSettingsValues() {
        val parcelOutput = """
            Result: Parcel(
              0x00000000: 00000001 00000000 00000000 00000000 '................'
            )
        """.trimIndent()
        assertTrue(JvmDeveloperOptionCommands.parseEnabled(EasyActionKind.TOGGLE_SHOW_REFRESH_RATE, parcelOutput) == true)
        assertTrue(JvmDeveloperOptionCommands.parseEnabled(EasyActionKind.TOGGLE_POINTER_LOCATION, "1") == true)
        assertFalse(JvmDeveloperOptionCommands.parseEnabled(EasyActionKind.TOGGLE_POINTER_LOCATION, "0") == true)
        assertTrue(JvmDeveloperOptionCommands.parseEnabled(EasyActionKind.TOGGLE_GPU_OVERDRAW, "show") == true)
        assertTrue(JvmDeveloperOptionCommands.parseEnabled(EasyActionKind.TOGGLE_GPU_PROFILE_BARS, "visual_bars") == true)
    }
}
