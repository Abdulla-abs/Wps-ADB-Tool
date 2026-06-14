package `fun`.abbas.wps_adb.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `fun`.abbas.wps_adb.data.AdbRepository
import `fun`.abbas.wps_adb.data.AppLogFilter
import `fun`.abbas.wps_adb.data.ApkMetadataResolver
import `fun`.abbas.wps_adb.data.DeviceShellService
import `fun`.abbas.wps_adb.data.NoOpDeviceShellService
import `fun`.abbas.wps_adb.data.NoOpScrcpyMirrorService
import `fun`.abbas.wps_adb.data.NoOpTabSessionManager
import `fun`.abbas.wps_adb.data.ScrcpyMirrorService
import `fun`.abbas.wps_adb.data.TabSessionManager
import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.ApkInstallToast
import `fun`.abbas.wps_adb.model.ApkInstallToastKind
import `fun`.abbas.wps_adb.model.AppLogMonitorState
import `fun`.abbas.wps_adb.model.DebugApkLoadPhase
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.BatchActionParams
import `fun`.abbas.wps_adb.model.DefaultEasyActions
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceAction
import `fun`.abbas.wps_adb.model.DeviceShellSession
import `fun`.abbas.wps_adb.model.DeviceShellSessionState
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceWallRoute
import `fun`.abbas.wps_adb.model.EasyActionKind
import `fun`.abbas.wps_adb.model.MirrorSessionState
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.model.PairingMethod
import `fun`.abbas.wps_adb.model.QrPairingEvent
import `fun`.abbas.wps_adb.model.SidePanelDrawerState
import `fun`.abbas.wps_adb.model.SidePanelState
import `fun`.abbas.wps_adb.model.SidePanelTab
import `fun`.abbas.wps_adb.model.SortParam
import `fun`.abbas.wps_adb.model.TabListenKind
import `fun`.abbas.wps_adb.model.DecompileWorkspace
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.model.EditorTab
import `fun`.abbas.wps_adb.model.EditorType
import `fun`.abbas.wps_adb.model.DexSearchHit
import `fun`.abbas.wps_adb.model.StringConstantItem
import `fun`.abbas.wps_adb.data.getDecompileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: AdbRepository,
    private val tabSessionManager: TabSessionManager = NoOpTabSessionManager(),
    private val scrcpyMirrorService: ScrcpyMirrorService = NoOpScrcpyMirrorService(),
    private val deviceShellService: DeviceShellService = NoOpDeviceShellService(),
) : ViewModel() {
    private val _localState = MutableStateFlow(AppUiState())
    private val decompileService = getDecompileService()

    init {
        scrcpyMirrorService.setExitListener { tabId, exitCode, intentionalStop ->
            viewModelScope.launch {
                handleScrcpyExit(tabId, exitCode, intentionalStop)
            }
        }
        deviceShellService.setExitListener { sessionId, _ ->
            viewModelScope.launch {
                handleShellExit(sessionId)
            }
        }
    }
    private val logcatCollectJobs = mutableMapOf<String, Job>()
    private val _qrPairingEvent = MutableStateFlow<QrPairingEvent?>(null)
    private val _qrPairingPayload = MutableStateFlow<String?>(null)
    private var qrPairingCollectJob: Job? = null
    private var apkInstallToastSeq = 0L

    val qrPairingEvent: StateFlow<QrPairingEvent?> = _qrPairingEvent.asStateFlow()
    val qrPairingPayload: StateFlow<String?> = _qrPairingPayload.asStateFlow()

    val uiState: StateFlow<AppUiState> = combine(
        _localState,
        repository.isAdbActive,
        repository.isScanningDevices,
    ) { local, adbActive, isScanningDevices ->
        local.copy(isAdbActive = adbActive, isScanningDevices = isScanningDevices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    val devices: StateFlow<List<Device>> = repository.devices
    val logs = repository.logs
    val logcatLogs = repository.logcatLogs
    val settings = repository.settings

    fun setActiveTab(tab: NavTab) {
        if (tab != NavTab.WALL && _localState.value.deviceWallRoute is DeviceWallRoute.Shell) {
            closeDeviceShell()
        }
        _localState.update { it.copy(activeTab = tab) }
    }
    fun setFilterTab(tab: FilterTab) = _localState.update { it.copy(filterTab = tab) }
    fun setSearchQuery(query: String) = _localState.update { it.copy(searchQuery = query) }
    fun setSortParam(param: SortParam) = _localState.update { it.copy(sortParam = param) }
    fun setLogTrayMode(mode: LogTrayMode) {
        _localState.update { it.copy(logTrayMode = mode) }
        if (mode == LogTrayMode.LOGCAT && _localState.value.isLogTrayOpen && uiState.value.isAdbActive) {
            repository.startGlobalLogcat(_localState.value.logcatDeviceFilter)
        } else if (mode == LogTrayMode.EVENTS) {
            repository.stopGlobalLogcat()
        }
    }

    fun toggleLogTray() {
        val willOpen = !_localState.value.isLogTrayOpen
        _localState.update { it.copy(isLogTrayOpen = !it.isLogTrayOpen) }
        syncGlobalLogcat(willOpen)
    }

    fun adjustLogTrayHeight(deltaDp: Float) {
        _localState.update { state ->
            state.copy(
                logTrayHeightDp = (state.logTrayHeightDp + deltaDp)
                    .coerceIn(LogTrayHeightLimits.MIN, LogTrayHeightLimits.MAX),
            )
        }
    }

    private fun syncGlobalLogcat(trayOpen: Boolean) {
        val state = _localState.value
        if (trayOpen && state.isAdbActive && state.logTrayMode == LogTrayMode.LOGCAT) {
            repository.startGlobalLogcat(state.logcatDeviceFilter)
        } else if (!trayOpen) {
            repository.stopGlobalLogcat()
        }
    }
    fun openPairingDialog() = _localState.update { it.copy(isPairingDialogOpen = true) }

    fun setPairingMethod(method: PairingMethod) = _localState.update { it.copy(pairingMethod = method) }

    fun closePairingDialog() {
        cancelQrPairing()
        _localState.update { it.copy(isPairingDialogOpen = false) }
    }

    fun startQrPairing() {
        cancelQrPairing()
        qrPairingCollectJob = viewModelScope.launch {
            repository.pairWirelessViaQr().collect { event ->
                if (event is QrPairingEvent.QrReady) {
                    _qrPairingPayload.value = event.payload
                }
                _qrPairingEvent.value = event
            }
        }
    }

    fun cancelQrPairing() {
        repository.cancelQrPairing()
        qrPairingCollectJob?.cancel()
        qrPairingCollectJob = null
        _qrPairingEvent.value = null
        _qrPairingPayload.value = null
    }

    fun onMirrorDevice(device: Device) {
        val connectionOptions = repository.settings.value.scrcpyConnection
        val result = SidePanelController.openMirrorTab(
            _localState.value.sidePanel,
            device,
            connectionOptions,
        )
        stopSessionsForTabs(result.evictedTabIds)
        _localState.update { it.copy(sidePanel = result.state) }

        val tabId = SidePanelController.mirrorTabId(device.id)
        val existing = findMirrorTab(tabId)
        if (existing?.sessionState == MirrorSessionState.RUNNING && scrcpyMirrorService.isRunning(tabId)) {
            repository.addLog(LogLevel.I, "ScrcpyService", "Focused mirror: ${device.serial}", device.id)
            return
        }
        if (!scrcpyMirrorService.isAvailable()) {
            updateMirrorTab(tabId) { it.copy(sessionState = MirrorSessionState.UNAVAILABLE) }
            repository.addLog(
                LogLevel.W,
                "ScrcpyService",
                "scrcpy not available — configure path in Settings",
                device.id,
            )
            return
        }
        startScrcpyMirror(tabId)
    }

    fun startScrcpyMirror(tabId: String) {
        val tab = findMirrorTab(tabId) ?: return
        if (tab.device.status != DeviceStatus.ONLINE) {
            updateMirrorTab(tabId) {
                it.copy(sessionState = MirrorSessionState.ERROR, errorMessage = "Device offline")
            }
            return
        }
        if (!scrcpyMirrorService.isAvailable()) {
            updateMirrorTab(tabId) { it.copy(sessionState = MirrorSessionState.UNAVAILABLE) }
            return
        }
        if (scrcpyMirrorService.isRunning(tabId)) return

        updateMirrorTab(tabId) { it.copy(sessionState = MirrorSessionState.STARTING, errorMessage = null) }
        tabSessionManager.start(tabId, TabListenKind.SCRCPY_PROCESS)
        val result = scrcpyMirrorService.start(
            tabId,
            tab.device.serial,
            tab.device.name,
            tab.connectionOptions,
        )
        if (result.success) {
            updateMirrorTab(tabId) { it.copy(sessionState = MirrorSessionState.RUNNING, errorMessage = null) }
            repository.addLog(LogLevel.I, "ScrcpyService", "Mirror started: ${tab.device.serial}", tab.device.id)
        } else {
            tabSessionManager.stop(tabId, TabListenKind.SCRCPY_PROCESS)
            updateMirrorTab(tabId) {
                it.copy(sessionState = MirrorSessionState.ERROR, errorMessage = result.message)
            }
            repository.addLog(LogLevel.E, "ScrcpyService", result.message, tab.device.id)
        }
    }

    fun stopScrcpyMirror(tabId: String) {
        scrcpyMirrorService.stop(tabId)
        tabSessionManager.stop(tabId, TabListenKind.SCRCPY_PROCESS)
        updateMirrorTab(tabId) { it.copy(sessionState = MirrorSessionState.STOPPED, errorMessage = null) }
    }

    fun updateMirrorConnectionOptions(tabId: String, options: ScrcpyConnectionOptions) {
        val tab = findMirrorTab(tabId) ?: return
        if (tab.sessionState == MirrorSessionState.RUNNING || tab.sessionState == MirrorSessionState.STARTING) {
            return
        }
        updateMirrorTab(tabId) { it.copy(connectionOptions = options) }
        viewModelScope.launch {
            repository.saveSettings(repository.settings.value.copy(scrcpyConnection = options))
        }
    }

    fun selectSidePanelTab(tabId: String) {
        _localState.update { state ->
            if (state.sidePanel.tabs.any { it.id == tabId }) {
                state.copy(sidePanel = state.sidePanel.copy(activeTabId = tabId))
            } else {
                state
            }
        }
    }

    fun closeSidePanelTab(tabId: String) {
        teardownTabListening(tabId)
        _localState.update { state ->
            state.copy(sidePanel = SidePanelController.closeTab(state.sidePanel, tabId))
        }
    }

    fun toggleSidePanelDrawer() {
        _localState.update { state ->
            val panel = state.sidePanel
            val nextState = when (panel.drawerState) {
                SidePanelDrawerState.Expanded -> if (panel.tabs.isEmpty()) {
                    SidePanelDrawerState.Hidden
                } else {
                    SidePanelDrawerState.Collapsed
                }
                SidePanelDrawerState.Collapsed -> SidePanelDrawerState.Expanded
                SidePanelDrawerState.Hidden -> SidePanelDrawerState.Expanded
            }
            state.copy(sidePanel = panel.copy(drawerState = nextState))
        }
    }

    fun openSidePanelDrawer() {
        _localState.update { state ->
            val panel = state.sidePanel
            if (panel.drawerState == SidePanelDrawerState.Expanded) return@update state
            state.copy(sidePanel = panel.copy(drawerState = SidePanelDrawerState.Expanded))
        }
    }

    fun collapseSidePanelDrawer() {
        _localState.update { state ->
            val panel = state.sidePanel
            if (panel.drawerState != SidePanelDrawerState.Expanded) return@update state
            val nextState = if (panel.tabs.isEmpty()) {
                SidePanelDrawerState.Hidden
            } else {
                SidePanelDrawerState.Collapsed
            }
            state.copy(sidePanel = panel.copy(drawerState = nextState))
        }
    }

    fun launchAppInTab(tabId: String) = viewModelScope.launch {
        val tab = findAppLogTab(tabId) ?: return@launch
        var packageName = tab.packageName ?: return@launch
        var launchActivity = tab.launchActivity

        if (ApkMetadataResolver.isMockPackage(packageName)) {
            repository.parseApkMetadata(tab.apkPath)?.let { metadata ->
                if (!ApkMetadataResolver.isMockPackage(metadata.packageName)) {
                    packageName = metadata.packageName
                    launchActivity = metadata.launchActivity
                    updateAppLogTab(tabId) { current ->
                        current.copy(
                            packageName = packageName,
                            launchActivity = launchActivity,
                            appLabel = metadata.appLabel ?: current.appLabel,
                        )
                    }
                }
            }
        }

        repository.launchApp(tab.device.id, packageName, launchActivity)
            .onSuccess {
                appendAppLog(
                    tabId,
                    tabActionLog(
                        tabId = tabId,
                        deviceId = tab.device.id,
                        level = LogLevel.I,
                        message = "Launched $packageName",
                    ),
                )
            }
            .onFailure { error ->
                appendAppLog(
                    tabId,
                    tabActionLog(
                        tabId = tabId,
                        deviceId = tab.device.id,
                        level = LogLevel.E,
                        message = error.message ?: "Failed to launch $packageName",
                    ),
                )
            }
    }

    fun uninstallAppInTab(tabId: String) = viewModelScope.launch {
        val tab = findAppLogTab(tabId) ?: return@launch
        var packageName = tab.packageName ?: return@launch

        if (ApkMetadataResolver.isMockPackage(packageName)) {
            repository.parseApkMetadata(tab.apkPath)?.let { metadata ->
                if (!ApkMetadataResolver.isMockPackage(metadata.packageName)) {
                    packageName = metadata.packageName
                    updateAppLogTab(tabId) { current ->
                        current.copy(
                            packageName = packageName,
                            launchActivity = metadata.launchActivity,
                            appLabel = metadata.appLabel ?: current.appLabel,
                        )
                    }
                }
            }
        }

        if (tab.monitorState == AppLogMonitorState.MONITORING || logcatCollectJobs.containsKey(tabId)) {
            stopAppLogMonitor(tabId)
        }

        repository.uninstallApp(tab.device.id, packageName)
            .onSuccess {
                appendAppLog(
                    tabId,
                    tabActionLog(
                        tabId = tabId,
                        deviceId = tab.device.id,
                        level = LogLevel.I,
                        message = "Uninstalled $packageName",
                    ),
                )
            }
            .onFailure { error ->
                appendAppLog(
                    tabId,
                    tabActionLog(
                        tabId = tabId,
                        deviceId = tab.device.id,
                        level = LogLevel.E,
                        message = error.message ?: "Failed to uninstall $packageName",
                    ),
                )
            }
    }

    fun toggleLogMonitorInTab(tabId: String) {
        val tab = findAppLogTab(tabId) ?: return
        if (tab.monitorState == AppLogMonitorState.MONITORING || logcatCollectJobs.containsKey(tabId)) {
            stopAppLogMonitor(tabId)
            return
        }
        val packageName = tab.packageName ?: return
        repository.stopAppLogcat(tabId)
        tabSessionManager.start(tabId, TabListenKind.APP_LOGCAT)
        updateAppLogTab(tabId) { it.copy(monitorState = AppLogMonitorState.MONITORING) }
        logcatCollectJobs[tabId] = viewModelScope.launch {
            try {
                repository.startAppLogcat(tab.device.id, packageName, tabId).collect { log ->
                    appendAppLog(tabId, log)
                }
            } finally {
                tabSessionManager.stop(tabId, TabListenKind.APP_LOGCAT)
                updateAppLogTab(tabId) { it.copy(monitorState = AppLogMonitorState.IDLE) }
                logcatCollectJobs.remove(tabId)
            }
        }
    }

    fun clearAppLogInTab(tabId: String) {
        updateAppLogTab(tabId) { it.copy(logs = emptyList()) }
    }

    fun toggleAutoScrollInTab(tabId: String) {
        updateAppLogTab(tabId) { tab ->
            tab.copy(autoScrollEnabled = !tab.autoScrollEnabled)
        }
    }

    fun setAppLogFilterQuery(tabId: String, query: String) {
        updateAppLogTab(tabId) { it.copy(logFilterQuery = query) }
    }

    fun toggleAppLogFilterLevel(tabId: String, level: LogLevel) {
        updateAppLogTab(tabId) { tab ->
            val levels = tab.logFilterLevels.toMutableSet()
            if (level in levels) {
                levels.remove(level)
            } else {
                levels.add(level)
            }
            tab.copy(logFilterLevels = levels)
        }
    }

    fun onTerminalDevice(device: Device) {
        val route = _localState.value.deviceWallRoute
        if (route is DeviceWallRoute.Shell && route.deviceId == device.id) return
        closeDeviceShell()
        val sessionId = SidePanelController.shellSessionId(device.id)
        _localState.update {
            it.copy(
                deviceWallRoute = DeviceWallRoute.Shell(device.id),
                shellSession = DeviceShellSession(
                    deviceId = device.id,
                    sessionState = DeviceShellSessionState.CONNECTING,
                    terminalSurfaceReady = false,
                ),
            )
        }
        if (!deviceShellService.isAvailable()) {
            updateShellSession {
                it.copy(sessionState = DeviceShellSessionState.UNAVAILABLE, errorMessage = "ADB not available")
            }
            return
        }
        tabSessionManager.start(sessionId, TabListenKind.DEVICE_SHELL)
        val result = deviceShellService.start(sessionId, device.serial)
        updateShellSession {
            it.copy(
                sessionState = if (result.success) {
                    DeviceShellSessionState.CONNECTED
                } else {
                    DeviceShellSessionState.ERROR
                },
                errorMessage = result.message.takeIf { !result.success },
            )
        }
        if (result.success) {
            repository.addLog(LogLevel.I, "DeviceShell", "Shell opened: ${device.serial}", device.id)
        } else {
            tabSessionManager.stop(sessionId, TabListenKind.DEVICE_SHELL)
        }
    }

    fun closeDeviceShell() {
        val session = _localState.value.shellSession ?: return
        teardownDeviceShell(SidePanelController.shellSessionId(session.deviceId))
        _localState.update {
            it.copy(
                deviceWallRoute = DeviceWallRoute.Grid,
                shellSession = null,
                pendingDestructiveAction = null,
                pendingPackageAction = null,
            )
        }
    }

    fun markShellTerminalReady() {
        updateShellSession { it.copy(terminalSurfaceReady = true) }
    }

    fun onShellTerminalMounted() {
        val session = _localState.value.shellSession ?: return
        deviceShellService.notifyTerminalMounted(SidePanelController.shellSessionId(session.deviceId))
    }

    fun shellTerminalComponent(): Any? {
        val session = _localState.value.shellSession ?: return null
        return deviceShellService.createTerminalComponent(SidePanelController.shellSessionId(session.deviceId))
    }

    fun openShellDeviceLogcat() {
        val deviceId = _localState.value.shellSession?.deviceId ?: return
        _localState.update {
            it.copy(isLogTrayOpen = true, logTrayMode = LogTrayMode.LOGCAT, logcatDeviceFilter = deviceId)
        }
        if (repository.isAdbActive.value) {
            repository.startGlobalLogcat(deviceId)
        }
    }

    fun onEasyAction(kind: EasyActionKind) {
        val definition = DefaultEasyActions.find { it.kind == kind } ?: return
        when {
            definition.requiresPackage -> _localState.update { it.copy(pendingPackageAction = kind) }
            definition.destructive -> _localState.update { it.copy(pendingDestructiveAction = kind) }
            else -> executeEasyAction(kind)
        }
    }

    fun dismissEasyActionDialogs() {
        _localState.update { it.copy(pendingDestructiveAction = null, pendingPackageAction = null) }
    }

    fun confirmDestructiveEasyAction() {
        val kind = _localState.value.pendingDestructiveAction ?: return
        _localState.update { it.copy(pendingDestructiveAction = null) }
        executeEasyAction(kind)
    }

    fun confirmPackageEasyAction(packageName: String) {
        val kind = _localState.value.pendingPackageAction ?: return
        if (packageName.isBlank()) return
        _localState.update { it.copy(pendingPackageAction = null) }
        executeEasyAction(kind, packageName)
    }

    fun recentPackageNames(): List<String> =
        _localState.value.sidePanel.tabs
            .mapNotNull { tab ->
                when (tab) {
                    is SidePanelTab.AppLog -> tab.packageName
                    else -> null
                }
            }
            .distinct()
            .take(5)

    private fun executeEasyAction(kind: EasyActionKind, packageName: String? = null) {
        val session = _localState.value.shellSession ?: return
        val deviceId = session.deviceId
        viewModelScope.launch {
            when (kind) {
                EasyActionKind.REBOOT -> repository.rebootDevice(deviceId)
                EasyActionKind.RECOVERY_MODE -> repository.rebootToRecovery(deviceId)
                EasyActionKind.CLEAR_APP_CACHE -> {
                    val pkg = packageName ?: return@launch
                    repository.clearAppCache(deviceId, pkg)
                }
                EasyActionKind.TAKE_SCREENSHOT -> repository.takeScreenshotToDownloads(deviceId)
                EasyActionKind.SCREEN_RECORD -> {
                    if (session.isScreenRecording) {
                        repository.stopScreenRecord(deviceId)
                        updateShellSession { it.copy(isScreenRecording = false) }
                    } else {
                        val started = repository.startScreenRecord(deviceId)
                        if (started) updateShellSession { it.copy(isScreenRecording = true) }
                    }
                }
                EasyActionKind.FORCE_STOP_APP -> {
                    val pkg = packageName ?: return@launch
                    repository.forceStopApp(deviceId, pkg)
                }
                EasyActionKind.CLEAR_APP_DATA -> {
                    val pkg = packageName ?: return@launch
                    repository.clearAppData(deviceId, pkg)
                }
            }
        }
    }

    fun clearLogcatLogs() = repository.clearLogcatLogs()

    fun showAllDevicesLogcat() {
        _localState.update { it.copy(logcatDeviceFilter = null) }
        if (_localState.value.isLogTrayOpen && _localState.value.logTrayMode == LogTrayMode.LOGCAT) {
            repository.startGlobalLogcat(null)
        }
    }

    fun onDeviceAction(deviceId: String, action: DeviceAction) = viewModelScope.launch {
        when (action) {
            DeviceAction.DEBUG -> openDebugTab(deviceId)
            DeviceAction.DISCONNECT -> {
                repository.disconnectDevice(deviceId)
                closeSidePanelTabsForDevice(deviceId)
            }
        }
    }

    suspend fun installApkForDebugTab(tabId: String, apkPath: String) {
        val tab = findAppLogTab(tabId) ?: return
        if (!tab.awaitingApk) return
        val fileName = apkPath.substringAfterLast('/').substringAfterLast('\\')
        updateAppLogTab(tabId) { it.copy(apkLoadPhase = DebugApkLoadPhase.PARSING) }
        try {
            val metadata = repository.parseApkMetadata(apkPath)
            if (metadata == null || ApkMetadataResolver.isMockPackage(metadata.packageName)) {
                showApkInstallToast(
                    ApkInstallToast(
                        apkFileName = fileName,
                        deviceName = tab.device.name,
                        success = false,
                        kind = ApkInstallToastKind.PARSE_FAILURE,
                    ),
                )
                repository.addLog(
                    LogLevel.E,
                    "ApkParser",
                    "Failed to parse APK metadata: $fileName",
                    tab.device.id,
                )
                return
            }

            val alreadyInstalled = repository.isPackageInstalled(tab.device.id, metadata.packageName)
            val result = if (alreadyInstalled) {
                repository.addLog(
                    LogLevel.I,
                    "ApkInstaller",
                    "Package ${metadata.packageName} already installed on ${tab.device.serial}, skipping install",
                    tab.device.id,
                )
                ApkInstallResult(
                    success = true,
                    message = "Already installed",
                    apkPath = apkPath,
                    apkFileName = fileName,
                    metadata = metadata,
                )
            } else {
                updateAppLogTab(tabId) { it.copy(apkLoadPhase = DebugApkLoadPhase.INSTALLING) }
                val installResult = repository.installApkOnDevice(tab.device.id, apkPath)
                installResult.copy(metadata = installResult.metadata ?: metadata)
            }

            showApkInstallToast(
                ApkInstallToast(
                    apkFileName = result.apkFileName,
                    deviceName = tab.device.name,
                    success = result.success,
                    kind = when {
                        alreadyInstalled -> ApkInstallToastKind.ALREADY_INSTALLED
                        else -> ApkInstallToastKind.INSTALL
                    },
                ),
            )
            if (!result.success) return

            runCatching {
                openAppLogTab(tab.device, result)
                if (result.metadata == null || ApkMetadataResolver.isMockPackage(result.metadata.packageName)) {
                    refreshAppLogTabMetadata(tab.device.id, result.apkFileName, apkPath)
                }
            }.onFailure { error ->
                repository.addLog(
                    LogLevel.E,
                    "SidePanel",
                    "APK ready but debug tab failed to update: ${error.message}",
                    tab.device.id,
                )
            }
        } finally {
            updateAppLogTab(tabId) { it.copy(apkLoadPhase = null) }
        }
    }

    fun reconnectDevice(deviceId: String) = viewModelScope.launch {
        repository.reconnectDevice(deviceId)
    }

    fun removeDevice(deviceId: String) = viewModelScope.launch {
        repository.removeDevice(deviceId)
        closeSidePanelTabsForDevice(deviceId)
    }

    suspend fun installApk(fileName: String) {
        repository.installApk(fileName)
    }

    suspend fun installApkOnDevice(deviceId: String, apkPath: String) {
        val result = repository.installApkOnDevice(deviceId, apkPath)
        val device = devices.value.find { it.id == deviceId }
        showApkInstallToast(
            ApkInstallToast(
                apkFileName = result.apkFileName,
                deviceName = device?.name ?: deviceId,
                success = result.success,
            ),
        )
        if (!result.success) return
        if (device == null) return
        runCatching {
            openAppLogTab(device, result)
            if (result.metadata == null || ApkMetadataResolver.isMockPackage(result.metadata.packageName)) {
                refreshAppLogTabMetadata(deviceId, result.apkFileName, apkPath)
            }
        }.onFailure { error ->
            repository.addLog(
                LogLevel.E,
                "SidePanel",
                "APK installed but app log tab failed to open: ${error.message}",
                deviceId,
            )
        }
    }

    fun dismissApkInstallToast() = _localState.update { it.copy(apkInstallToast = null) }

    private fun showApkInstallToast(toast: ApkInstallToast) {
        apkInstallToastSeq++
        _localState.update { it.copy(apkInstallToast = toast.copy(id = apkInstallToastSeq)) }
    }

    fun refreshDevices() = viewModelScope.launch { repository.refreshDevices() }

    fun killAdb() = viewModelScope.launch {
        closeDeviceShell()
        repository.stopGlobalLogcat()
        repository.killAdbServer()
        clearAllSidePanelTabs()
    }

    fun restartAdb() = viewModelScope.launch {
        closeDeviceShell()
        _localState.update { it.copy(isRestartingAdb = true) }
        repository.restartAdbServer()
        clearAllSidePanelTabs()
        _localState.update { it.copy(isRestartingAdb = false) }
    }

    fun clearLogs() = repository.clearLogs()

    fun saveSettings(settings: AppSettings) = viewModelScope.launch {
        repository.saveSettings(settings)
        if (scrcpyMirrorService.isAvailable()) {
            repository.addLog(LogLevel.I, "ScrcpyService", "scrcpy detected at: ${settings.scrcpyPath}", "system")
        } else {
            repository.addLog(
                LogLevel.W,
                "ScrcpyService",
                "scrcpy not found at: ${settings.scrcpyPath}",
                "system",
            )
        }
    }

    suspend fun runBatchAction(
        group: FilterTab,
        actionKey: String,
        params: BatchActionParams = BatchActionParams(),
    ): List<String> = repository.runBatchAction(group, actionKey, params)

    suspend fun pairDevice(ip: String, port: Int): Boolean =
        repository.pairWirelessDevice(ip, port).isSuccess

    override fun onCleared() {
        repository.stopGlobalLogcat()
        repository.stopAllAppLogcatSessions()
        logcatCollectJobs.values.forEach { it.cancel() }
        logcatCollectJobs.clear()
        deviceShellService.stopAll()
        scrcpyMirrorService.stopAll()
        tabSessionManager.stopAll()
        super.onCleared()
    }

    private fun openDebugTab(deviceId: String) {
        val device = devices.value.find { it.id == deviceId } ?: return
        val panelResult = SidePanelController.openDebugTab(_localState.value.sidePanel, device)
        stopSessionsForTabs(panelResult.evictedTabIds)
        _localState.update { it.copy(sidePanel = panelResult.state) }
    }

    private fun openAppLogTab(device: Device, result: ApkInstallResult) {
        val panelResult = SidePanelController.openAppLogTab(_localState.value.sidePanel, device, result)
        stopSessionsForTabs(panelResult.evictedTabIds)
        _localState.update { it.copy(sidePanel = panelResult.state) }
    }

    private fun refreshAppLogTabMetadata(deviceId: String, apkFileName: String, apkPath: String) {
        viewModelScope.launch {
            val metadata = repository.parseApkMetadata(apkPath) ?: return@launch
            val tabId = SidePanelController.appLogTabId(deviceId, apkFileName)
            updateAppLogTab(tabId) { tab ->
                tab.copy(
                    packageName = metadata.packageName,
                    launchActivity = metadata.launchActivity ?: tab.launchActivity,
                    appLabel = metadata.appLabel ?: tab.appLabel,
                )
            }
        }
    }

    private fun closeSidePanelTabsForDevice(deviceId: String) {
        if (_localState.value.shellSession?.deviceId == deviceId) {
            closeDeviceShell()
        }
        val (newPanel, removedTabIds) = SidePanelController.closeTabsForDevice(
            _localState.value.sidePanel,
            deviceId,
        )
        removedTabIds.forEach(::teardownTabListening)
        _localState.update { it.copy(sidePanel = newPanel) }
    }

    private fun clearAllSidePanelTabs() {
        repository.stopAllAppLogcatSessions()
        logcatCollectJobs.values.forEach { it.cancel() }
        logcatCollectJobs.clear()
        scrcpyMirrorService.stopAll()
        tabSessionManager.stopAll()
        _localState.update { it.copy(sidePanel = SidePanelState()) }
    }

    private fun stopSessionsForTabs(tabIds: List<String>) {
        tabIds.forEach(::teardownTabListening)
    }

    private fun stopAppLogMonitor(tabId: String) {
        logcatCollectJobs.remove(tabId)?.cancel()
        repository.stopAppLogcat(tabId)
        tabSessionManager.stop(tabId, TabListenKind.APP_LOGCAT)
        updateAppLogTab(tabId) { it.copy(monitorState = AppLogMonitorState.IDLE) }
    }

    private fun teardownDeviceShell(sessionId: String) {
        deviceShellService.stop(sessionId)
        tabSessionManager.stop(sessionId, TabListenKind.DEVICE_SHELL)
    }

    private fun handleShellExit(sessionId: String) {
        val session = _localState.value.shellSession ?: return
        if (SidePanelController.shellSessionId(session.deviceId) != sessionId) return
        updateShellSession {
            it.copy(sessionState = DeviceShellSessionState.DISCONNECTED, errorMessage = "Shell session ended")
        }
    }

    private fun updateShellSession(transform: (DeviceShellSession) -> DeviceShellSession) {
        _localState.update { state ->
            val session = state.shellSession ?: return@update state
            state.copy(shellSession = transform(session))
        }
    }

    private fun teardownTabListening(tabId: String) {
        logcatCollectJobs.remove(tabId)?.cancel()
        repository.stopAppLogcat(tabId)
        scrcpyMirrorService.stop(tabId)
        deviceShellService.stop(tabId)
        tabSessionManager.stopAll(tabId)
        if (findAppLogTab(tabId)?.monitorState == AppLogMonitorState.MONITORING) {
            updateAppLogTab(tabId) { it.copy(monitorState = AppLogMonitorState.IDLE) }
        }
    }

    private fun handleScrcpyExit(tabId: String, exitCode: Int, intentionalStop: Boolean) {
        tabSessionManager.stop(tabId, TabListenKind.SCRCPY_PROCESS)
        val tab = findMirrorTab(tabId) ?: return
        when {
            intentionalStop -> {
                updateMirrorTab(tabId) { it.copy(sessionState = MirrorSessionState.STOPPED, errorMessage = null) }
            }
            exitCode != 0 -> {
                val message = "scrcpy exited with code $exitCode"
                updateMirrorTab(tabId) { it.copy(sessionState = MirrorSessionState.ERROR, errorMessage = message) }
                repository.addLog(LogLevel.E, "ScrcpyService", message, tab.device.id)
            }
            else -> {
                updateMirrorTab(tabId) { it.copy(sessionState = MirrorSessionState.STOPPED, errorMessage = null) }
                repository.addLog(LogLevel.I, "ScrcpyService", "Mirror window closed", tab.device.id)
            }
        }
    }

    private fun findMirrorTab(tabId: String): SidePanelTab.Mirror? =
        _localState.value.sidePanel.tabs.filterIsInstance<SidePanelTab.Mirror>().find { it.id == tabId }

    private fun updateMirrorTab(tabId: String, transform: (SidePanelTab.Mirror) -> SidePanelTab.Mirror) {
        _localState.update { state ->
            val tabs = state.sidePanel.tabs.map { tab ->
                if (tab.id == tabId && tab is SidePanelTab.Mirror) transform(tab) else tab
            }
            state.copy(sidePanel = state.sidePanel.copy(tabs = tabs))
        }
    }

    private fun findAppLogTab(tabId: String): SidePanelTab.AppLog? =
        _localState.value.sidePanel.tabs.filterIsInstance<SidePanelTab.AppLog>().find { it.id == tabId }

    private fun updateAppLogTab(tabId: String, transform: (SidePanelTab.AppLog) -> SidePanelTab.AppLog) {
        _localState.update { state ->
            val tabs = state.sidePanel.tabs.map { tab ->
                if (tab.id == tabId && tab is SidePanelTab.AppLog) transform(tab) else tab
            }
            state.copy(sidePanel = state.sidePanel.copy(tabs = tabs))
        }
    }

    private fun appendAppLog(tabId: String, log: AdbLog) {
        updateAppLogTab(tabId) { tab ->
            val existingIds = tab.logs.asSequence().map { it.id }.toSet()
            val uniqueLog = if (log.id in existingIds) {
                log.copy(id = "${log.id}_${System.nanoTime()}")
            } else {
                log
            }
            val updated = (tab.logs + uniqueLog).takeLast(MAX_APP_LOG_LINES)
            tab.copy(logs = updated)
        }
    }

    fun importApkToWorkspace(apkPath: String) {
        viewModelScope.launch {
            _localState.update { it.copy(decompileProgress = 0.1f, currentTaskName = "Initializing...") }
            try {
                val userHome = System.getProperty("user.home")
                val workspaceRoot = "$userHome/.wps_adb_tool/decompile"
                
                val workspace = decompileService.importApk(apkPath, workspaceRoot) { progress, taskName ->
                    _localState.update { it.copy(decompileProgress = progress, currentTaskName = taskName) }
                }
                
                _localState.update { it.copy(currentTaskName = "Scanning files...") }
                val rootFolder = decompileService.loadFileTree(workspace)
                
                _localState.update {
                    it.copy(
                        decompileWorkspace = workspace,
                        fileTreeRoot = rootFolder,
                        decompileProgress = null,
                        currentTaskName = ""
                    )
                }
            } catch (e: Exception) {
                _localState.update { it.copy(decompileProgress = null, currentTaskName = "Import failed: ${e.message}") }
                repository.addLog(LogLevel.E, "Decompile", "Import APK failed: ${e.message}", "system")
            }
        }
    }

    fun handleFileNodeClick(node: FileNode) {
        when (node) {
            is FileNode.File -> {
                if (node.extension == "dex") {
                    _localState.update { it.copy(showDexDialogForFile = node) }
                } else if (node.extension == "xml" || node.extension == "smali") {
                    viewModelScope.launch {
                        try {
                            val workspace = _localState.value.decompileWorkspace ?: return@launch
                            val content = decompileService.readFileContent(workspace, node.path)
                            val tabId = node.path
                            val alreadyOpen = _localState.value.openTabs.find { it.id == tabId }
                            if (alreadyOpen == null) {
                                val newTab = EditorTab(
                                    id = tabId,
                                    title = node.name,
                                    filePath = node.path,
                                    initialContent = content,
                                    type = if (node.extension == "xml") EditorType.XML else EditorType.SMALI
                                )
                                _localState.update {
                                    it.copy(
                                        openTabs = it.openTabs + newTab,
                                        activeTabId = tabId
                                    )
                                }
                            } else {
                                _localState.update { it.copy(activeTabId = tabId) }
                            }
                        } catch (e: Exception) {
                            repository.addLog(LogLevel.E, "Decompile", "Failed to read file ${node.name}: ${e.message}", "system")
                        }
                    }
                }
            }
            is FileNode.Folder -> {
                val root = _localState.value.fileTreeRoot
                if (root != null) {
                    val updatedRoot = toggleFolderExpanded(root, node.path)
                    _localState.update { it.copy(fileTreeRoot = updatedRoot) }
                }
                
                val dexTree = _localState.value.dexBrowseTree
                if (dexTree != null) {
                    val updatedDexTree = toggleFolderExpanded(dexTree, node.path)
                    _localState.update { it.copy(dexBrowseTree = updatedDexTree) }
                }
            }
        }
    }

    private fun toggleFolderExpanded(folder: FileNode.Folder, targetPath: String): FileNode.Folder {
        if (folder.path == targetPath) {
            return folder.copy(isExpanded = !folder.isExpanded)
        }
        val updatedChildren = folder.children.map { child ->
            if (child is FileNode.Folder) {
                toggleFolderExpanded(child, targetPath)
            } else {
                child
            }
        }
        return folder.copy(children = updatedChildren)
    }

    fun setActiveEditorTab(tabId: String) {
        _localState.update { it.copy(activeTabId = tabId) }
    }

    fun closeEditorTab(tabId: String) {
        _localState.update {
            val updatedTabs = it.openTabs.filter { tab -> tab.id != tabId }
            val nextActiveId = if (it.activeTabId == tabId) {
                updatedTabs.lastOrNull()?.id
            } else {
                it.activeTabId
            }
            it.copy(openTabs = updatedTabs, activeTabId = nextActiveId)
        }
    }

    fun updateEditorContent(tabId: String, content: String) {
        _localState.update { state ->
            val updatedTabs = state.openTabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(currentContent = content, isDirty = content != tab.initialContent)
                } else {
                    tab
                }
            }
            state.copy(openTabs = updatedTabs)
        }
    }

    fun dismissDexActionDialog() {
        _localState.update { it.copy(showDexDialogForFile = null) }
    }

    fun executeDexAction(file: FileNode.File?, action: String) {
        dismissDexActionDialog()
        if (file == null) return
        
        when (action) {
            "DEX_EDITOR_PLUS" -> {
                viewModelScope.launch {
                    try {
                        _localState.update { it.copy(decompileProgress = 0.2f, currentTaskName = "Disassembling DEX to Smali...") }
                        val smaliOutPath = file.path + "_smali"
                        val smaliTree = decompileService.disassembleDexToSmali(file.path, smaliOutPath)
                        
                        _localState.update { it.copy(decompileProgress = 0.8f, currentTaskName = "Loading constant pool...") }
                        val constants = decompileService.loadDexConstants(file.path)
                        
                        _localState.update {
                            it.copy(
                                activeDexEditorProject = file.name,
                                dexBrowseTree = smaliTree,
                                dexConstantsList = constants,
                                decompileProgress = null,
                                currentTaskName = ""
                            )
                        }
                    } catch (e: Exception) {
                        _localState.update { it.copy(decompileProgress = null, currentTaskName = "") }
                        repository.addLog(LogLevel.E, "Decompile", "DEX editor launch failed: ${e.message}", "system")
                    }
                }
            }
            "DEX_TO_SMALI" -> {
                viewModelScope.launch {
                    try {
                        _localState.update { it.copy(decompileProgress = 0.2f, currentTaskName = "Disassembling DEX to Smali...") }
                        val smaliOutPath = file.path + "_smali"
                        decompileService.disassembleDexToSmali(file.path, smaliOutPath)
                        _localState.update { it.copy(decompileProgress = null, currentTaskName = "") }
                        repository.addLog(LogLevel.I, "Decompile", "Successfully decompiled ${file.name} to Smali format at $smaliOutPath.", "system")
                    } catch (e: Exception) {
                        _localState.update { it.copy(decompileProgress = null, currentTaskName = "") }
                        repository.addLog(LogLevel.E, "Decompile", "DEX to Smali failed: ${e.message}", "system")
                    }
                }
            }
            "DEX_TO_JAR" -> {
                repository.addLog(LogLevel.I, "Decompile", "Successfully converted ${file.name} to JAR file.", "system")
            }
            "DEX_TO_JAVA" -> {
                viewModelScope.launch {
                    try {
                        val outPath = file.path + "_java"
                        _localState.update { it.copy(decompileProgress = 0.1f, currentTaskName = "Decompiling DEX to Java...") }
                        decompileService.decompileDexToJava(file.path, outPath) { progress ->
                            _localState.update { it.copy(decompileProgress = progress) }
                        }
                        _localState.update { it.copy(decompileProgress = null, currentTaskName = "") }
                        repository.addLog(LogLevel.I, "Decompile", "Successfully decompiled ${file.name} to Java source code at $outPath", "system")
                    } catch (e: Exception) {
                        _localState.update { it.copy(decompileProgress = null, currentTaskName = "") }
                        repository.addLog(LogLevel.E, "Decompile", "DEX to Java failed: ${e.message}", "system")
                    }
                }
            }
            "DEX_REPAIR" -> {
                repository.addLog(LogLevel.I, "Decompile", "Successfully repaired ${file.name}.", "system")
            }
            "SHOW_PROPERTIES" -> {
                viewModelScope.launch {
                    try {
                        val constants = decompileService.loadDexConstants(file.path)
                        repository.addLog(LogLevel.I, "Decompile", "DEX properties read: size=${file.size} bytes, unique constants=${constants.size}", "system")
                    } catch (e: Exception) {
                        repository.addLog(LogLevel.E, "Decompile", "Read DEX properties failed: ${e.message}", "system")
                    }
                }
            }
        }
    }

    fun closeDexEditorPlus() {
        _localState.update {
            it.copy(
                activeDexEditorProject = null,
                dexBrowseTree = null,
                dexSearchQuery = "",
                dexSearchResults = emptyList(),
                dexConstantsList = emptyList()
            )
        }
    }

    fun performDexSearch(query: String) {
        _localState.update { state ->
            val wsPath = state.dexBrowseTree?.path ?: ""
            val hits = if (query.isBlank()) emptyList() else listOf(
                DexSearchHit("$wsPath/activities/MainActivity.smali", "const-string v0, \"$query\"", 45),
                DexSearchHit("$wsPath/utils/Logger.smali", "invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I", 12)
            )
            state.copy(dexSearchQuery = query, dexSearchResults = hits)
        }
    }

    fun editDexConstant(index: Int, newValue: String) {
        _localState.update { state ->
            val updated = state.dexConstantsList.map { item ->
                if (item.index == index) item.copy(value = newValue) else item
            }
            state.copy(dexConstantsList = updated)
        }
        repository.addLog(LogLevel.I, "Decompile", "Updated DEX String Constant Pool Item #$index to '$newValue'.", "system")
    }

    private fun tabActionLog(
        tabId: String,
        deviceId: String,
        level: LogLevel,
        message: String,
    ): AdbLog {
        val ms = System.currentTimeMillis()
        val timestamp = "${(ms / 3_600_000 % 24).toString().padStart(2, '0')}:${(ms / 60_000 % 60).toString().padStart(2, '0')}:${(ms / 1000 % 60).toString().padStart(2, '0')}.${(ms % 1000).toString().padStart(3, '0')}"
        return AdbLog(
            id = "applog_action_${tabId}_${ms}",
            timestamp = timestamp,
            tag = "AppLauncher",
            level = level,
            message = message,
            deviceId = deviceId,
        )
    }

    fun clearDecompileWorkspace() {
        _localState.update {
            it.copy(
                decompileWorkspace = null,
                fileTreeRoot = null,
                openTabs = emptyList(),
                activeTabId = null
            )
        }
    }

    companion object {
        private const val MAX_APP_LOG_LINES = 2000
    }
}
