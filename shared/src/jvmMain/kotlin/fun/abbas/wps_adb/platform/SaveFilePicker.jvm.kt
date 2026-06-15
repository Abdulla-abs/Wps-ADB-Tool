package `fun`.abbas.wps_adb.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual suspend fun pickSaveFile(suggestedName: String): String? = withContext(Dispatchers.Swing) {
    val dialog = FileDialog(null as Frame?, "Save File", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val directory = dialog.directory
    val fileName = dialog.file
    if (directory == null || fileName == null) return@withContext null
    File(directory, fileName).absolutePath
}
