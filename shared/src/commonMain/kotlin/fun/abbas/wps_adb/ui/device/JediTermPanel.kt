package `fun`.abbas.wps_adb.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun JediTermPanel(
    terminalComponent: Any?,
    modifier: Modifier = Modifier,
    onMounted: () -> Unit = {},
)
