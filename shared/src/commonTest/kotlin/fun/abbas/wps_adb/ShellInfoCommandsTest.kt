package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.model.ShellInfoCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShellInfoCommandsTest {
    @Test
    fun shellCommand_mapsDeviceModelToGetprop() {
        assertEquals(
            "getprop ro.product.model",
            ShellInfoCommands.shellCommand(EasyActionKind.INFO_DEVICE_MODEL),
        )
    }

    @Test
    fun wrappedShellInput_includesEchoAndCommand() {
        val input = ShellInfoCommands.wrappedShellInput(EasyActionKind.INFO_SCREEN_SIZE)
        assertNotNull(input)
        assertTrue(input!!.contains("wm size"))
        assertTrue(input.contains(">> wm size"))
    }
}
