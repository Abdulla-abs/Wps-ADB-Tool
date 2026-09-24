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

    @Test
    fun resolveDialogOwner_resolvesFrameAndDialog() {
        val frame = java.awt.Frame("Test Frame")
        try {
            val resolvedFrame = SceneUiUtils.resolveDialogOwner(frame)
            assertEquals(frame, resolvedFrame)

            val dialog = java.awt.Dialog(frame, "Test Dialog")
            try {
                val resolvedDialog = SceneUiUtils.resolveDialogOwner(dialog)
                assertEquals(dialog, resolvedDialog)

                val childWindow = java.awt.Window(dialog)
                try {
                    val resolvedChild = SceneUiUtils.resolveDialogOwner(childWindow)
                    assertEquals(dialog, resolvedChild)
                } finally {
                    childWindow.dispose()
                }
            } finally {
                dialog.dispose()
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun resolveDialogOwner_handlesNullSafely() {
        // When null is passed and no window is active, fallback should be null or active window without crashing
        val resolved = SceneUiUtils.resolveDialogOwner(null)
        // resolved may be null or currently active frame, but should not throw
        assertTrue(resolved == null || resolved is java.awt.Frame || resolved is java.awt.Dialog)
    }

    @Test
    fun createGlbFileDialog_configuresFilterAndMode() {
        val frame = java.awt.Frame("Test Frame")
        try {
            val dialog = SceneUiUtils.createGlbFileDialog("Test GLB Dialog", owner = frame)
            assertEquals(java.awt.FileDialog.LOAD, dialog.mode)
            assertEquals("Test GLB Dialog", dialog.title)
            assertEquals(frame, dialog.owner)

            val filter = dialog.filenameFilter
            assertNotNull(filter)
            assertTrue(filter.accept(File("."), "scene.glb"))
            assertTrue(filter.accept(File("."), "model.GLB"))
            assertTrue(!filter.accept(File("."), "texture.png"))
            assertTrue(!filter.accept(File("."), "scene.gltf"))
        } finally {
            dialogDisposeSafely(frame)
        }
    }

    @Test
    fun showGlbFileDialog_returnsNullOnCancel() {
        val frame = java.awt.Frame("Test Frame")
        try {
            val result = SceneUiUtils.showGlbFileDialog(
                title = "Select Model",
                owner = frame,
                dialogRunner = { dialog ->
                    // Simulate user cancellation (no file/dir set)
                }
            )
            assertNull(result, "Canceling dialog should return null without throwing")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun showGlbFileDialog_returnsFileWhenSelected() {
        val frame = java.awt.Frame("Test Frame")
        val tempDir = Files.createTempDirectory("glb_test_dir").toFile()
        try {
            val expectedFile = File(tempDir, "sample.glb")
            expectedFile.writeText("sample")

            val result = SceneUiUtils.showGlbFileDialog(
                title = "Select Model",
                owner = frame,
                dialogRunner = { dialog ->
                    // Simulate user choosing a file
                    dialog.directory = tempDir.absolutePath
                    dialog.file = "sample.glb"
                }
            )
            assertNotNull(result)
            assertEquals(expectedFile.canonicalPath, result.canonicalPath)
        } finally {
            tempDir.deleteRecursively()
            frame.dispose()
        }
    }

    private fun dialogDisposeSafely(frame: java.awt.Frame) {
        try {
            frame.dispose()
        } catch (_: Throwable) {}
    }
}
