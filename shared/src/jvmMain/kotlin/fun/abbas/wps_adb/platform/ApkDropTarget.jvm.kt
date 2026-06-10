package `fun`.abbas.wps_adb.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draganddrop.dragData
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import java.nio.file.Paths

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.apkDropTarget(
    enabled: Boolean,
    onDragEnter: () -> Unit,
    onDragExit: () -> Unit,
    onApkDropped: (filePath: String) -> Unit,
): Modifier {
    if (!enabled) return this

    val onDragEnterState = rememberUpdatedState(onDragEnter)
    val onDragExitState = rememberUpdatedState(onDragExit)
    val onApkDroppedState = rememberUpdatedState(onApkDropped)

    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                onDragEnterState.value()
            }

            override fun onExited(event: DragAndDropEvent) {
                onDragExitState.value()
            }

            override fun onEnded(event: DragAndDropEvent) {
                onDragExitState.value()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onDragExitState.value()
                val path = event.firstApkPath() ?: return false
                onApkDroppedState.value(path)
                return true
            }
        }
    }

    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.hasDroppableFileList() },
        target = target,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.hasDroppableFileList(): Boolean {
    val transferable = runCatching { awtTransferable }.getOrNull() ?: return false
    return transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.firstApkPath(): String? {
    val fromDragData = (dragData() as? DragData.FilesList)
        ?.readFiles()
        ?.mapNotNull(::toExistingFile)
    fromDragData?.let { files ->
        ApkFileDetector.firstApkFile(files)?.absolutePath?.let { return it }
    }

    val transferable = runCatching { awtTransferable }.getOrNull() ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null
    @Suppress("UNCHECKED_CAST")
    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File> ?: return null
    return ApkFileDetector.firstApkFile(files)?.absolutePath
}

private fun toExistingFile(uriString: String): File? = runCatching {
    val path = when {
        uriString.startsWith("file:", ignoreCase = true) -> Paths.get(URI(uriString))
        else -> Paths.get(uriString)
    }
    path.toFile().takeIf { it.isFile }
}.getOrNull()
