package `fun`.abbas.wps_adb.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.viewmodel.LogTrayMode
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.logs_clear
import wpsadbtool.shared.generated.resources.logs_clear_logcat
import wpsadbtool.shared.generated.resources.logs_close
import wpsadbtool.shared.generated.resources.logs_logcat_filter_all
import wpsadbtool.shared.generated.resources.logs_tab_events
import wpsadbtool.shared.generated.resources.logs_tab_logcat

@Composable
fun TerminalLogsPanel(
    mode: LogTrayMode,
    onModeChange: (LogTrayMode) -> Unit,
    eventLogs: List<AdbLog>,
    logcatLogs: List<AdbLog>,
    logcatFilterLabel: String?,
    onShowAllDevices: () -> Unit,
    onClearEvents: () -> Unit,
    onClearLogcat: () -> Unit,
    onClose: () -> Unit,
    logRetention: Int = 2500,
    modifier: Modifier = Modifier,
) {
    val displayLogs = when (mode) {
        LogTrayMode.EVENTS -> eventLogs
        LogTrayMode.LOGCAT -> logcatLogs
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(CarbonColors.SurfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CarbonColors.SurfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.logs_tab_events),
                    fontSize = 12.sp,
                    color = if (mode == LogTrayMode.EVENTS) CarbonColors.Primary else CarbonColors.Outline,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onModeChange(LogTrayMode.EVENTS) },
                )
                Text(
                    text = stringResource(Res.string.logs_tab_logcat),
                    fontSize = 12.sp,
                    color = if (mode == LogTrayMode.LOGCAT) CarbonColors.Primary else CarbonColors.Outline,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onModeChange(LogTrayMode.LOGCAT) },
                )
                if (mode == LogTrayMode.LOGCAT && logcatFilterLabel != null) {
                    Text(
                        text = logcatFilterLabel,
                        fontSize = 11.sp,
                        color = CarbonColors.OnSurface,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = stringResource(Res.string.logs_logcat_filter_all),
                        fontSize = 11.sp,
                        color = CarbonColors.Primary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable(onClick = onShowAllDevices),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val clearLabel = if (mode == LogTrayMode.EVENTS) {
                    stringResource(Res.string.logs_clear)
                } else {
                    stringResource(Res.string.logs_clear_logcat)
                }
                val onClear = if (mode == LogTrayMode.EVENTS) onClearEvents else onClearLogcat
                Text(
                    text = clearLabel,
                    fontSize = 11.sp,
                    color = CarbonColors.Outline,
                    modifier = Modifier.clickable(onClick = onClear),
                )
                Text(
                    text = stringResource(Res.string.logs_close),
                    fontSize = 11.sp,
                    color = CarbonColors.Outline,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }
        }
        SelectableLogList(
            logs = displayLogs.takeLast(logRetention.coerceAtLeast(1)),
            modifier = Modifier.weight(1f).padding(8.dp),
        )
    }
}
