package `fun`.abbas.wps_adb.data

import java.io.File
import java.util.zip.ZipFile

internal object ApkMetaInf {
    fun isSignatureEntry(entryName: String): Boolean {
        if (!entryName.startsWith("META-INF/", ignoreCase = true)) return false
        val fileName = entryName.substringAfterLast('/')
        if (fileName.equals("MANIFEST.MF", ignoreCase = true)) return true
        return fileName.endsWith(".RSA", ignoreCase = true) ||
            fileName.endsWith(".DSA", ignoreCase = true) ||
            fileName.endsWith(".EC", ignoreCase = true) ||
            fileName.endsWith(".SF", ignoreCase = true)
    }

    fun stripSignatureEntries(apk: File) {
        val temp = File(apk.parentFile, "${apk.nameWithoutExtension}.stripped.apk")
        val entries = ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && !isSignatureEntry(it.name) }
                .map { entry ->
                    val data = zip.getInputStream(entry).use { it.readBytes() }
                    ApkEntry(entry.name, data)
                }
                .toList()
        }
        ApkPacker.repack(entries, temp)
        temp.copyTo(apk, overwrite = true)
        temp.delete()
    }
}
