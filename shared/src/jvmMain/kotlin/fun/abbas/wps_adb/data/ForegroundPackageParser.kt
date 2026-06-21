package `fun`.abbas.wps_adb.data

internal object ForegroundPackageParser {
    private val TOP_RESUMED_ACTIVITY = Regex("""topResumedActivity=ActivityRecord\{.*? u\d+ (\S+)/""")
    private val CURRENT_FOCUS = Regex("""mCurrentFocus=Window\{.*? u\d+ (\S+)/""")
    private val RESUMED_ACTIVITY = Regex("""ResumedActivity: ActivityRecord\{.*? u\d+ (\S+)/""")
    private val ACTIVITY_TOP = Regex("""ACTIVITY (\S+)/""")

    fun parse(activityOutput: String, windowOutput: String, topOutput: String): String? {
        packageFrom(activityOutput, TOP_RESUMED_ACTIVITY)?.let { return it }
        packageFrom(windowOutput, CURRENT_FOCUS)?.let { pkg ->
            if (!isSystemPackage(pkg)) return pkg
        }
        packageFrom(activityOutput, RESUMED_ACTIVITY)?.let { return it }
        packageFrom(topOutput, ACTIVITY_TOP)?.let { return it }
        return packageFrom(windowOutput, CURRENT_FOCUS)
    }

    private fun packageFrom(output: String, pattern: Regex): String? =
        pattern.find(output)?.groupValues?.getOrNull(1)?.takeIf { it.contains('.') }

    private fun isSystemPackage(packageName: String): Boolean =
        packageName == "com.android.systemui" || packageName == "android"
}
