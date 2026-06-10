package `fun`.abbas.wps_adb.platform

import java.io.File
import java.util.zip.ZipFile

object ApkFileDetector {
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun isApkFile(file: File): Boolean {
        if (!file.isFile) return false
        if (file.extension.equals("apk", ignoreCase = true)) return true
        if (!hasZipMagic(file)) return false
        return containsAndroidManifest(file)
    }

    fun firstApkFile(files: Iterable<File>): File? =
        files.firstOrNull { isApkFile(it) }

    private fun hasZipMagic(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 && header.contentEquals(ZIP_MAGIC)
        }
    }.getOrDefault(false)

    private fun containsAndroidManifest(file: File): Boolean = runCatching {
        ZipFile(file).use { zip -> zip.getEntry("AndroidManifest.xml") != null }
    }.getOrDefault(false)
}
