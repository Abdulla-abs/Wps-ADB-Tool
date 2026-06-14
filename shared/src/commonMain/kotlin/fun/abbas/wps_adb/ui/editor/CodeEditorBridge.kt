package `fun`.abbas.wps_adb.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CodeEditorBridge(
    content: String,
    onContentChange: (String) -> Unit,
    syntax: String, // "xml", "smali", "java"
    modifier: Modifier = Modifier
)
