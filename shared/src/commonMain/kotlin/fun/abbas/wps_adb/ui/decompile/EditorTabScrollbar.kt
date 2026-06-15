package `fun`.abbas.wps_adb.ui.decompile

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun EditorTabScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
)
