package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.LogcatLineParser
import `fun`.abbas.wps_adb.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class LogcatLineParserTest {

    @Test
    fun parse_standardLogcatLine() {
        val log = LogcatLineParser.parse(
            line = "03-25 10:12:33.444  1234  5678 I MyApp: onCreate called",
            deviceId = "device-1",
            tabId = "tab-1",
            sessionId = 42L,
            lineIndex = 0,
        )

        assertEquals("03-25 10:12:33.444", log.timestamp)
        assertEquals(LogLevel.I, log.level)
        assertEquals("MyApp", log.tag)
        assertEquals("onCreate called", log.message)
        assertEquals("device-1", log.deviceId)
    }

    @Test
    fun parse_nonStandardLine_usesRawMessage() {
        val log = LogcatLineParser.parse(
            line = "some unstructured output",
            deviceId = null,
            tabId = "tab-2",
            sessionId = 99L,
            lineIndex = 3,
        )

        assertEquals(LogLevel.I, log.level)
        assertEquals("some unstructured output", log.message)
    }

    @Test
    fun extractProcessId_readsThreadtimePidColumn() {
        assertEquals(
            1234,
            LogcatLineParser.extractProcessId("03-25 10:12:33.444  1234  5678 I MyApp: onCreate called"),
        )
    }

    @Test
    fun belongsToProcess_matchesOnlyTargetPid() {
        val line = "03-25 10:12:33.444  1234  5678 I MyApp: onCreate called"

        assertEquals(true, LogcatLineParser.belongsToProcess(line, 1234))
        assertEquals(false, LogcatLineParser.belongsToProcess(line, 9999))
        assertEquals(false, LogcatLineParser.belongsToProcess("Unrecognized Option", 1234))
    }
}
