package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.PortRangeValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortRangeValidatorTest {
    @Test
    fun isInRange_acceptsConfiguredBounds() {
        val settings = AppSettings(minPort = 5555, maxPort = 5585)
        assertTrue(PortRangeValidator.isInRange(5555, settings))
        assertTrue(PortRangeValidator.isInRange(5585, settings))
        assertFalse(PortRangeValidator.isInRange(5554, settings))
        assertFalse(PortRangeValidator.isInRange(5586, settings))
    }

    @Test
    fun normalizedRange_handlesReversedInput() {
        assertEquals(5500..5599, PortRangeValidator.normalizedRange(5599, 5500))
    }
}
