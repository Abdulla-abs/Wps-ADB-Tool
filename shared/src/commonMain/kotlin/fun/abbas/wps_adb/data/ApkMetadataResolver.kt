package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ApkMetadata

object ApkMetadataResolver {
    fun isMockPackage(packageName: String): Boolean = packageName.startsWith("com.mock.")

    fun fromFileName(fileName: String): ApkMetadata {
        ApkFileNameParser.metadataFromFileName(fileName)?.let { return it }
        val base = fileName
            .removeSuffix(".apk")
            .removeSuffix(".APK")
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .lowercase()
            .trim('_')
        val packageName = if (base.isEmpty()) "com.mock.app" else "com.mock.$base"
        val label = fileName.removeSuffix(".apk").removeSuffix(".APK")
        return ApkMetadata(packageName = packageName, appLabel = label)
    }
}
