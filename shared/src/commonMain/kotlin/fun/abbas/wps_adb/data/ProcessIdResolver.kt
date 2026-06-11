package `fun`.abbas.wps_adb.data

object ProcessIdResolver {
    fun parsePidList(output: String): List<Int> =
        output.trim()
            .split(Regex("\\s+"))
            .mapNotNull { token -> token.toIntOrNull() }

    fun parseSinglePid(output: String): Int? {
        val pids = parsePidList(output)
        if (pids.size != 1) return null
        val pid = pids.single()
        return pid.takeIf { it > 1 }
    }

    fun parseFromPs(output: String, packageName: String): Int? =
        output.lineSequence()
            .map { it.trim() }
            .filter { line -> line.endsWith(packageName) }
            .mapNotNull { line ->
                line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
            }
            .firstOrNull { pid -> pid > 1 }
}
