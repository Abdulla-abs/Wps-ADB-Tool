package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.ApkMetadata
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.BatchActionParams
import `fun`.abbas.wps_adb.platform.ApkMetadataParser
import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceApp
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.QrPairingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlin.random.Random

class MockAdbRepository(
    private val apkMetadataParser: ApkMetadataParser = ApkMetadataParser(),
    private val initialScanDelayMs: Long = 1200,
    private val refreshDelayMs: Long = 600,
) : AdbRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installedPackages = mutableSetOf<Pair<String, String>>()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _logs = MutableStateFlow(MockData.initialLogs)
    override val logs: StateFlow<List<AdbLog>> = _logs.asStateFlow()

    private val _logcatLogs = MutableStateFlow<List<AdbLog>>(emptyList())
    override val logcatLogs: StateFlow<List<AdbLog>> = _logcatLogs.asStateFlow()

    private val _isAdbActive = MutableStateFlow(true)
    override val isAdbActive: StateFlow<Boolean> = _isAdbActive.asStateFlow()

    private val _isScanningDevices = MutableStateFlow(false)
    override val isScanningDevices: StateFlow<Boolean> = _isScanningDevices.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val logcatClosers = mutableMapOf<String, () -> Unit>()
    private var mockGlobalLogcatJob: Job? = null
    private var qrPairingJob: Job? = null
    private var qrPairingCancel: (() -> Unit)? = null

    init {
        scope.launch { performInitialDeviceScan() }
        startLiveLogFeed()
    }

    private suspend fun performInitialDeviceScan() {
        _isScanningDevices.value = true
        try {
            if (initialScanDelayMs > 0) delay(initialScanDelayMs)
            _devices.value = MockData.initialDevices
            addLog(LogLevel.I, "DeviceTracker", "Discovered ${_devices.value.size} device(s)", "system")
        } finally {
            _isScanningDevices.value = false
        }
    }

    override suspend fun refreshDevices() {
        _isScanningDevices.value = true
        try {
            if (refreshDelayMs > 0) delay(refreshDelayMs)
            addLog(LogLevel.I, "DeviceTracker", "Re-discovering connected TCP endpoints...", "system")
        } finally {
            _isScanningDevices.value = false
        }
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

    override fun pairWirelessViaQr(): Flow<QrPairingEvent> = callbackFlow {
        val creds = AdbQrPayloadBuilder.generate()
        trySend(QrPairingEvent.QrReady(creds.payload, creds.serviceName))
        trySend(QrPairingEvent.WaitingForScan)
        qrPairingCancel = {
            trySend(QrPairingEvent.Cancelled)
            close()
        }
        qrPairingJob = scope.launch {
            delay(2500)
            if (!isActive) return@launch
            trySend(QrPairingEvent.PairingInProgress("192.168.1.105:37845"))
            delay(800)
            if (!isActive) return@launch
            trySend(QrPairingEvent.Connecting("192.168.1.105:5555"))
            delay(600)
            if (!isActive) return@launch
            val device = pairWirelessDevice("192.168.1.105", 5555).getOrThrow()
            trySend(QrPairingEvent.Success(device))
            close()
        }
        awaitClose {
            qrPairingJob?.cancel()
            qrPairingJob = null
            qrPairingCancel = null
        }
    }

    override fun cancelQrPairing() {
        qrPairingJob?.cancel()
        qrPairingJob = null
        qrPairingCancel?.invoke()
        qrPairingCancel = null
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

    override suspend fun rebootToRecovery(deviceId: String): Boolean {
        val target = _devices.value.find { it.id == deviceId } ?: return false
        addLog(LogLevel.W, "EasyAction", "Recovery reboot: ${target.serial}", deviceId)
        _devices.update { list -> list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.OFFLINE) else it } }
        return true
    }

    override suspend fun clearAppCache(deviceId: String, packageName: String): Boolean {
        addLog(LogLevel.I, "EasyAction", "Cleared cache for $packageName (mock)", deviceId)
        return true
    }

    override suspend fun takeScreenshotToDownloads(deviceId: String): String? {
        addLog(LogLevel.I, "EasyAction", "Screenshot saved (mock)", deviceId)
        return "/mock/screenshot.png"
    }

    override suspend fun takeScreenshotToClipboard(deviceId: String): Boolean {
        addLog(LogLevel.I, "EasyAction", "Screenshot copied to clipboard (mock)", deviceId)
        return true
    }

    override suspend fun startScreenRecord(deviceId: String): Boolean {
        addLog(LogLevel.I, "EasyAction", "Screen recording started (mock)", deviceId)
        return true
    }

    override suspend fun stopScreenRecord(deviceId: String): String? {
        addLog(LogLevel.I, "EasyAction", "Screen recording stopped (mock)", deviceId)
        return "/mock/record.mp4"
    }

    override suspend fun forceStopApp(deviceId: String, packageName: String): Boolean {
        addLog(LogLevel.I, "EasyAction", "force-stop $packageName (mock)", deviceId)
        return true
    }

    override suspend fun clearAppData(deviceId: String, packageName: String): Boolean {
        addLog(LogLevel.I, "EasyAction", "pm clear $packageName (mock)", deviceId)
        return true
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

    override suspend fun removeDevice(deviceId: String) {
        val target = _devices.value.find { it.id == deviceId } ?: return
        _devices.update { list -> list.filterNot { it.id == deviceId } }
        addLog(LogLevel.I, "DeviceManager", "Removed ${target.serial} from device list", deviceId)
    }

    override suspend fun installApk(fileName: String) {
        addLog(LogLevel.I, "ApkInstaller", "Starting broadcast ADB sideload installation of: $fileName", "system")
        _devices.value.filter { it.status == DeviceStatus.ONLINE }.forEach { device ->
            installApkOnDeviceInternal(device, fileName)
        }
        addLog(LogLevel.I, "ApkInstaller", "Batch deployment of $fileName concluded successfully.", "system")
    }

    override suspend fun installApkOnDevice(deviceId: String, apkPath: String): ApkInstallResult {
        val fileName = apkPath.substringAfterLast('/').substringAfterLast('\\')
        val device = _devices.value.find { it.id == deviceId } ?: run {
            val message = "Device not found: $deviceId"
            addLog(LogLevel.E, "ApkInstaller", message, "system")
            return ApkInstallResult(false, message, apkPath, fileName)
        }
        if (device.status != DeviceStatus.ONLINE) {
            val message = "Device offline, cannot install: ${device.serial}"
            addLog(LogLevel.W, "ApkInstaller", message, deviceId)
            return ApkInstallResult(false, message, apkPath, fileName)
        }
        return installApkOnDeviceInternal(device, apkPath)
    }

    private suspend fun installApkOnDeviceInternal(device: Device, apkPath: String): ApkInstallResult {
        val fileName = apkPath.substringAfterLast('/').substringAfterLast('\\')
        addLog(LogLevel.I, "ApkInstaller", "Pushing apk bundle payload to device serial: ${device.serial}", device.id)
        delay(1400)
        val metadata = apkMetadataParser.parse(apkPath, _settings.value.adbPath)
            ?: ApkFileNameParser.metadataFromFileName(fileName)
            ?: ApkMetadataResolver.fromFileName(fileName)
        addLog(
            LogLevel.I,
            "PackageInstaller",
            "Package ${metadata.packageName} successfully installed on interface: ${device.serial}",
            device.id,
        )
        installedPackages.add(device.id to metadata.packageName)
        return ApkInstallResult(
            success = true,
            message = "Success",
            apkPath = apkPath,
            apkFileName = fileName,
            metadata = metadata,
        )
    }

    override suspend fun isPackageInstalled(deviceId: String, packageName: String): Boolean {
        if (installedPackages.contains(deviceId to packageName)) return true
        val device = _devices.value.find { it.id == deviceId } ?: return false
        return device.apps.any { it.packageName == packageName }
    }

    override suspend fun parseApkMetadata(apkPath: String): ApkMetadata? {
        val fileName = apkPath.substringAfterLast('/').substringAfterLast('\\')
        return apkMetadataParser.parse(apkPath, _settings.value.adbPath)
            ?: ApkFileNameParser.metadataFromFileName(fileName)
            ?: ApkMetadataResolver.fromFileName(fileName)
    }

    override suspend fun launchApp(
        deviceId: String,
        packageName: String,
        launchActivity: String?,
    ): Result<Unit> {
        val device = _devices.value.find { it.id == deviceId }
            ?: return Result.failure(IllegalStateException("Device not found: $deviceId"))
        if (device.status != DeviceStatus.ONLINE) {
            return Result.failure(IllegalStateException("Device offline: ${device.serial}"))
        }
        delay(400)
        addLog(
            LogLevel.I,
            "AppLauncher",
            "Launched $packageName on ${device.serial}",
            deviceId,
        )
        return Result.success(Unit)
    }

    override suspend fun uninstallApp(deviceId: String, packageName: String): Result<Unit> {
        val device = _devices.value.find { it.id == deviceId }
            ?: return Result.failure(IllegalStateException("Device not found: $deviceId"))
        if (device.status != DeviceStatus.ONLINE) {
            return Result.failure(IllegalStateException("Device offline: ${device.serial}"))
        }
        delay(400)
        addLog(
            LogLevel.I,
            "AppUninstaller",
            "Uninstalled $packageName on ${device.serial}",
            deviceId,
        )
        return Result.success(Unit)
    }

    override fun startAppLogcat(deviceId: String, packageName: String, tabId: String): Flow<AdbLog> = callbackFlow {
        val device = _devices.value.find { it.id == deviceId }
        if (device == null) {
            close()
            return@callbackFlow
        }
        var index = 0
        val emitterJob: Job = scope.launch {
            val messages = listOf(
                "onCreate called",
                "Application initialized",
                "Activity resumed",
                "Network request completed",
            )
            while (isActive) {
                val ms = System.currentTimeMillis()
                val timestamp = "${(ms / 3_600_000 % 24).toString().padStart(2, '0')}:${(ms / 60_000 % 60).toString().padStart(2, '0')}:${(ms / 1000 % 60).toString().padStart(2, '0')}.${(ms % 1000).toString().padStart(3, '0')}"
                val currentIndex = index++
                trySend(
                    AdbLog(
                        id = "mock_logcat_${tabId}_$currentIndex",
                        timestamp = timestamp,
                        tag = packageName.substringAfterLast('.'),
                        level = if (currentIndex % 4 == 0) LogLevel.D else LogLevel.I,
                        message = messages[currentIndex % messages.size],
                        deviceId = deviceId,
                    ),
                )
                delay(1500)
            }
        }
        logcatClosers[tabId] = {
            emitterJob.cancel()
            close()
        }
        awaitClose {
            emitterJob.cancel()
            logcatClosers.remove(tabId)
        }
    }

    override fun stopAppLogcat(tabId: String) {
        logcatClosers.remove(tabId)?.invoke()
    }

    override fun stopAllAppLogcatSessions() {
        logcatClosers.keys.toList().forEach(::stopAppLogcat)
    }

    override fun startGlobalLogcat(deviceId: String?) {
        mockGlobalLogcatJob?.cancel()
        mockGlobalLogcatJob = scope.launch {
            var i = 0
            val sessionId = System.nanoTime()
            while (isActive) {
                delay(800)
                val device = deviceId?.let { id -> _devices.value.find { it.id == id } }
                    ?: _devices.value.filter { it.status == DeviceStatus.ONLINE }.randomOrNull()
                    ?: continue
                val line = "03-15 10:14:22.123  1234  1234 I MockTag: heartbeat #$i"
                appendMockLogcat(device.id, line, sessionId, i++)
            }
        }
    }

    override fun stopGlobalLogcat() {
        mockGlobalLogcatJob?.cancel()
        mockGlobalLogcatJob = null
    }

    private fun appendMockLogcat(deviceId: String, line: String, sessionId: Long, lineIndex: Int) {
        val log = LogcatLineParser.parse(
            line = line,
            deviceId = deviceId,
            tabId = "global",
            sessionId = sessionId,
            lineIndex = lineIndex,
        )
        _logcatLogs.update { (it + log).takeLast(2500) }
    }

    override fun clearLogcatLogs() {
        _logcatLogs.value = emptyList()
    }

    override suspend fun killAdbServer() {
        stopAllAppLogcatSessions()
        if (!_isAdbActive.value) return
        _isAdbActive.value = false
        _devices.update { list -> list.map { it.copy(status = DeviceStatus.OFFLINE) } }
        addLog(LogLevel.E, "AdbDaemon", "ADB transport server aborted by user action. Sockets closed.", "system")
    }

    override suspend fun restartAdbServer() {
        stopAllAppLogcatSessions()
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

    override suspend fun runBatchAction(
        group: FilterTab,
        actionKey: String,
        params: BatchActionParams,
    ): List<String> {
        val activeDevices = _devices.value.filter { device ->
            device.status == DeviceStatus.ONLINE && when (group) {
                FilterTab.PHYSICAL -> device.type == DeviceType.PHYSICAL
                FilterTab.EMULATORS -> device.type == DeviceType.EMULATOR
                FilterTab.ALL -> true
            }
        }
        if (activeDevices.isEmpty()) return emptyList()

        val detail = when {
            actionKey.contains("install-package") -> params.apkPath?.substringAfterLast('/') ?: "apk"
            actionKey.contains("pm clear") -> params.packageName ?: "com.android.settings"
            else -> actionKey
        }
        val lines = mutableListOf("Initiating parallel cluster batch operation: $actionKey")
        activeDevices.forEach { device ->
            delay(300)
            lines += "[OK] ${device.serial}: $detail"
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
