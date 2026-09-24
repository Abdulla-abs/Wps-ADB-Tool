package `fun`.abbas.wps_adb.scene.ui

import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File
import java.io.FilenameFilter

object SceneUiUtils {
    /**
     * Resolves a suitable parent [Frame] or [Dialog] for an AWT [FileDialog].
     * If the given [owner] is already a Frame or Dialog, it is returned directly.
     * If [owner] is another Window subclass, its owner hierarchy is inspected.
     * If [owner] is null, falls back to KeyboardFocusManager's active or focused window.
     */
    fun resolveDialogOwner(owner: Window?): Window? {
        var current: Window? = owner
        while (current != null && current !is Frame && current !is Dialog) {
            current = current.owner
        }
        if (current is Frame || current is Dialog) {
            return current
        }

        val fallback = try {
            val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            kfm.activeWindow ?: kfm.focusedWindow
        } catch (_: Throwable) {
            null
        }

        var fallbackCurrent: Window? = fallback
        while (fallbackCurrent != null && fallbackCurrent !is Frame && fallbackCurrent !is Dialog) {
            fallbackCurrent = fallbackCurrent.owner
        }
        return if (fallbackCurrent is Frame || fallbackCurrent is Dialog) fallbackCurrent else null
    }

    /**
     * Creates an AWT FileDialog configured for .glb files with the resolved owner.
     */
    fun createGlbFileDialog(title: String, owner: Window? = null): FileDialog {
        val resolvedOwner = resolveDialogOwner(owner)
        val dialog = when (resolvedOwner) {
            is Frame -> FileDialog(resolvedOwner, title, FileDialog.LOAD)
            is Dialog -> FileDialog(resolvedOwner, title, FileDialog.LOAD)
            else -> FileDialog(null as Frame?, title, FileDialog.LOAD)
        }
        dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(".glb", ignoreCase = true) }
        return dialog
    }

    /**
     * Safely opens an AWT FileDialog configured for .glb files.
     * Uses [owner] as parent if valid, or falls back gracefully.
     * Returns null if user cancels the dialog (so existing input remains preserved).
     */
    fun showGlbFileDialog(
        title: String,
        owner: Window? = null,
        dialogRunner: (FileDialog) -> Unit = { it.isVisible = true },
    ): File? {
        val dialog = createGlbFileDialog(title, owner)
        dialogRunner(dialog)

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
