package `fun`.abbas.wps_adb.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.ui.device.DeviceWallScreen
import `fun`.abbas.wps_adb.ui.groups.GroupManagementScreen
import `fun`.abbas.wps_adb.ui.logs.TerminalLogsPanel
import `fun`.abbas.wps_adb.ui.mirror.MirrorDrawer
import `fun`.abbas.wps_adb.ui.pairing.PairingDialog
import `fun`.abbas.wps_adb.ui.settings.SettingsScreen
import `fun`.abbas.wps_adb.viewmodel.AppViewModel

@Composable
fun AppShell(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val onlineCount = devices.count { it.status == DeviceStatus.ONLINE }

    Box(modifier = Modifier.fillMaxSize().background(CarbonColors.Background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                activeTab = uiState.activeTab,
                onTabChange = viewModel::setActiveTab,
                onlineCount = onlineCount,
                onApkInstall = viewModel::installApk,
                isLogTrayOpen = uiState.isLogTrayOpen,
                onToggleLogTray = viewModel::toggleLogTray,
                modifier = Modifier.width(240.dp).fillMaxHeight(),
            )

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppHeader(
                    activeTab = uiState.activeTab,
                    filterTab = uiState.filterTab,
                    searchQuery = uiState.searchQuery,
                    sortParam = uiState.sortParam,
                    onFilterChange = viewModel::setFilterTab,
                    onSearchChange = viewModel::setSearchQuery,
                    onSortChange = viewModel::setSortParam,
                    onRefresh = viewModel::refreshDevices,
                    onAddWireless = viewModel::openPairingDialog,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (uiState.isLogTrayOpen) 280.dp else 32.dp),
                ) {
                    when (uiState.activeTab) {
                        NavTab.WALL -> DeviceWallScreen(
                            devices = devices,
                            filterTab = uiState.filterTab,
                            searchQuery = uiState.searchQuery,
                            sortParam = uiState.sortParam,
                            onMirror = viewModel::onMirrorDevice,
                            onTerminal = viewModel::onTerminalDevice,
                            onAction = viewModel::onDeviceAction,
                            onApkDrop = viewModel::installApkOnDevice,
                            onReconnect = viewModel::reconnectDevice,
                            modifier = Modifier.fillMaxSize(),
                        )
                        NavTab.GROUPS -> GroupManagementScreen(
                            devices = devices,
                            onBatchAction = viewModel::runBatchAction,
                        )
                        NavTab.SETTINGS -> SettingsScreen(
                            settings = settings,
                            onSave = viewModel::saveSettings,
                        )
                    }
                }

                StatusFooter(
                    isAdbActive = uiState.isAdbActive,
                    isRestarting = uiState.isRestartingAdb,
                    onlineCount = onlineCount,
                    isLogTrayOpen = uiState.isLogTrayOpen,
                    onToggleLogTray = viewModel::toggleLogTray,
                    onKillAdb = viewModel::killAdb,
                    onRestartAdb = viewModel::restartAdb,
                )
            }
        }

        if (uiState.isAdbActive && uiState.isLogTrayOpen) {
            TerminalLogsPanel(
                logs = logs,
                onClear = viewModel::clearLogs,
                onClose = viewModel::toggleLogTray,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }

        if (uiState.isPairingDialogOpen) {
            PairingDialog(
                onDismiss = viewModel::closePairingDialog,
                onPairComplete = viewModel::pairDevice,
            )
        }

        uiState.mirroredDevice?.let { device ->
            MirrorDrawer(
                device = device,
                onClose = { viewModel.setMirroredDevice(null) },
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
            )
        }
    }
}
