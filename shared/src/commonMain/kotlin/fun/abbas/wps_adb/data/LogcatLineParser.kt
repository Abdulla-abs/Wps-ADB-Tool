package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.LogLevel

object LogcatLineParser {
    private val threadTimePidRegex = Regex(
        """^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+(\d+)\s+\d+\s+""",
    )
    private val slashTagRegex = Regex(
        """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])/([^:]+):\s*(.*)$""",
    )
    private val spaceTagRegex = Regex(
        """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+):\s*(.*)$""",
    )

    fun extractProcessId(line: String): Int? =
        threadTimePidRegex.find(line.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun belongsToProcess(line: String, pid: Int): Boolean = extractProcessId(line) == pid

    fun parse(line: String, deviceId: String?, tabId: String, sessionId: Long, lineIndex: Int): AdbLog {
        val trimmed = line.trim()
        sequenceOf(slashTagRegex, spaceTagRegex).forEach { regex ->
            regex.matchEntire(trimmed)?.let { match ->
                val timestamp = match.groupValues[1]
                val level = parseLevel(match.groupValues[2])
                val tag = match.groupValues[3].trim()
                val message = match.groupValues[4]
                return AdbLog(
                    id = "logcat_${tabId}_${sessionId}_$lineIndex",
                    timestamp = timestamp,
                    tag = tag,
                    level = level,
                    message = message,
                    deviceId = deviceId,
                )
            }
        }
        return AdbLog(
            id = "logcat_${tabId}_${sessionId}_$lineIndex",
            timestamp = "",
            tag = "logcat",
            level = LogLevel.I,
            message = trimmed,
            deviceId = deviceId,
        )
    }

    private fun parseLevel(token: String): LogLevel = when (token.uppercase()) {
        "V" -> LogLevel.V
        "D" -> LogLevel.D
        "I" -> LogLevel.I
        "W" -> LogLevel.W
        "E", "F" -> LogLevel.E
        else -> LogLevel.I
    }
}
