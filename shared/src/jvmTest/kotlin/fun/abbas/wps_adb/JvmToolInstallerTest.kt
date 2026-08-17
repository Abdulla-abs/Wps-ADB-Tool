package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmToolInstaller
import `fun`.abbas.wps_adb.model.ToolKind
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmToolInstallerTest {
    @Test
    fun unzip_extractsPlatformToolsAdbLayout() {
        val tempDir = kotlin.io.path.createTempDirectory("wps-tool-install").toFile()
        try {
            val installer = JvmToolInstaller()
            val zip = File(tempDir, "platform-tools-latest-windows.zip")
            ZipOutputStream(zip.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("platform-tools/"))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("platform-tools/adb.exe"))
                zos.write("fake-adb".toByteArray())
                zos.closeEntry()
            }

            val dest = File(tempDir, "out").apply { mkdirs() }
            val unzip = JvmToolInstaller::class.java.getDeclaredMethod("unzip", File::class.java, File::class.java)
            unzip.isAccessible = true
            unzip.invoke(installer, zip, dest)

            val adb = File(dest, "platform-tools/adb.exe")
            assertTrue(adb.isFile)
            assertEquals("fake-adb", adb.readText())

            val find = JvmToolInstaller::class.java.getDeclaredMethod(
                "findExecutable",
                ToolKind::class.java,
                File::class.java,
            )
            find.isAccessible = true
            val found = find.invoke(installer, ToolKind.ADB, dest) as File
            assertEquals(adb.canonicalFile, found.canonicalFile)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
