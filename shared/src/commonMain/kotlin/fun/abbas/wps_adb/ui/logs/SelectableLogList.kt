package `fun`.abbas.wps_adb.ui.logs

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.theme.CarbonColors

fun formatAdbLogLine(log: AdbLog): String =
    "${log.timestamp} ${log.level.name}/${log.tag}: ${log.message}"

@Composable
fun SelectableLogList(
    logs: List<AdbLog>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    emptyContent: (@Composable () -> Unit)? = null,
) {
    SelectionContainer(modifier = modifier) {
        LazyColumn(state = listState) {
            if (logs.isEmpty()) {
                if (emptyContent != null) {
                    item { emptyContent() }
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    Text(
                        text = formatAdbLogLine(log),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = logLineColor(log.level),
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

fun logLineColor(level: LogLevel) = when (level) {
    LogLevel.V -> CarbonColors.Outline
    LogLevel.D -> CarbonColors.Secondary
    LogLevel.I -> CarbonColors.Primary
    LogLevel.W -> CarbonColors.SecondaryContainer
    LogLevel.E -> CarbonColors.Error
}
