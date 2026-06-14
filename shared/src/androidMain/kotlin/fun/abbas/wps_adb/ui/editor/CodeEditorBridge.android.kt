package `fun`.abbas.wps_adb.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp

@Composable
actual fun CodeEditorBridge(
    content: String,
    onContentChange: (String) -> Unit,
    syntax: String,
    modifier: Modifier
) {
    BasicTextField(
        value = content,
        onValueChange = onContentChange,
        textStyle = TextStyle(
            color = Color(226, 226, 230),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Default
        ),
        modifier = modifier
            .fillMaxSize()
            .background(Color(12, 14, 17))
    )
}
