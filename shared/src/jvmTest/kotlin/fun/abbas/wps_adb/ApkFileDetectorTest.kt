package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.platform.ApkFileDetector
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkFileDetectorTest {
    @Test
    fun isApkFile_acceptsApkExtensionWithoutManifestCheck() {
        val file = File.createTempFile("sample", ".apk")
        file.deleteOnExit()
        file.writeBytes(byteArrayOf(0x00))

        assertTrue(ApkFileDetector.isApkFile(file))
    }

    @Test
    fun isApkFile_acceptsNonApkExtensionWhenZipContainsManifest() {
        val file = File.createTempFile("sample", ".1")
        file.deleteOnExit()
        writeMinimalApkZip(file)

        assertTrue(ApkFileDetector.isApkFile(file))
    }

    @Test
    fun isApkFile_rejectsNonApkExtensionWithoutManifest() {
        val file = File.createTempFile("sample", ".1")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("hello".toByteArray())
            zip.closeEntry()
        }

        assertFalse(ApkFileDetector.isApkFile(file))
    }

    private fun writeMinimalApkZip(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("<manifest />".toByteArray())
            zip.closeEntry()
        }
    }
}
