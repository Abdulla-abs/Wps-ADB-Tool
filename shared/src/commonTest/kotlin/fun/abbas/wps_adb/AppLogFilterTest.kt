package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AppLogFilter
import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogFilterTest {
    private val logs = listOf(
        AdbLog("1", "10:00:00.000", "MyApp", LogLevel.I, "onCreate", "device"),
        AdbLog("2", "10:00:00.100", "System", LogLevel.W, "low memory", "device"),
        AdbLog("3", "10:00:00.200", "MyApp", LogLevel.E, "crash in main", "device"),
    )

    @Test
    fun apply_returnsAllLogsWhenFilterInactive() {
        val result = AppLogFilter.apply(logs, query = "", enabledLevels = AppLogFilter.defaultLevels)

        assertEquals(3, result.size)
        assertFalse(AppLogFilter.isActive("", AppLogFilter.defaultLevels))
    }

    @Test
    fun apply_filtersByKeywordInTagOrMessage() {
        val result = AppLogFilter.apply(logs, query = "myapp", enabledLevels = AppLogFilter.defaultLevels)

        assertEquals(2, result.size)
        assertEquals(listOf("1", "3"), result.map { it.id })
        assertTrue(AppLogFilter.isActive("myapp", AppLogFilter.defaultLevels))
    }

    @Test
    fun apply_filtersByEnabledLevels() {
        val result = AppLogFilter.apply(
            logs,
            query = "",
            enabledLevels = setOf(LogLevel.W, LogLevel.E),
        )

        assertEquals(2, result.size)
        assertEquals(listOf("2", "3"), result.map { it.id })
        assertTrue(AppLogFilter.isActive("", setOf(LogLevel.W, LogLevel.E)))
    }

    @Test
    fun apply_combinesKeywordAndLevelFilters() {
        val result = AppLogFilter.apply(
            logs,
            query = "crash",
            enabledLevels = setOf(LogLevel.I, LogLevel.E),
        )

        assertEquals(1, result.size)
        assertEquals("3", result.single().id)
    }
}
