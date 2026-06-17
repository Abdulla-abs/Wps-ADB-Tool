package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.RecentDecompileProject
import java.io.File

actual object DecompileWorkspaceStore {
    private const val MAX_ENTRIES = 8

    actual fun loadRecent(recentFile: String): List<RecentDecompileProject> {
        val primary = File(recentFile)
        if (primary.exists()) {
            val result = runCatching { parseRecentJson(primary.readText()) }.getOrElse { emptyList() }
            if (result.isNotEmpty()) return result
        }
        val legacy = File(AppDataPaths.legacyDecompileRoot(), "recent.json")
        if (legacy.exists()) {
            return runCatching { parseRecentJson(legacy.readText()) }.getOrElse { emptyList() }
        }
        return emptyList()
    }

    actual fun saveRecent(recentFile: String, project: RecentDecompileProject) {
        val file = File(recentFile)
        file.parentFile?.mkdirs()
        val updated = (listOf(project) + loadRecent(recentFile).filter { it.workspacePath != project.workspacePath })
            .sortedByDescending { it.lastOpenedAtMillis }
            .take(MAX_ENTRIES)
        file.writeText(toRecentJson(updated))
    }

    actual fun removeRecent(recentFile: String, workspacePath: String) {
        val file = File(recentFile)
        if (!file.exists()) return
        val updated = loadRecent(recentFile).filter { it.workspacePath != workspacePath }
        if (updated.isEmpty()) {
            file.delete()
        } else {
            file.writeText(toRecentJson(updated))
        }
    }

    actual fun deleteProject(recentFile: String, project: RecentDecompileProject) {
        runCatching { File(project.workspacePath).deleteRecursively() }
        removeRecent(recentFile, project.workspacePath)
    }

    private fun escapeJson(value: String): String = buildString(value.length + 8) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun toRecentJson(entries: List<RecentDecompileProject>): String = buildString {
        append('[')
        entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append('{')
            append("\"apkPath\":\"").append(escapeJson(entry.apkPath)).append('"')
            append(",\"workspacePath\":\"").append(escapeJson(entry.workspacePath)).append('"')
            append(",\"packageName\":\"").append(escapeJson(entry.packageName)).append('"')
            append(",\"apkFileName\":\"").append(escapeJson(entry.apkFileName)).append('"')
            append(",\"lastOpenedAtMillis\":").append(entry.lastOpenedAtMillis)
            append('}')
        }
        append(']')
    }

    private fun parseRecentJson(raw: String): List<RecentDecompileProject> {
        val trimmed = raw.trim()
        if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) return emptyList()
        val body = trimmed.substring(1, trimmed.length - 1).trim()
        if (body.isEmpty()) return emptyList()
        return splitJsonObjects(body).mapNotNull(::parseRecentObject)
    }

    private fun splitJsonObjects(body: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        body.forEachIndexed { index, ch ->
            when (ch) {
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects += body.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun parseRecentObject(raw: String): RecentDecompileProject? {
        fun field(name: String): String? {
            val pattern = Regex("""\"$name\"\s*:\s*\"((?:\\.|[^\"])*)\"\s*""")
            return pattern.find(raw)?.groupValues?.get(1)?.let(::unescapeJson)
        }
        val lastOpened = Regex(""""lastOpenedAtMillis"\s*:\s*(\d+)""")
            .find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val apkPath = field("apkPath") ?: return null
        val workspacePath = field("workspacePath") ?: return null
        val packageName = field("packageName") ?: return null
        val apkFileName = field("apkFileName") ?: return null
        return RecentDecompileProject(
            apkPath = apkPath,
            workspacePath = workspacePath,
            packageName = packageName,
            apkFileName = apkFileName,
            lastOpenedAtMillis = lastOpened,
        )
    }

    private fun unescapeJson(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(value[i + 1])
                }
                i += 2
            } else {
                append(value[i])
                i++
            }
        }
    }
}
