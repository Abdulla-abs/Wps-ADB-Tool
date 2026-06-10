package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceApp
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class MockAdbRepository : AdbRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _devices = MutableStateFlow(MockData.initialDevices)
    override val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _logs = MutableStateFlow(MockData.initialLogs)
    override val logs: StateFlow<List<AdbLog>> = _logs.asStateFlow()

    private val _isAdbActive = MutableStateFlow(true)
    override val isAdbActive: StateFlow<Boolean> = _isAdbActive.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        startLiveLogFeed()
    }

    override suspend fun refreshDevices() {
        addLog(LogLevel.I, "DeviceTracker", "Re-discovering connected TCP endpoints...", "system")
    }

    override suspend fun pairWirelessDevice(ip: String, port: Int): Result<Device> {
        delay(1500)
        val newDevice = Device(
            id = "paired_${System.currentTimeMillis()}",
            name = "Pixel 7 Pro",
            serial = "GP4${Random.nextInt(100000, 999999)}",
            type = DeviceType.PHYSICAL,
            connectionType = ConnectionType.WIFI,
            status = DeviceStatus.ONLINE,
            androidVersion = "Android 14",
            batteryLevel = 72,
            isCharging = false,
            storageUsed = "45GB",
            storageTotal = "128GB",
            storagePercent = 35,
            screenshotUrl = "",
            screenDescription = "Home Screen",
            apps = listOf(
                DeviceApp("Launcher", "com.google.android.apps.nexuslauncher", "home"),
            ),
            activityLog = listOf("adb_daemon: Wireless pairing completed on $ip:$port"),
        )
        _devices.update { listOf(newDevice) + it }
        addLog(LogLevel.I, "AdbDaemon", "Client wireless handshaking paired successfully: [${newDevice.name}]", newDevice.id)
        return Result.success(newDevice)
    }

    override suspend fun rebootDevice(deviceId: String) {
        val target = _devices.value.find { it.id == deviceId } ?: return
        addLog(LogLevel.W, "DeviceManager", "reboot instruction piped to target client: ${target.serial}", deviceId)
        _devices.update { list ->
            list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.OFFLINE) else it }
        }
        scope.launch {
            delay(2500)
            _devices.update { list ->
                list.map {
                    if (it.id == deviceId) {
                        it.copy(status = DeviceStatus.ONLINE, batteryLevel = minOf(100, it.batteryLevel + 2))
                    } else {
                        it
                    }
                }
            }
            addLog(
                LogLevel.I,
                "DeviceManager",
                "Handshake restored. Target client initialized successfully: ${target.serial}",
                deviceId,
            )
        }
    }

    override suspend fun disconnectDevice(deviceId: String) {
        val target = _devices.value.find { it.id == deviceId } ?: return
        addLog(LogLevel.E, "DeviceManager", "Forced disconnection socket drop on request: ${target.serial}", deviceId)
        _devices.update { list ->
            list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.OFFLINE) else it }
        }
    }

    override suspend fun reconnectDevice(deviceId: String) {
        val target = _devices.value.find { it.id == deviceId } ?: return
        if (target.status == DeviceStatus.ONLINE) return
        addLog(LogLevel.I, "DeviceManager", "Reconnecting ${target.serial}...", deviceId)
        delay(1200)
        _devices.update { list ->
            list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.ONLINE) else it }
        }
        addLog(LogLevel.I, "DeviceManager", "Reconnected successfully: ${target.serial}", deviceId)
    }

    override suspend fun installApk(fileName: String) {
        addLog(LogLevel.I, "ApkInstaller", "Starting broadcast ADB sideload installation of: $fileName", "system")
        _devices.value.filter { it.status == DeviceStatus.ONLINE }.forEach { device ->
            installApkOnDeviceInternal(device, fileName)
        }
        addLog(LogLevel.I, "ApkInstaller", "Batch deployment of $fileName concluded successfully.", "system")
    }

    override suspend fun installApkOnDevice(deviceId: String, apkPath: String) {
        val device = _devices.value.find { it.id == deviceId } ?: run {
            addLog(LogLevel.E, "ApkInstaller", "Device not found: $deviceId", "system")
            return
        }
        if (device.status != DeviceStatus.ONLINE) {
            addLog(LogLevel.W, "ApkInstaller", "Device offline, cannot install: ${device.serial}", deviceId)
            return
        }
        installApkOnDeviceInternal(device, apkPath)
    }

    private suspend fun installApkOnDeviceInternal(device: Device, apkPath: String) {
        val fileName = apkPath.substringAfterLast('/').substringAfterLast('\\')
        addLog(LogLevel.I, "ApkInstaller", "Pushing apk bundle payload to device serial: ${device.serial}", device.id)
        delay(1400)
        addLog(
            LogLevel.I,
            "PackageInstaller",
            "Package $fileName successfully installed on interface: ${device.serial}",
            device.id,
        )
    }

    override suspend fun killAdbServer() {
        if (!_isAdbActive.value) return
        _isAdbActive.value = false
        _devices.update { list -> list.map { it.copy(status = DeviceStatus.OFFLINE) } }
        addLog(LogLevel.E, "AdbDaemon", "ADB transport server aborted by user action. Sockets closed.", "system")
    }

    override suspend fun restartAdbServer() {
        addLog(LogLevel.W, "AdbDaemon", "Resetting active socket daemon... Rebuilding network bindings.", "system")
        delay(1800)
        _isAdbActive.value = true
        _devices.value = MockData.initialDevices
        addLog(LogLevel.I, "AdbDaemon", "ADB Server successfully launched (v1.0.41). Sockets listening.", "system")
    }

    override fun addLog(level: LogLevel, tag: String, message: String, deviceId: String?) {
        val ms = System.currentTimeMillis()
        val ts = "${(ms / 3_600_000 % 24).toString().padStart(2, '0')}:${(ms / 60_000 % 60).toString().padStart(2, '0')}:${(ms / 1000 % 60).toString().padStart(2, '0')}.${(ms % 1000).toString().padStart(3, '0')}"
        _logs.update {
            it + AdbLog(
                id = "log_${System.currentTimeMillis()}_${it.size}",
                timestamp = ts,
                tag = tag,
                level = level,
                message = message,
                deviceId = deviceId,
            )
        }
    }

    override fun clearLogs() {
        _logs.value = emptyList()
    }

    override suspend fun saveSettings(settings: AppSettings) {
        _settings.value = settings
        addLog(LogLevel.I, "Settings", "Local ADB configurations saved successfully!", "system")
    }

    override suspend fun runBatchAction(group: FilterTab, actionKey: String): List<String> {
        val activeDevices = _devices.value.filter { device ->
            device.status == DeviceStatus.ONLINE && when (group) {
                FilterTab.PHYSICAL -> device.type == DeviceType.PHYSICAL
                FilterTab.EMULATORS -> device.type == DeviceType.EMULATOR
                FilterTab.ALL -> true
            }
        }
        if (activeDevices.isEmpty()) return emptyList()

        val lines = mutableListOf("Initiating parallel cluster batch operation: $actionKey")
        activeDevices.forEach { device ->
            delay(300)
            lines += "[$actionKey] Executed on ${device.serial} (${device.name})"
        }
        lines += "Batch operation $actionKey completed on ${activeDevices.size} devices."
        lines.forEach { addLog(LogLevel.I, "BatchExecutor", it, "system") }
        return lines
    }

    private fun startLiveLogFeed() {
        scope.launch {
            while (true) {
                delay(7200)
                if (!_isAdbActive.value) continue
                val activeDevices = _devices.value.filter { it.status == DeviceStatus.ONLINE }
                if (activeDevices.isEmpty()) continue
                val device = activeDevices.random()
                val messages = listOf(
                    "battery_service: battery properties changed (level=${device.batteryLevel})",
                    "power_manager: surface_flinger update state: render layers loaded",
                    "network_diag: tcp transport heartbeat ping successful (latency: ${(5..25).random()}ms)",
                    "input_reader: processed motion event window action (focus=true)",
                )
                addLog(LogLevel.D, "SysMonitor", messages.random(), device.id)
            }
        }
    }
}
