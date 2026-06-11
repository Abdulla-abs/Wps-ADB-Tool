package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ApkMetadata

object ApkFileNameParser {
    fun extractPackageFromFileName(fileName: String): String? {
        val base = fileName
            .substringBeforeLast('.')
            .trim()
        if (base.isEmpty()) return null

        val candidates = listOf(
            base.substringBefore('_'),
            base.substringBefore(" ("),
            base.substringBefore(' '),
            base,
        ).map { it.lowercase().trim() }.distinct()

        return candidates.firstNotNullOfOrNull { candidate ->
            candidate.takeIf { ApkPackageNames.isLikelyAppPackage(it) }
        } ?: Regex("""^(com\.[a-z0-9_]+(?:\.[a-z0-9_]+)+)""")
            .find(base.lowercase())
            ?.groupValues
            ?.getOrNull(1)
            ?.substringBefore('_')
            ?.takeIf { ApkPackageNames.isLikelyAppPackage(it) }
    }

    fun metadataFromFileName(fileName: String): ApkMetadata? {
        val packageName = extractPackageFromFileName(fileName) ?: return null
        val label = fileName.removeSuffix(".apk").removeSuffix(".APK")
        return ApkMetadata(packageName = packageName, appLabel = label)
    }
}
