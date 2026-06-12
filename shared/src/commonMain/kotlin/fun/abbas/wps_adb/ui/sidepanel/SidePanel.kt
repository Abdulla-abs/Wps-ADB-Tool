package `fun`.abbas.wps_adb.ui.sidepanel

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.sidepanel_action_collapse
import wpsadbtool.shared.generated.resources.sidepanel_action_expand
import wpsadbtool.shared.generated.resources.sidepanel_empty_subtitle
import wpsadbtool.shared.generated.resources.sidepanel_empty_title
import wpsadbtool.shared.generated.resources.sidepanel_tab_count
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.MirrorSessionState
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.SidePanelDrawerState
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
    onDebugApkDrop: suspend (String, String) -> Unit,
    onStartMirror: (String) -> Unit,
    onStopMirror: (String) -> Unit,
    onConnectionOptionsChange: (String, ScrcpyConnectionOptions) -> Unit,
    onToggleDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    val drawerWidth = when (state.drawerState) {
        SidePanelDrawerState.Expanded -> SIDE_PANEL_WIDTH
        SidePanelDrawerState.Collapsed -> SIDE_PANEL_COLLAPSED_WIDTH
        SidePanelDrawerState.Hidden -> 0.dp
    }
    val drawerShape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(drawerWidth)
            .animateContentSize()
            .shadow(elevation = 12.dp, shape = drawerShape, clip = false)
            .clip(drawerShape)
            .background(CarbonColors.SurfaceContainerLow)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant, shape = drawerShape),
    ) {
        when (state.drawerState) {
            SidePanelDrawerState.Collapsed -> SidePanelCollapsedRail(
                tabCount = state.tabs.size,
                onExpand = onToggleDrawer,
            )
            SidePanelDrawerState.Expanded -> SidePanelExpandedContent(
                state = state,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onLaunchApp = onLaunchApp,
                onUninstallApp = onUninstallApp,
                onToggleMonitor = onToggleMonitor,
                onToggleAutoScroll = onToggleAutoScroll,
                onLogFilterQueryChange = onLogFilterQueryChange,
                onToggleLogFilterLevel = onToggleLogFilterLevel,
                onClearLogs = onClearLogs,
                onDebugApkDrop = onDebugApkDrop,
                onStartMirror = onStartMirror,
                onStopMirror = onStopMirror,
                onConnectionOptionsChange = onConnectionOptionsChange,
                onCollapse = onToggleDrawer,
            )
            SidePanelDrawerState.Hidden -> Unit
        }
    }
}

@Composable
private fun SidePanelCollapsedRail(
    tabCount: Int,
    onExpand: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onExpand)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "‹",
            fontSize = 20.sp,
            color = CarbonColors.Primary,
            modifier = Modifier.padding(4.dp),
        )
        Text(
            text = stringResource(Res.string.sidepanel_action_expand),
            fontSize = 10.sp,
            color = CarbonColors.Outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        if (tabCount > 0) {
            Text(
                text = stringResource(Res.string.sidepanel_tab_count, tabCount),
                fontSize = 10.sp,
                color = CarbonColors.OnPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(CarbonColors.Primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SidePanelExpandedContent(
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
    onDebugApkDrop: suspend (String, String) -> Unit,
    onStartMirror: (String) -> Unit,
    onStopMirror: (String) -> Unit,
    onConnectionOptionsChange: (String, ScrcpyConnectionOptions) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CarbonColors.SurfaceContainer)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.sidepanel_action_collapse),
                fontSize = 11.sp,
                color = CarbonColors.Outline,
            )
            Text(
                text = "›",
                fontSize = 18.sp,
                color = CarbonColors.Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onCollapse)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (state.tabs.isNotEmpty()) {
            SidePanelTabBar(
                tabs = state.tabs,
                activeTabId = state.activeTabId,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
            )
        }

        Box(
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
                    onApkDropped = { apkPath -> onDebugApkDrop(tab.id, apkPath) },
                    modifier = Modifier.fillMaxSize(),
                )
                null -> SidePanelEmptyState(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SidePanelEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.sidepanel_empty_title),
            fontSize = 14.sp,
            color = CarbonColors.OnSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.sidepanel_empty_subtitle),
            fontSize = 12.sp,
            color = CarbonColors.Outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

val SIDE_PANEL_WIDTH = 400.dp
val SIDE_PANEL_COLLAPSED_WIDTH = 48.dp
val SIDE_PANEL_CONTENT_GAP = 8.dp

fun sidePanelContentInsetEnd(state: SidePanelState): Dp = when (state.drawerState) {
    SidePanelDrawerState.Hidden -> 0.dp
    SidePanelDrawerState.Collapsed -> SIDE_PANEL_COLLAPSED_WIDTH + SIDE_PANEL_CONTENT_GAP
    SidePanelDrawerState.Expanded -> SIDE_PANEL_WIDTH + SIDE_PANEL_CONTENT_GAP
}
