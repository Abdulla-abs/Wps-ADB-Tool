package `fun`.abbas.wps_adb.ui.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun QrCodeImage(
    payload: String,
    modifier: Modifier = Modifier,
)
