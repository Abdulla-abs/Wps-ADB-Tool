package `fun`.abbas.wps_adb.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `fun`.abbas.wps_adb.data.AdbRepository
import `fun`.abbas.wps_adb.data.AppLogFilter
import `fun`.abbas.wps_adb.data.ApkMetadataResolver
import `fun`.abbas.wps_adb.data.NoOpScrcpyMirrorService
import `fun`.abbas.wps_adb.data.NoOpTabSessionManager
import `fun`.abbas.wps_adb.data.ScrcpyMirrorService
import `fun`.abbas.wps_adb.data.TabSessionManager
import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.ApkInstallToast
import `fun`.abbas.wps_adb.model.AppLogMonitorState
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.BatchActionParams
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceAction
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.MirrorSessionState
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.model.PairingMethod
import `fun`.abbas.wps_adb.model.QrPairingEvent
import `fun`.abbas.wps_adb.model.SidePanelState
import `fun`.abbas.wps_adb.model.SidePanelTab
import `fun`.abbas.wps_adb.model.SortParam
import `fun`.abbas.wps_adb.model.TabListenKind
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
) : ViewModel() {
    private val _localState = MutableStateFlow(AppUiState())

    init {
        scrcpyMirrorService.setExitListener { tabId, exitCode, intentionalStop ->
            viewModelScope.launch {
                handleScrcpyExit(tabId, exitCode, intentionalStop)
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

    fun setActiveTab(tab: NavTab) = _localState.update { it.copy(activeTab = tab) }
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
        _localState.update {
            it.copy(isLogTrayOpen = true, logTrayMode = LogTrayMode.LOGCAT, logcatDeviceFilter = device.id)
        }
        if (repository.isAdbActive.value) {
            repository.startGlobalLogcat(device.id)
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
            DeviceAction.REBOOT -> repository.rebootDevice(deviceId)
            DeviceAction.DISCONNECT -> {
                repository.disconnectDevice(deviceId)
                closeSidePanelTabsForDevice(deviceId)
            }
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
        repository.stopGlobalLogcat()
        repository.killAdbServer()
        clearAllSidePanelTabs()
    }

    fun restartAdb() = viewModelScope.launch {
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
        scrcpyMirrorService.stopAll()
        tabSessionManager.stopAll()
        super.onCleared()
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

    private fun teardownTabListening(tabId: String) {
        logcatCollectJobs.remove(tabId)?.cancel()
        repository.stopAppLogcat(tabId)
        scrcpyMirrorService.stop(tabId)
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

    companion object {
        private const val MAX_APP_LOG_LINES = 2000
    }
}
