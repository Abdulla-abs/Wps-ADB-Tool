package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ApkMetadata

object ApkBadgingParser {
    private val packageNameRegex = Regex("""package:\s+name='([^']+)'""")
    private val versionNameRegex = Regex("""versionName='([^']+)'""")
    private val applicationLabelRegex = Regex("""application-label(?:-\w+)?:'([^']+)'""")
    private val launchableActivityRegex = Regex("""launchable-activity:\s+name='([^']+)'""")

    fun parseAaptOutput(output: String): ApkMetadata? {
        val packageName = packageNameRegex.find(output)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (packageName.isEmpty()) return null
        val versionName = versionNameRegex.find(output)?.groupValues?.getOrNull(1)?.trim()
        val appLabel = applicationLabelRegex.find(output)?.groupValues?.getOrNull(1)?.trim()
        val launchActivity = launchableActivityRegex.find(output)?.groupValues?.getOrNull(1)?.trim()
        return ApkMetadata(
            packageName = packageName,
            appLabel = appLabel?.takeIf { it.isNotEmpty() },
            versionName = versionName?.takeIf { it.isNotEmpty() },
            launchActivity = AppLaunchResult.normalizeComponent(
                packageName,
                launchActivity?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    fun parseApkanalyzerOutput(output: String): ApkMetadata? {
        val packageName = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("error", ignoreCase = true) }
            ?: return null
        if (packageName.contains(' ')) return null
        return parsePackageNameLine(packageName)
    }

    fun parsePackageNameLine(output: String): ApkMetadata? {
        val packageName = output.trim()
        if (!ApkPackageNames.isLikelyAppPackage(packageName)) return null
        return ApkMetadata(packageName = packageName)
    }
}
