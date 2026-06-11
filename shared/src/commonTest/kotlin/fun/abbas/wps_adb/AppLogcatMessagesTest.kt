package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AppLogcatMessages
import `fun`.abbas.wps_adb.data.LogcatLineParser
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppLogcatMessagesTest {
    @Test
    fun systemId_isUniquePerCall() {
        val first = AppLogcatMessages.systemId("tab-1", "waiting")
        val second = AppLogcatMessages.systemId("tab-1", "waiting")
        assertNotEquals(first, second)
    }

    @Test
    fun parse_logIdsIncludeSessionToAvoidReuseAcrossRestarts() {
        val firstSession = LogcatLineParser.parse("raw line", "device", "tab", sessionId = 1L, lineIndex = 0)
        val secondSession = LogcatLineParser.parse("raw line", "device", "tab", sessionId = 2L, lineIndex = 0)
        assertNotEquals(firstSession.id, secondSession.id)
    }
}
