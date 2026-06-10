package `fun`.abbas.wps_adb.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `fun`.abbas.wps_adb.data.AdbRepository
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceAction
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.model.SortParam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: AdbRepository,
) : ViewModel() {
    private val _localState = MutableStateFlow(AppUiState())

    val uiState: StateFlow<AppUiState> = combine(
        _localState,
        repository.isAdbActive,
    ) { local, adbActive ->
        local.copy(isAdbActive = adbActive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    val devices: StateFlow<List<Device>> = repository.devices
    val logs = repository.logs
    val settings = repository.settings

    fun setActiveTab(tab: NavTab) = _localState.update { it.copy(activeTab = tab) }
    fun setFilterTab(tab: FilterTab) = _localState.update { it.copy(filterTab = tab) }
    fun setSearchQuery(query: String) = _localState.update { it.copy(searchQuery = query) }
    fun setSortParam(param: SortParam) = _localState.update { it.copy(sortParam = param) }
    fun toggleLogTray() = _localState.update { it.copy(isLogTrayOpen = !it.isLogTrayOpen) }
    fun openPairingDialog() = _localState.update { it.copy(isPairingDialogOpen = true) }
    fun closePairingDialog() = _localState.update { it.copy(isPairingDialogOpen = false) }
    fun setMirroredDevice(device: Device?) = _localState.update { it.copy(mirroredDevice = device) }

    fun onMirrorDevice(device: Device) {
        setMirroredDevice(device)
        repository.addLog(LogLevel.I, "MirrorService", "Starting mirroring on target: ${device.serial}", device.id)
    }

    fun onTerminalDevice(device: Device) {
        _localState.update { it.copy(isLogTrayOpen = true) }
        repository.addLog(LogLevel.I, "AdbTerminal", "Focused shell inspection stream on device: ${device.serial}", device.id)
    }

    fun onDeviceAction(deviceId: String, action: DeviceAction) = viewModelScope.launch {
        when (action) {
            DeviceAction.REBOOT -> repository.rebootDevice(deviceId)
            DeviceAction.DISCONNECT -> {
                repository.disconnectDevice(deviceId)
                if (_localState.value.mirroredDevice?.id == deviceId) {
                    setMirroredDevice(null)
                }
            }
        }
    }

    fun reconnectDevice(deviceId: String) = viewModelScope.launch {
        repository.reconnectDevice(deviceId)
    }

    suspend fun installApk(fileName: String) {
        repository.installApk(fileName)
    }

    suspend fun installApkOnDevice(deviceId: String, apkPath: String) {
        repository.installApkOnDevice(deviceId, apkPath)
    }

    fun refreshDevices() = viewModelScope.launch { repository.refreshDevices() }

    fun killAdb() = viewModelScope.launch {
        repository.killAdbServer()
        setMirroredDevice(null)
    }

    fun restartAdb() = viewModelScope.launch {
        _localState.update { it.copy(isRestartingAdb = true) }
        repository.restartAdbServer()
        _localState.update { it.copy(isRestartingAdb = false, mirroredDevice = null) }
    }

    fun clearLogs() = repository.clearLogs()

    fun saveSettings(settings: AppSettings) = viewModelScope.launch {
        repository.saveSettings(settings)
    }

    fun runBatchAction(group: FilterTab, actionKey: String) = viewModelScope.launch {
        repository.runBatchAction(group, actionKey)
    }

    suspend fun pairDevice(ip: String, port: Int): Boolean {
        val success = repository.pairWirelessDevice(ip, port).isSuccess
        if (success) {
            closePairingDialog()
        }
        return success
    }
}
