package `fun`.abbas.wps_adb.model

import kotlin.math.roundToInt

data class DeviceStorageSnapshot(
    val used: String,
    val total: String,
    val percent: Int,
)

object DeviceStorageMetrics {
    fun parseDfOutput(output: String): DeviceStorageSnapshot? =
        parseDfLines(output).firstOrNull()?.toSnapshot()

    fun parseDfOutputPreferringDataMount(output: String): DeviceStorageSnapshot? {
        val lines = parseDfLines(output)
        if (lines.isEmpty()) return null
        val mountPriority = listOf(
            { line: ParsedDfLine -> line.mountPoint == "/data" },
            { line: ParsedDfLine -> line.mountPoint == "/storage/emulated" },
            { line: ParsedDfLine -> line.mountPoint.startsWith("/data/") },
            { line: ParsedDfLine -> line.mountPoint.contains("/emulated") },
        )
        for (predicate in mountPriority) {
            lines.firstOrNull(predicate)?.let { return it.toSnapshot() }
        }
        return lines.first().toSnapshot()
    }

    private fun parseDfLines(output: String): List<ParsedDfLine> =
        output.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("Filesystem", ignoreCase = true) &&
                    line.contains('%')
            }
            .mapNotNull(::parseDfLine)
            .toList()

    private fun parseDfLine(line: String): ParsedDfLine? {
        val percentMatch = Regex("""(\d+)%""").find(line) ?: return null
        val percent = percentMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return null
        val beforePercent = line.substring(0, percentMatch.range.first).trim()
        val mountPoint = line.substring(percentMatch.range.last + 1).trim().ifBlank { "/" }
        val tokens = beforePercent.split(Regex("""\s+"""))
        if (tokens.size < 3) return null
        val totalKb = parseSizeToKb(tokens[1]) ?: return null
        val usedKb = parseSizeToKb(tokens[2]) ?: return null
        return ParsedDfLine(totalKb = totalKb, usedKb = usedKb, percent = percent, mountPoint = mountPoint)
    }

    private fun parseSizeToKb(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val unitMatch = Regex("""^([\d.]+)([KMGTP]?)$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
            ?: return null
        val value = unitMatch.groupValues[1].toDoubleOrNull() ?: return null
        val unit = unitMatch.groupValues[2].uppercase()
        val multiplier = when (unit) {
            "", "K" -> 1L
            "M" -> 1024L
            "G" -> 1024L * 1024L
            "T" -> 1024L * 1024L * 1024L
            "P" -> 1024L * 1024L * 1024L * 1024L
            else -> return null
        }
        return (value * multiplier).roundToInt().toLong()
    }

    private fun ParsedDfLine.toSnapshot(): DeviceStorageSnapshot =
        DeviceStorageSnapshot(
            used = formatStorageKb(usedKb),
            total = formatStorageKb(totalKb),
            percent = percent,
        )

    private fun formatStorageKb(kb: Long): String {
        if (kb <= 0L) return "--"
        val mb = kb / 1024.0
        if (mb < 1024.0) {
            return "${mb.roundToInt()}MB"
        }
        val gb = mb / 1024.0
        val rounded = (gb * 10.0).roundToInt() / 10.0
        return if (rounded == rounded.roundToInt().toDouble()) {
            "${rounded.roundToInt()}GB"
        } else {
            "${rounded}GB"
        }
    }
}

private data class ParsedDfLine(
    val totalKb: Long,
    val usedKb: Long,
    val percent: Int,
    val mountPoint: String,
)
