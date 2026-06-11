package `fun`.abbas.wps_adb.platform

import `fun`.abbas.wps_adb.data.ApkMetadataResolver
import `fun`.abbas.wps_adb.model.ApkMetadata
import java.io.File

actual class ApkMetadataParser {
    actual suspend fun parse(apkPath: String, adbPath: String): ApkMetadata? {
        val fileName = File(apkPath).name.ifBlank {
            apkPath.substringAfterLast('/').substringAfterLast('\\')
        }
        return ApkMetadataResolver.fromFileName(fileName)
    }
}
