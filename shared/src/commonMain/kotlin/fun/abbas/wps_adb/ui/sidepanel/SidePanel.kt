package `fun`.abbas.wps_adb.ui.sidepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.MirrorSessionState
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.SidePanelState
import `fun`.abbas.wps_adb.model.SidePanelTab
import `fun`.abbas.wps_adb.theme.CarbonColors

@Composable
fun SidePanel(
    state: SidePanelState,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onToggleMonitor: (String) -> Unit,
    onToggleAutoScroll: (String) -> Unit,
    onLogFilterQueryChange: (String, String) -> Unit,
    onToggleLogFilterLevel: (String, LogLevel) -> Unit,
    onClearLogs: (String) -> Unit,
    onStartMirror: (String) -> Unit,
    onStopMirror: (String) -> Unit,
    onConnectionOptionsChange: (String, ScrcpyConnectionOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    Column(
        modifier = modifier
            .width(SIDE_PANEL_WIDTH)
            .fillMaxHeight()
            .background(CarbonColors.SurfaceContainerLow)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant),
    ) {
        SidePanelTabBar(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(CarbonColors.SurfaceContainerLow),
        ) {
            when (val tab = state.activeTab) {
                is SidePanelTab.Mirror -> MirrorTabContent(
                    device = tab.device,
                    sessionState = tab.sessionState,
                    errorMessage = tab.errorMessage,
                    connectionOptions = tab.connectionOptions,
                    settingsEditable = tab.sessionState != MirrorSessionState.RUNNING &&
                        tab.sessionState != MirrorSessionState.STARTING,
                    onStartMirror = { onStartMirror(tab.id) },
                    onStopMirror = { onStopMirror(tab.id) },
                    onConnectionOptionsChange = { options -> onConnectionOptionsChange(tab.id, options) },
                    modifier = Modifier.fillMaxSize(),
                )
                is SidePanelTab.AppLog -> AppLogTabContent(
                    tab = tab,
                    onLaunchApp = { onLaunchApp(tab.id) },
                    onUninstallApp = { onUninstallApp(tab.id) },
                    onToggleMonitor = { onToggleMonitor(tab.id) },
                    onToggleAutoScroll = { onToggleAutoScroll(tab.id) },
                    onLogFilterQueryChange = { query -> onLogFilterQueryChange(tab.id, query) },
                    onToggleLogFilterLevel = { level -> onToggleLogFilterLevel(tab.id, level) },
                    onClearLogs = { onClearLogs(tab.id) },
                    modifier = Modifier.fillMaxSize(),
                )
                null -> Unit
            }
        }
    }
}

val SIDE_PANEL_WIDTH = 400.dp
