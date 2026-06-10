package `fun`.abbas.wps_adb.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.theme.CarbonColors
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.*
import wpsadbtool.shared.generated.resources.Res

@Composable
fun TerminalLogsPanel(
    logs: List<AdbLog>,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Text(stringResource(Res.string.logs_title), fontSize = 12.sp, color = CarbonColors.OnSurface, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.logs_clear), fontSize = 11.sp, color = CarbonColors.Outline, modifier = Modifier.clickable(onClick = onClear))
                Text(stringResource(Res.string.logs_close), fontSize = 11.sp, color = CarbonColors.Outline, modifier = Modifier.clickable(onClick = onClose))
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
            items(logs.takeLast(200)) { log ->
                Text(
                    "${log.timestamp} ${log.level.name}/${log.tag}: ${log.message}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = logColor(log.level),
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

private fun logColor(level: LogLevel) = when (level) {
    LogLevel.V -> CarbonColors.Outline
    LogLevel.D -> CarbonColors.Secondary
    LogLevel.I -> CarbonColors.Primary
    LogLevel.W -> CarbonColors.SecondaryContainer
    LogLevel.E -> CarbonColors.Error
}
