package `fun`.abbas.wps_adb.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.data.JediTermSizeSync
import `fun`.abbas.wps_adb.data.TerminalHostPanel
import `fun`.abbas.wps_adb.theme.CarbonColors
import javax.swing.JComponent
import javax.swing.SwingUtilities

@Composable
actual fun JediTermPanel(
    terminalComponent: Any?,
    modifier: Modifier,
    onMounted: () -> Unit,
) {
    if (terminalComponent is JComponent) {
        LaunchedEffect(terminalComponent) {
            onMounted()
        }
        SwingPanel(
            factory = { terminalComponent },
            update = { component ->
                onMounted()
                SwingUtilities.invokeLater {
                    when (component) {
                        is TerminalHostPanel -> {
                            JediTermSizeSync.scheduleSync(component.widget)
                            component.requestTerminalFocus()
                        }
                        else -> component.requestFocusInWindow()
                    }
                }
            },
            modifier = modifier.background(CarbonColors.SurfaceContainerLowest),
        )
    } else {
        Box(
            modifier = modifier
                .background(CarbonColors.SurfaceContainerLowest)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Terminal unavailable", fontSize = 12.sp, color = CarbonColors.Outline)
        }
    }
}
