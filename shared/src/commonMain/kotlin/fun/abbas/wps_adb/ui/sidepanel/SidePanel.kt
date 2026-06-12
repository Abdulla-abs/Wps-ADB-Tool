package `fun`.abbas.wps_adb.ui.sidepanel

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.sidepanel_action_collapse
import wpsadbtool.shared.generated.resources.sidepanel_empty_subtitle
import wpsadbtool.shared.generated.resources.sidepanel_empty_title
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.MirrorSessionState
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.SidePanelState
import `fun`.abbas.wps_adb.model.SidePanelTab
import `fun`.abbas.wps_adb.theme.CarbonColors

private val SidePanelScrimColor = Color.Black.copy(alpha = 0.45f)

@Composable
fun SidePanelScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SidePanelScrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}

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
    if (!state.isExpanded) return

    val drawerShape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(SIDE_PANEL_WIDTH)
            .animateContentSize()
            .shadow(elevation = 12.dp, shape = drawerShape, clip = false)
            .clip(drawerShape)
            .background(CarbonColors.SurfaceContainerLow)
            .border(width = 1.dp, color = CarbonColors.OutlineVariant, shape = drawerShape),
    ) {
        SidePanelExpandedContent(
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

fun sidePanelContentInsetEnd(state: SidePanelState): Dp = 0.dp
