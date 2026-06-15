package `fun`.abbas.wps_adb.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

actual suspend fun pickDirectory(initialPath: String?): String? = withContext(Dispatchers.Swing) {
    val chooser = JFileChooser(initialPath ?: FileSystemView.getFileSystemView().homeDirectory.path).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select data cache directory"
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
    chooser.selectedFile?.absolutePath
}
