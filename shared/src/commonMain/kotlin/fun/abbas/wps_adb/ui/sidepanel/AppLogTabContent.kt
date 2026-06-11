package `fun`.abbas.wps_adb.ui.sidepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.abbas.wps_adb.data.AppLogFilter
import `fun`.abbas.wps_adb.model.AppLogMonitorState
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.SidePanelTab
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.ui.logs.SelectableLogList
import `fun`.abbas.wps_adb.ui.logs.logLineColor
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.applog_action_autoscroll_start
import wpsadbtool.shared.generated.resources.applog_action_autoscroll_stop
import wpsadbtool.shared.generated.resources.applog_action_clear
import wpsadbtool.shared.generated.resources.applog_action_launch
import wpsadbtool.shared.generated.resources.applog_action_uninstall
import wpsadbtool.shared.generated.resources.applog_action_monitor_start
import wpsadbtool.shared.generated.resources.applog_action_monitor_stop
import wpsadbtool.shared.generated.resources.applog_filter_placeholder
import wpsadbtool.shared.generated.resources.applog_logs_empty
import wpsadbtool.shared.generated.resources.applog_logs_no_match
import wpsadbtool.shared.generated.resources.applog_package_parsing
import wpsadbtool.shared.generated.resources.applog_package_unknown
import wpsadbtool.shared.generated.resources.mirror_device_subtitle

@Composable
fun AppLogTabContent(
    tab: SidePanelTab.AppLog,
    onLaunchApp: () -> Unit,
    onUninstallApp: () -> Unit,
    onToggleMonitor: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onLogFilterQueryChange: (String) -> Unit,
    onToggleLogFilterLevel: (LogLevel) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canLaunch = !tab.packageName.isNullOrBlank()
    val isMonitoring = tab.monitorState == AppLogMonitorState.MONITORING
    val isAutoScrollEnabled = tab.autoScrollEnabled
    val listState = rememberLazyListState()
    val filteredLogs = remember(tab.logs, tab.logFilterQuery, tab.logFilterLevels) {
        AppLogFilter.apply(tab.logs, tab.logFilterQuery, tab.logFilterLevels)
    }
    LaunchedEffect(isAutoScrollEnabled, filteredLogs.size, filteredLogs.lastOrNull()?.id) {
        if (isAutoScrollEnabled && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tab.device.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CarbonColors.OnSurface)
            Text(
                stringResource(Res.string.mirror_device_subtitle, tab.device.name, tab.device.serial),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = CarbonColors.Outline,
            )
            Text(tab.apkFileName, fontSize = 11.sp, color = CarbonColors.OnSurfaceVariant)
            Text(
                text = when {
                    tab.packageName != null -> tab.packageName
                    else -> stringResource(Res.string.applog_package_parsing)
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (tab.packageName != null) CarbonColors.Primary else CarbonColors.Outline,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(
                label = stringResource(Res.string.applog_action_launch),
                enabled = canLaunch,
                selected = false,
                onClick = onLaunchApp,
            )
            ActionChip(
                label = stringResource(Res.string.applog_action_uninstall),
                enabled = canLaunch,
                selected = false,
                onClick = onUninstallApp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(
                label = if (isMonitoring) {
                    stringResource(Res.string.applog_action_monitor_stop)
                } else {
                    stringResource(Res.string.applog_action_monitor_start)
                },
                enabled = canLaunch,
                selected = isMonitoring,
                onClick = onToggleMonitor,
            )
            ActionChip(
                label = if (isAutoScrollEnabled) {
                    stringResource(Res.string.applog_action_autoscroll_stop)
                } else {
                    stringResource(Res.string.applog_action_autoscroll_start)
                },
                enabled = true,
                selected = isAutoScrollEnabled,
                onClick = onToggleAutoScroll,
            )
            ActionChip(
                label = stringResource(Res.string.applog_action_clear),
                enabled = tab.logs.isNotEmpty(),
                selected = false,
                onClick = onClearLogs,
            )
        }

        OutlinedTextField(
            value = tab.logFilterQuery,
            onValueChange = onLogFilterQueryChange,
            placeholder = {
                Text(
                    stringResource(Res.string.applog_filter_placeholder),
                    fontSize = 11.sp,
                    color = CarbonColors.Outline,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = CarbonColors.OnSurface,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CarbonColors.Primary,
                unfocusedBorderColor = CarbonColors.OutlineVariant,
                focusedTextColor = CarbonColors.OnSurface,
                unfocusedTextColor = CarbonColors.OnSurface,
                cursorColor = CarbonColors.Primary,
                focusedContainerColor = CarbonColors.SurfaceContainerLow,
                unfocusedContainerColor = CarbonColors.SurfaceContainerLow,
            ),
            shape = RoundedCornerShape(8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LogLevel.entries.forEach { level ->
                LevelFilterChip(
                    level = level,
                    selected = level in tab.logFilterLevels,
                    onClick = { onToggleLogFilterLevel(level) },
                )
            }
        }

        if (!canLaunch) {
            Text(
                stringResource(Res.string.applog_package_unknown),
                fontSize = 10.sp,
                color = CarbonColors.Outline,
            )
        }

        SelectableLogList(
            logs = filteredLogs,
            listState = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(CarbonColors.SurfaceContainerLowest, RoundedCornerShape(8.dp))
                .border(1.dp, CarbonColors.OutlineVariant, RoundedCornerShape(8.dp))
                .padding(8.dp),
            emptyContent = {
                Text(
                    text = if (tab.logs.isEmpty()) {
                        stringResource(Res.string.applog_logs_empty)
                    } else {
                        stringResource(Res.string.applog_logs_no_match)
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CarbonColors.Outline,
                )
            },
        )
    }
}

@Composable
private fun LevelFilterChip(
    level: LogLevel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val levelColor = logLineColor(level)
    val borderColor = when {
        selected -> levelColor
        else -> CarbonColors.OutlineVariant
    }
    val textColor = when {
        selected -> levelColor
        else -> CarbonColors.Outline
    }
    Text(
        level.name,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = textColor,
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(
                if (selected) levelColor.copy(alpha = 0.12f) else CarbonColors.SurfaceContainer,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        !enabled -> CarbonColors.OutlineVariant.copy(alpha = 0.4f)
        selected -> CarbonColors.Primary
        else -> CarbonColors.OutlineVariant
    }
    val textColor = when {
        !enabled -> CarbonColors.Outline
        selected -> CarbonColors.Primary
        else -> CarbonColors.OnSurfaceVariant
    }
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(
                if (selected) CarbonColors.Primary.copy(alpha = 0.1f) else CarbonColors.SurfaceContainer,
                RoundedCornerShape(8.dp),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
