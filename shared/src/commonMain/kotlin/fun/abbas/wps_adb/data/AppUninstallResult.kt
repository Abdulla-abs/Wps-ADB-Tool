package `fun`.abbas.wps_adb.data

object AppUninstallResult {
    fun isSuccessful(exitCode: Int, output: String): Boolean {
        val lower = output.lowercase()
        if (
            "delete_failed" in lower ||
            "unknown package" in lower ||
            "not installed" in lower ||
            "does not exist" in lower ||
            "exception" in lower ||
            "permission denial" in lower ||
            "failure [" in lower
        ) {
            return false
        }
        if (exitCode == 0) return true
        return "success" in lower
    }
}
