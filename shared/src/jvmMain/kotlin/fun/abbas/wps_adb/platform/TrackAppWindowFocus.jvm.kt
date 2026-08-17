package `fun`.abbas.wps_adb.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
actual fun TrackAppWindowFocus() {
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .collect { focused -> AppWindowFocus.setFocused(focused) }
    }
}
