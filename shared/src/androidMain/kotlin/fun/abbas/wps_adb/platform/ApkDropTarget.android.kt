package `fun`.abbas.wps_adb.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.apkDropTarget(
    enabled: Boolean,
    onDragEnter: () -> Unit,
    onDragExit: () -> Unit,
    onApkDropped: (filePath: String) -> Unit,
): Modifier = this
