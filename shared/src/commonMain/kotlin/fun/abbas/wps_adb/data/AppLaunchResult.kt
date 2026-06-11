package `fun`.abbas.wps_adb.data

object AppLaunchResult {
    fun isSuccessful(exitCode: Int, output: String): Boolean {
        val lower = output.lowercase()
        if ("no activities found" in lower || "monkey aborted" in lower) return false
        if (
            "unable to resolve" in lower ||
            "does not exist" in lower ||
            "securityexception" in lower
        ) {
            return false
        }
        if (exitCode == 0) return true
        return "events injected" in lower || "starting: intent" in lower
    }

    fun normalizeComponent(packageName: String, launchActivity: String?): String? {
        val activity = launchActivity?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if ('/' in activity) return activity
        return "$packageName/$activity"
    }

    fun resolveActivityComponent(output: String): String? {
        val nameMatches = Regex("""name=([^\s]+/[^\s]+)""")
            .findAll(output)
            .map { it.groupValues[1] }
            .toList()
        if (nameMatches.isNotEmpty()) return nameMatches.last()

        return output.lineSequence()
            .map { it.trim() }
            .lastOrNull { line -> line.contains('/') && !line.contains(' ') }
    }
}
