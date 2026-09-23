package `fun`.abbas.wps_adb.scene.ui

import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.FilenameFilter

object SceneUiUtils {
    /**
     * Safely opens an AWT FileDialog configured for .glb files.
     * Handles Frame, Dialog, or falls back to null parent window.
     * Returns null if user cancels the dialog (so existing input remains preserved).
     */
    fun showGlbFileDialog(title: String): File? {
        val activeWindow = try {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        } catch (_: Throwable) {
            null
        }

        val dialog = when (activeWindow) {
            is Frame -> FileDialog(activeWindow, title, FileDialog.LOAD)
            is Dialog -> FileDialog(activeWindow, title, FileDialog.LOAD)
            else -> FileDialog(null as Frame?, title, FileDialog.LOAD)
        }

        dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(".glb", ignoreCase = true) }
        dialog.isVisible = true

        val file = dialog.file
        val dir = dialog.directory
        return if (file != null && dir != null) File(dir, file) else null
    }

    /**
     * Validates that the file exists, is a normal file, has read permissions,
     * has a .glb extension, and does not exceed optional maximum byte size.
     * Returns null on success, or a human-readable error description on failure.
     */
    fun validateGlbFile(file: File, maxSizeBytes: Long? = null): String? {
        if (!file.exists()) {
            return "File does not exist: ${file.name}"
        }
        if (!file.isFile) {
            return "Path is a directory or special file, not a regular file: ${file.name}"
        }
        if (!file.canRead()) {
            return "Cannot read file (permission denied): ${file.name}"
        }
        if (!file.name.endsWith(".glb", ignoreCase = true)) {
            return "Only .glb 3D model format is supported"
        }
        if (maxSizeBytes != null && file.length() > maxSizeBytes) {
            val maxMb = maxSizeBytes / (1024 * 1024)
            val currentMb = file.length() / (1024 * 1024)
            return "File size (${currentMb}MB) exceeds ${maxMb}MB limit"
        }
        return null
    }
}
