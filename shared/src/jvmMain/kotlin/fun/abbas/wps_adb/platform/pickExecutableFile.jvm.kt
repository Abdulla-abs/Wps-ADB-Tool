package `fun`.abbas.wps_adb.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

actual suspend fun pickExecutableFile(
    initialPath: String?,
    dialogTitle: String,
): String? = withContext(Dispatchers.Swing) {
    val trimmed = initialPath?.trim()?.takeIf { it.isNotEmpty() }
    val initial = trimmed?.let { File(it) }
    val startDir = when {
        initial == null -> FileSystemView.getFileSystemView().homeDirectory
        initial.isDirectory -> initial
        initial.isFile -> initial.parentFile
        else -> initial.parentFile ?: FileSystemView.getFileSystemView().homeDirectory
    }
    val chooser = JFileChooser(startDir).apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        this.dialogTitle = dialogTitle
        selectedFile = initial?.takeIf { it.isFile }
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
    chooser.selectedFile?.takeIf { it.isFile }?.absolutePath
}
