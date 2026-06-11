package `fun`.abbas.wps_adb.platform

import `fun`.abbas.wps_adb.model.ApkMetadata

expect class ApkMetadataParser() {
    suspend fun parse(apkPath: String, adbPath: String): ApkMetadata?
}
