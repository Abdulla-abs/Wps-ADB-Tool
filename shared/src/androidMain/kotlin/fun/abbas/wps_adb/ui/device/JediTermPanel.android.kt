package `fun`.abbas.wps_adb.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.shell_terminal_unavailable

@Composable
actual fun JediTermPanel(
    terminalComponent: Any?,
    modifier: Modifier,
    onMounted: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(CarbonColors.SurfaceContainerLowest)
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.shell_terminal_unavailable),
            fontSize = 12.sp,
            color = CarbonColors.Outline,
        )
    }
}
