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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import wpsadbtool.shared.generated.resources.Res
import wpsadbtool.shared.generated.resources.apk_install_toast_failure
import wpsadbtool.shared.generated.resources.apk_install_toast_success
import wpsadbtool.shared.generated.resources.logs_logcat_filter_device
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.theme.CarbonColors
import `fun`.abbas.wps_adb.ui.device.DeviceWallScreen
import `fun`.abbas.wps_adb.ui.groups.GroupManagementScreen
import `fun`.abbas.wps_adb.ui.logs.TerminalLogsPanel
import `fun`.abbas.wps_adb.ui.pairing.PairingDialog
import `fun`.abbas.wps_adb.ui.settings.SettingsScreen
import `fun`.abbas.wps_adb.ui.sidepanel.SIDE_PANEL_WIDTH
import `fun`.abbas.wps_adb.ui.sidepanel.SidePanel
import `fun`.abbas.wps_adb.viewmodel.AppViewModel

@Composable
fun AppShell(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val logcatLogs by viewModel.logcatLogs.collectAsState()
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
                            isScanningDevices = uiState.isScanningDevices,
                            filterTab = uiState.filterTab,
                            searchQuery = uiState.searchQuery,
                            sortParam = uiState.sortParam,
                            onMirror = viewModel::onMirrorDevice,
                            onTerminal = viewModel::onTerminalDevice,
                            onAction = viewModel::onDeviceAction,
                            onApkDrop = viewModel::installApkOnDevice,
                            onReconnect = viewModel::reconnectDevice,
                            onRemove = viewModel::removeDevice,
                            modifier = Modifier.fillMaxSize(),
                        )
                        NavTab.GROUPS -> GroupManagementScreen(
                            devices = devices,
                            onBatchAction = viewModel::runBatchAction,
                        )
                        NavTab.SETTINGS -> SettingsScreen(
                            settings = settings,
                            onSave = viewModel::saveSettings,
                            modifier = Modifier.fillMaxSize(),
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

            if (uiState.sidePanel.isVisible) {
                SidePanel(
                    state = uiState.sidePanel,
                    onSelectTab = viewModel::selectSidePanelTab,
                    onCloseTab = viewModel::closeSidePanelTab,
                    onLaunchApp = viewModel::launchAppInTab,
                    onUninstallApp = viewModel::uninstallAppInTab,
                    onToggleMonitor = viewModel::toggleLogMonitorInTab,
                    onToggleAutoScroll = viewModel::toggleAutoScrollInTab,
                    onLogFilterQueryChange = viewModel::setAppLogFilterQuery,
                    onToggleLogFilterLevel = viewModel::toggleAppLogFilterLevel,
                    onClearLogs = viewModel::clearAppLogInTab,
                    onStartMirror = viewModel::startScrcpyMirror,
                    onStopMirror = viewModel::stopScrcpyMirror,
                    onConnectionOptionsChange = viewModel::updateMirrorConnectionOptions,
                    modifier = Modifier.width(SIDE_PANEL_WIDTH).fillMaxHeight(),
                )
            }
        }

        if (uiState.isAdbActive && uiState.isLogTrayOpen) {
            val filterDeviceName = uiState.logcatDeviceFilter?.let { id -> devices.find { it.id == id }?.name }
            TerminalLogsPanel(
                mode = uiState.logTrayMode,
                onModeChange = viewModel::setLogTrayMode,
                eventLogs = logs,
                logcatLogs = logcatLogs,
                logcatFilterLabel = filterDeviceName?.let { name -> stringResource(Res.string.logs_logcat_filter_device, name) },
                onShowAllDevices = viewModel::showAllDevicesLogcat,
                onClearEvents = viewModel::clearLogs,
                onClearLogcat = viewModel::clearLogcatLogs,
                onClose = viewModel::toggleLogTray,
                logRetention = settings.logRetention,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }

        uiState.apkInstallToast?.let { toast ->
            val message = if (toast.success) {
                stringResource(Res.string.apk_install_toast_success, toast.apkFileName, toast.deviceName)
            } else {
                stringResource(Res.string.apk_install_toast_failure, toast.apkFileName, toast.deviceName)
            }
            ToastBanner(
                message = message,
                isSuccess = toast.success,
                toastId = toast.id,
                onDismiss = viewModel::dismissApkInstallToast,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
        }

        if (uiState.isPairingDialogOpen) {
            val qrPairingEvent by viewModel.qrPairingEvent.collectAsState()
            val qrPairingPayload by viewModel.qrPairingPayload.collectAsState()
            PairingDialog(
                onDismiss = viewModel::closePairingDialog,
                onPairComplete = viewModel::pairDevice,
                pairingMethod = uiState.pairingMethod,
                onPairingMethodChange = viewModel::setPairingMethod,
                qrPairingEvent = qrPairingEvent,
                qrPairingPayload = qrPairingPayload,
                onStartQrPairing = viewModel::startQrPairing,
                onCancelQrPairing = viewModel::cancelQrPairing,
                onRefreshQrPairing = viewModel::startQrPairing,
                defaultPort = settings.minPort,
                minPort = settings.minPort,
                maxPort = settings.maxPort,
            )
        }
    }
}
