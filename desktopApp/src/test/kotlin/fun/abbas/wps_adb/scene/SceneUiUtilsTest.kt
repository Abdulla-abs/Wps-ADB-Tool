package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.scene.ui.SceneUiUtils
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneUiUtilsTest {

    @Test
    fun validateGlbFile_successOnValidGlb() {
        val tempFile = Files.createTempFile("test_model", ".glb").toFile()
        try {
            tempFile.writeBytes(ByteArray(1024)) // 1 KB
            val error = SceneUiUtils.validateGlbFile(tempFile, maxSizeBytes = 10 * 1024 * 1024)
            assertNull(error, "Valid .glb file should return null error")
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun validateGlbFile_failsOnNonExistentFile() {
        val nonExistent = File("non_existent_file_${System.currentTimeMillis()}.glb")
        val error = SceneUiUtils.validateGlbFile(nonExistent)
        assertNotNull(error)
        assertTrue(error.contains("does not exist", ignoreCase = true))
    }

    @Test
    fun validateGlbFile_failsOnDirectory() {
        val tempDir = Files.createTempDirectory("test_glb_dir").toFile()
        try {
            val error = SceneUiUtils.validateGlbFile(tempDir)
            assertNotNull(error)
            assertTrue(error.contains("directory", ignoreCase = true))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validateGlbFile_failsOnNonGlbExtension() {
        val tempFile = Files.createTempFile("test_model", ".obj").toFile()
        try {
            tempFile.writeText("v 0 0 0")
            val error = SceneUiUtils.validateGlbFile(tempFile)
            assertNotNull(error)
            assertTrue(error.contains("Only .glb", ignoreCase = true))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun validateGlbFile_failsOnSizeExceeded() {
        val tempFile = Files.createTempFile("large_model", ".glb").toFile()
        try {
            tempFile.writeBytes(ByteArray(2048)) // 2 KB
            val error = SceneUiUtils.validateGlbFile(tempFile, maxSizeBytes = 1024) // 1 KB limit
            assertNotNull(error)
            assertTrue(error.contains("exceeds", ignoreCase = true))
        } finally {
            tempFile.delete()
        }
    }
}
