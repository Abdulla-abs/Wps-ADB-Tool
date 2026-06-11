package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.LogLevel

object AppLogFilter {
    val defaultLevels: Set<LogLevel> = LogLevel.entries.toSet()

    fun apply(
        logs: List<AdbLog>,
        query: String,
        enabledLevels: Set<LogLevel>,
    ): List<AdbLog> {
        val normalizedQuery = query.trim().lowercase()
        return logs.filter { log ->
            log.level in enabledLevels && matchesQuery(log, normalizedQuery)
        }
    }

    fun isActive(query: String, enabledLevels: Set<LogLevel>): Boolean =
        query.isNotBlank() || enabledLevels.size < LogLevel.entries.size

    private fun matchesQuery(log: AdbLog, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        return log.tag.lowercase().contains(normalizedQuery) ||
            log.message.lowercase().contains(normalizedQuery)
    }
}
