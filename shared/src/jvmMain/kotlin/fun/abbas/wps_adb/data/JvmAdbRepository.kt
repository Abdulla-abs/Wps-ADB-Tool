package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.ApkMetadata
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.BatchActionParams
import `fun`.abbas.wps_adb.model.PortRangeValidator
import `fun`.abbas.wps_adb.platform.ApkMetadataParser
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceScreenMetrics
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import `fun`.abbas.wps_adb.platform.ApkFileDetector
import java.io.File

class JvmAdbRepository(
    private val wirelessStore: WirelessDeviceStore = WirelessDeviceStore(),
    private val removedDeviceStore: RemovedDeviceStore = RemovedDeviceStore(),
    private val settingsStore: AppSettingsStore = AppSettingsStore(),
    private val apkMetadataParser: ApkMetadataParser = ApkMetadataParser(),
) : AdbRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _settings = MutableStateFlow(settingsStore.load())
    private val screenshotDir = File(System.getProperty("java.io.tmpdir"), "wps-adb-screenshots").apply { mkdirs() }
    private val logcatClosers = mutableMapOf<String, () -> Unit>()
    private val globalLogcatJobs = mutableMapOf<String, Job>() // key = serial
    private var globalLogcatFilterDeviceId: String? = null
    private var qrPairingService: JvmWirelessQrPairingService? = null
    private var qrPairingCollectJob: Job? = null
    private var enrichJob: Job? = null
    private var enrichGeneration = 0
    private var deviceScanJob: Job? = null
    private val hardwareSerialByTransport = mutableMapOf<String, String>()

    private val runner: JvmAdbRunner
        get() = JvmAdbRunner { _settings.value.adbPath }

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _logs = MutableStateFlow<List<AdbLog>>(emptyList())
    override val logs: StateFlow<List<AdbLog>> = _logs.asStateFlow()

    private val _logcatLogs = MutableStateFlow<List<AdbLog>>(emptyList())
    override val logcatLogs: StateFlow<List<AdbLog>> = _logcatLogs.asStateFlow()

    private val _isAdbActive = MutableStateFlow(true)
    override val isAdbActive: StateFlow<Boolean> = _isAdbActive.asStateFlow()

    private val _isScanningDevices = MutableStateFlow(false)
    override val isScanningDevices: StateFlow<Boolean> = _isScanningDevices.asStateFlow()

    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        if (_settings.value.autoApproveKey) {
            ensureAdbKeyPair()
        }
        scope.launch {
            if (!runner.isAvailable()) {
                addLog(LogLevel.W, "AdbDaemon", "ADB binary not found — check Settings > ADB path", "system")
                _isAdbActive.value = false
                return@launch
            }
            _isScanningDevices.value = true
            try {
                addLog(LogLevel.I, "AdbDaemon", "ADB server connected (desktop real mode)", "system")
                reconnectSavedWirelessDevices()
                refreshDevicesInternal()
            } finally {
                _isScanningDevices.value = false
            }
        }
        restartDeviceScanJob()
    }

    private fun restartDeviceScanJob() {
        deviceScanJob?.cancel()
        deviceScanJob = scope.launch {
            while (isActive) {
                val intervalSec = _settings.value.scanIntervalSec.coerceAtLeast(5)
                delay(intervalSec * 1000L)
                if (!_isAdbActive.value || _isScanningDevices.value) continue
                try {
                    refreshDevicesInternal()
                } catch (_: Exception) {
                    // keep scan loop alive
                }
            }
        }
    }

    private fun ensureAdbKeyPair() {
        if (JvmAdbKeyManager.ensureKeyPair(_settings.value.adbPath)) return
        addLog(
            LogLevel.W,
            "AdbKeyManager",
            "Could not ensure ADB RSA key pair — first USB connection may require manual authorization on device",
            "system",
        )
    }

    private suspend fun reconnectSavedWirelessDevices() = withContext(Dispatchers.IO) {
        val saved = wirelessStore.load()
        if (saved.isEmpty()) return@withContext
        addLog(LogLevel.I, "AdbDaemon", "Reconnecting ${saved.size} saved wireless device(s)...", "system")
        saved.forEach { device ->
            val result = runner.run(listOf("connect", device.endpoint))
            val output = result.output.ifBlank { "No output" }
            val level = if (result.success || "connected" in output.lowercase()) LogLevel.I else LogLevel.W
            addLog(level, "AdbDaemon", "${device.endpoint}: $output", "system")
        }
        delay(500)
    }

    override suspend fun refreshDevices() = withContext(Dispatchers.IO) {
        if (!_isAdbActive.value) return@withContext
        _isScanningDevices.value = true
        try {
            refreshDevicesInternal()
        } finally {
            _isScanningDevices.value = false
        }
    }

    private suspend fun refreshDevicesInternal() {
        val result = runner.run(listOf("devices", "-l"))
        if (!result.success) {
            addLog(LogLevel.E, "DeviceTracker", "adb devices failed: ${result.output}", "system")
            return
        }
        val parsed = DeviceTransportDeduplicator.dedupeParsedDevices(
            JvmAdbDeviceParser.parseDevicesOutput(result.output),
        ).filterNot { removedDeviceStore.contains(it.serial) }
        val previousBySerial = _devices.value.associateBy { it.serial }
        val basic = parsed.map { buildBasicDevice(it, previousBySerial[it.serial]) }
        _devices.value = mergeWithSavedWirelessDevices(
            DeviceTransportDeduplicator.dedupeDevices(basic, hardwareSerialByTransport),
        )
        addLog(LogLevel.I, "DeviceTracker", "Discovered ${_devices.value.size} device(s)", "system")
        scheduleDeviceEnrichment(parsed)
    }

    private fun buildBasicDevice(parsed: ParsedAdbDevice, previous: Device?): Device {
        val base = JvmAdbDeviceParser.toDevice(parsed)
        if (parsed.status != DeviceStatus.ONLINE || previous == null) return base
        return base.copy(
            androidVersion = previous.androidVersion,
            batteryLevel = previous.batteryLevel,
            isCharging = previous.isCharging,
            screenshotUrl = previous.screenshotUrl,
            formFactor = previous.formFactor,
            screenWidthPx = previous.screenWidthPx,
            screenHeightPx = previous.screenHeightPx,
        )
    }

    private fun scheduleDeviceEnrichment(parsed: List<ParsedAdbDevice>) {
        val online = parsed.filter { it.status == DeviceStatus.ONLINE }
        enrichJob?.cancel()
        if (online.isEmpty()) return
        val generation = ++enrichGeneration
        enrichJob = scope.launch(Dispatchers.IO) {
            coroutineScope {
                online.map { device ->
                    async {
                        if (generation != enrichGeneration) return@async
                        val enriched = enrichDevice(device)
                        if (generation != enrichGeneration) return@async
                        if (enriched.connectionType == ConnectionType.WIFI) {
                            wirelessStore.updateFromDevice(enriched)
                        }
                        applyEnrichedDevice(enriched)
                    }
                }.awaitAll()
            }
            deduplicateByHardwareSerial(generation)
        }
    }

    private fun deduplicateByHardwareSerial(generation: Int) {
        if (generation != enrichGeneration) return
        val current = _devices.value
        val deduped = DeviceTransportDeduplicator.dedupeDevices(current, hardwareSerialByTransport)
        val removedCount = current.size - deduped.size
        if (removedCount <= 0) return

        _devices.value = deduped
        addLog(
            LogLevel.I,
            "DeviceTracker",
            "Merged $removedCount duplicate transport(s) for the same hardware",
            "system",
        )
    }

    private fun applyEnrichedDevice(enriched: Device) {
        _devices.update { current ->
            val index = current.indexOfFirst { it.serial == enriched.serial }
            if (index < 0) return@update current
            current.toMutableList().apply { this[index] = enriched }
        }
    }

    private fun mergeWithSavedWirelessDevices(connected: List<Device>): List<Device> {
        val connectedSerials = connected.map { it.serial }.toSet()
        val offlineSaved = wirelessStore.load()
            .filter { saved -> saved.endpoint !in connectedSerials && !removedDeviceStore.contains(saved.endpoint) }
            .map { it.toOfflineDevice() }
        return connected + offlineSaved
    }

    override suspend fun pairWirelessDevice(ip: String, port: Int): Result<Device> = withContext(Dispatchers.IO) {
        if (!PortRangeValidator.isInRange(port, _settings.value)) {
            val range = PortRangeValidator.normalizedRange(_settings.value.minPort, _settings.value.maxPort)
            val message = "Port $port is outside configured range $range"
            addLog(LogLevel.E, "AdbDaemon", message, "system")
            return@withContext Result.failure(IllegalStateException(message))
        }
        val target = "$ip:$port"
        addLog(LogLevel.I, "AdbDaemon", "Connecting to wireless target $target...", "system")
        val result = runner.run(listOf("connect", target))
        val output = result.output.lowercase()
        val connected = result.success || "connected" in output || "already connected" in output
        if (!connected) {
            addLog(LogLevel.E, "AdbDaemon", "Connect failed: ${result.output}", "system")
            return@withContext Result.failure(IllegalStateException(result.output))
        }
        addLog(LogLevel.I, "AdbDaemon", result.output.ifBlank { "Connected to $target" }, "system")
        delay(800)
        refreshDevices()
        val device = _devices.value.find { it.serial == target }
            ?: _devices.value.find { ':' in it.serial && it.serial.startsWith(ip) }
        if (device != null) {
            clearRemovalMark(device.serial)
            clearRemovalMark(target)
            wirelessStore.updateFromDevice(device)
            addLog(LogLevel.I, "AdbDaemon", "Wireless device paired: ${device.name}", device.id)
            Result.success(device)
        } else {
            Result.failure(IllegalStateException("Device not found after connect"))
        }
    }

    override fun pairWirelessViaQr(): Flow<QrPairingEvent> = callbackFlow {
        qrPairingService?.cancel()
        qrPairingCollectJob?.cancel()
        val service = JvmWirelessQrPairingService(
            runner = runner,
            onDevicesRefresh = { refreshDevicesInternal() },
            findDevice = { target ->
                _devices.value.find { it.serial == target }
                    ?: _devices.value.find {
                        ':' in it.serial && it.serial.startsWith(target.substringBefore(':'))
                    }
            },
            updateWirelessStore = { device ->
                clearRemovalMark(device.serial)
                wirelessStore.updateFromDevice(device)
            },
        )
        qrPairingService = service
        val collectJob = scope.launch {
            service.pair().collect { event ->
                when (event) {
                    is QrPairingEvent.PairingInProgress ->
                        addLog(LogLevel.I, "AdbDaemon", "QR pairing with ${event.endpoint}...", "system")
                    is QrPairingEvent.Connecting ->
                        addLog(LogLevel.I, "AdbDaemon", "QR connecting to ${event.endpoint}...", "system")
                    is QrPairingEvent.Success ->
                        addLog(LogLevel.I, "AdbDaemon", "QR wireless device paired: ${event.device.name}", event.device.id)
                    is QrPairingEvent.Failure ->
                        addLog(LogLevel.E, "AdbDaemon", event.message, "system")
                    else -> Unit
                }
                trySend(event)
                if (event is QrPairingEvent.Success || event is QrPairingEvent.Failure || event is QrPairingEvent.Cancelled) {
                    close()
                }
            }
        }
        qrPairingCollectJob = collectJob
        awaitClose {
            service.cancel()
            collectJob.cancel()
            qrPairingService = null
            qrPairingCollectJob = null
        }
    }

    override fun cancelQrPairing() {
        qrPairingService?.cancel()
        qrPairingCollectJob?.cancel()
        qrPairingService = null
        qrPairingCollectJob = null
    }

    override suspend fun rebootDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val target = _devices.value.find { it.id == deviceId } ?: return@withContext
        addLog(LogLevel.W, "DeviceManager", "Rebooting ${target.serial}...", deviceId)
        _devices.update { list -> list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.OFFLINE) else it } }
        val result = runner.run(listOf("reboot"), serial = target.serial)
        if (result.success) {
            addLog(LogLevel.I, "DeviceManager", "Reboot command sent to ${target.serial}", deviceId)
            delay(3000)
            refreshDevices()
        } else {
            addLog(LogLevel.E, "DeviceManager", "Reboot failed: ${result.output}", deviceId)
        }
    }

    override suspend fun disconnectDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val target = _devices.value.find { it.id == deviceId } ?: return@withContext
        if (target.connectionType == ConnectionType.WIFI || ':' in target.serial) {
            runner.run(listOf("disconnect", target.serial))
        }
        _devices.update { list -> list.map { if (it.id == deviceId) it.copy(status = DeviceStatus.OFFLINE) else it } }
        addLog(LogLevel.E, "DeviceManager", "Disconnected ${target.serial}", deviceId)
    }

    override suspend fun reconnectDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val target = _devices.value.find { it.id == deviceId } ?: run {
            addLog(LogLevel.E, "DeviceManager", "Device not found: $deviceId", "system")
            return@withContext
        }
        if (target.status == DeviceStatus.ONLINE) {
            addLog(LogLevel.I, "DeviceManager", "Device already online: ${target.serial}", deviceId)
            return@withContext
        }
        addLog(LogLevel.I, "DeviceManager", "Reconnecting ${target.serial}...", deviceId)
        if (':' in target.serial) {
            val host = target.serial.substringBeforeLast(':')
            val port = target.serial.substringAfterLast(':').toIntOrNull() ?: 5555
            pairWirelessDevice(host, port)
        } else {
            refreshDevices()
        }
    }

    override suspend fun removeDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val target = _devices.value.find { it.id == deviceId } ?: return@withContext
        removedDeviceStore.add(target.serial)
        removeWirelessPersistence(target)
        dismissFromAdb(target)
        _devices.update { list -> list.filterNot { it.id == deviceId } }
        addLog(LogLevel.I, "DeviceManager", "Removed ${target.serial} from device list", deviceId)
    }

    private fun removeWirelessPersistence(device: Device) {
        if (':' in device.serial) {
            wirelessStore.remove(device.serial)
            return
        }
        if (device.connectionType == ConnectionType.WIFI && device.name.isNotBlank()) {
            wirelessStore.load()
                .filter { saved -> saved.name == device.name }
                .forEach { wirelessStore.remove(it.endpoint) }
        }
    }

    private fun dismissFromAdb(device: Device) {
        val isWireless = device.connectionType == ConnectionType.WIFI ||
            ':' in device.serial ||
            DeviceTransportDeduplicator.isWirelessTlsSerial(device.serial)
        if (!isWireless) return
        runner.run(listOf("disconnect", device.serial))
    }

    private fun clearRemovalMark(serial: String) {
        if (serial.isBlank()) return
        removedDeviceStore.remove(serial)
    }

    override suspend fun installApk(fileName: String) = withContext(Dispatchers.IO) {
        val apkFile = resolveApkFile(fileName) ?: run {
            addLog(LogLevel.E, "ApkInstaller", "APK file not found: $fileName", "system")
            return@withContext
        }
        val online = _devices.value.filter { it.status == DeviceStatus.ONLINE }
        if (online.isEmpty()) {
            addLog(LogLevel.W, "ApkInstaller", "No online devices for APK install", "system")
            return@withContext
        }
        addLog(LogLevel.I, "ApkInstaller", "Installing ${apkFile.name} on ${online.size} device(s)...", "system")
        val metadata = apkMetadataParser.parse(apkFile.absolutePath, _settings.value.adbPath)
        runParallel(online) { device ->
            installApkOnDeviceInternal(device, apkFile, apkFile.absolutePath, metadata)
        }
        Unit
    }

    override suspend fun installApkOnDevice(deviceId: String, apkPath: String): ApkInstallResult = withContext(Dispatchers.IO) {
        val apkFile = resolveApkFile(apkPath) ?: run {
            val message = "APK file not found: $apkPath"
            addLog(LogLevel.E, "ApkInstaller", message, deviceId)
            return@withContext ApkInstallResult(false, message, apkPath, apkPath.substringAfterLast('\\').substringAfterLast('/'))
        }
        val device = _devices.value.find { it.id == deviceId } ?: run {
            val message = "Device not found: $deviceId"
            addLog(LogLevel.E, "ApkInstaller", message, "system")
            return@withContext ApkInstallResult(false, message, apkPath, apkFile.name)
        }
        if (device.status != DeviceStatus.ONLINE) {
            val message = "Device offline, cannot install: ${device.serial}"
            addLog(LogLevel.W, "ApkInstaller", message, deviceId)
            return@withContext ApkInstallResult(false, message, apkPath, apkFile.name)
        }
        addLog(LogLevel.I, "ApkInstaller", "Installing ${apkFile.name} on ${device.serial}...", deviceId)
        val metadata = apkMetadataParser.parse(apkFile.absolutePath, _settings.value.adbPath)
            ?: ApkFileNameParser.metadataFromFileName(apkFile.name)
        installApkOnDeviceInternal(device, apkFile, apkPath, metadata)
    }

    override suspend fun parseApkMetadata(apkPath: String): ApkMetadata? = withContext(Dispatchers.IO) {
        val apkFile = resolveApkFile(apkPath) ?: return@withContext null
        apkMetadataParser.parse(apkFile.absolutePath, _settings.value.adbPath)
    }

    override suspend fun launchApp(
        deviceId: String,
        packageName: String,
        launchActivity: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val device = _devices.value.find { it.id == deviceId }
            ?: return@withContext Result.failure(IllegalStateException("Device not found: $deviceId"))
        if (device.status != DeviceStatus.ONLINE) {
            return@withContext Result.failure(IllegalStateException("Device offline: ${device.serial}"))
        }
        val result = runner.launchApp(device.serial, packageName, launchActivity)
        val output = result.output.ifBlank { "No output" }
        if (AppLaunchResult.isSuccessful(result.exitCode, output)) {
            addLog(LogLevel.I, "AppLauncher", "Launched $packageName on ${device.serial}", deviceId)
            Result.success(Unit)
        } else {
            addLog(LogLevel.E, "AppLauncher", "${device.serial}: $output", deviceId)
            Result.failure(IllegalStateException(output))
        }
    }

    override suspend fun uninstallApp(deviceId: String, packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val device = _devices.value.find { it.id == deviceId }
            ?: return@withContext Result.failure(IllegalStateException("Device not found: $deviceId"))
        if (device.status != DeviceStatus.ONLINE) {
            return@withContext Result.failure(IllegalStateException("Device offline: ${device.serial}"))
        }
        val result = runner.uninstallApp(device.serial, packageName)
        val output = result.output.ifBlank { "No output" }
        if (AppUninstallResult.isSuccessful(result.exitCode, output)) {
            addLog(LogLevel.I, "AppUninstaller", "Uninstalled $packageName on ${device.serial}", deviceId)
            Result.success(Unit)
        } else {
            addLog(LogLevel.E, "AppUninstaller", "${device.serial}: $output", deviceId)
            Result.failure(IllegalStateException(output))
        }
    }

    override fun startAppLogcat(deviceId: String, packageName: String, tabId: String): Flow<AdbLog> = callbackFlow {
        val device = _devices.value.find { it.id == deviceId }
        if (device == null) {
            close()
            return@callbackFlow
        }

        val sessionId = System.nanoTime()
        var lineIndex = 0
        var logcatSession: LogcatSession? = null
        var waitingMessageSent = false

        val monitorJob: Job = scope.launch(Dispatchers.IO) {
            try {
                var pid: Int? = null
                while (isActive && pid == null) {
                    pid = runner.resolveProcessId(device.serial, packageName)
                    if (pid == null) {
                        if (!waitingMessageSent) {
                            waitingMessageSent = true
                            trySend(
                                AdbLog(
                                    id = AppLogcatMessages.systemId(tabId, "waiting"),
                                    timestamp = "",
                                    tag = "AppLogcat",
                                    level = LogLevel.I,
                                    message = AppLogcatMessages.waitingForProcess(packageName),
                                    deviceId = deviceId,
                                ),
                            )
                        }
                        delay(500)
                    }
                }
                if (!isActive || pid == null) return@launch

                if (waitingMessageSent) {
                    trySend(
                        AdbLog(
                            id = AppLogcatMessages.systemId(tabId, "attached"),
                            timestamp = "",
                            tag = "AppLogcat",
                            level = LogLevel.I,
                            message = AppLogcatMessages.attachedToProcess(pid, packageName),
                            deviceId = deviceId,
                        ),
                    )
                }

                logcatSession = try {
                    runner.startLogcat(device.serial, pid)
                } catch (exception: Exception) {
                    trySend(
                        AdbLog(
                            id = AppLogcatMessages.systemId(tabId, "error"),
                            timestamp = "",
                            tag = "AppLogcat",
                            level = LogLevel.E,
                            message = exception.message ?: "Failed to start logcat",
                            deviceId = deviceId,
                        ),
                    )
                    close()
                    return@launch
                }

                logcatSession?.process?.inputStream?.bufferedReader()?.useLines { lines ->
                    lines.forEach { line ->
                        if (!isActive) return@useLines
                        if (
                            logcatSession?.filterPidClientSide == true &&
                            !LogcatLineParser.belongsToProcess(line, pid)
                        ) {
                            return@forEach
                        }
                        trySend(
                            LogcatLineParser.parse(
                                line = line,
                                deviceId = deviceId,
                                tabId = tabId,
                                sessionId = sessionId,
                                lineIndex = lineIndex++,
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                // stream ended
            } finally {
                logcatSession?.process?.destroy()
                close()
            }
        }

        logcatClosers[tabId] = {
            monitorJob.cancel()
            logcatSession?.process?.destroy()
            close()
        }
        awaitClose {
            monitorJob.cancel()
            logcatSession?.process?.destroy()
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
        globalLogcatFilterDeviceId = deviceId
        stopAllGlobalLogcatProcesses()

        val targets = if (deviceId != null) {
            val device = _devices.value.find { it.id == deviceId }
            if (device == null || device.status == DeviceStatus.OFFLINE) {
                addLog(LogLevel.W, "GlobalLogcat", "Device unavailable for logcat: $deviceId", deviceId)
                return
            }
            listOf(device)
        } else {
            _devices.value.filter { it.status == DeviceStatus.ONLINE }
        }

        if (targets.isEmpty()) return

        for (device in targets) {
            val serial = device.serial
            if (serial in globalLogcatJobs) continue

            val job = scope.launch(Dispatchers.IO) {
                val sessionId = System.nanoTime()
                var lineIndex = 0
                var logcatSession: LogcatSession? = null
                try {
                    logcatSession = runner.startGlobalLogcat(serial)
                    logcatSession.process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (!isActive) return@useLines
                            appendLogcatLine(
                                LogcatLineParser.parse(
                                    line = line,
                                    deviceId = device.id,
                                    tabId = "global",
                                    sessionId = sessionId,
                                    lineIndex = lineIndex++,
                                ),
                            )
                        }
                    }
                } catch (_: Exception) {
                    // stream ended
                } finally {
                    logcatSession?.process?.destroy()
                    globalLogcatJobs.remove(serial)
                }
            }
            globalLogcatJobs[serial] = job
        }
    }

    override fun stopGlobalLogcat() {
        globalLogcatFilterDeviceId = null
        stopAllGlobalLogcatProcesses()
    }

    private fun stopAllGlobalLogcatProcesses() {
        globalLogcatJobs.values.forEach { it.cancel() }
        globalLogcatJobs.clear()
    }

    private fun appendLogcatLine(log: AdbLog) {
        val retention = _settings.value.logRetention.coerceAtLeast(1)
        _logcatLogs.update { (it + log).takeLast(retention) }
    }

    override fun clearLogcatLogs() {
        _logcatLogs.value = emptyList()
    }

    private fun installApkOnDeviceInternal(
        device: Device,
        apkFile: File,
        apkPath: String,
        metadata: ApkMetadata?,
    ): ApkInstallResult {
        val result = runner.installApk(apkFile, device.serial)
        val output = result.output.ifBlank { "No output" }
        val success = result.success && ("Success" in output || "success" in output.lowercase())
        val level = if (success) LogLevel.I else LogLevel.E
        if (!apkFile.extension.equals("apk", ignoreCase = true)) {
            addLog(LogLevel.I, "ApkInstaller", "Using push+pm install for non-.apk file: ${apkFile.name}", device.id)
        }
        addLog(level, "ApkInstaller", "${device.serial}: $output", device.id)
        return ApkInstallResult(
            success = success,
            message = output,
            apkPath = apkPath,
            apkFileName = apkFile.name,
            metadata = metadata,
        )
    }

    override suspend fun killAdbServer() = withContext(Dispatchers.IO) {
        stopGlobalLogcat()
        stopAllAppLogcatSessions()
        runner.run(listOf("kill-server"))
        _isAdbActive.value = false
        _devices.value = emptyList()
        addLog(LogLevel.E, "AdbDaemon", "ADB server killed", "system")
    }

    override suspend fun restartAdbServer() = withContext(Dispatchers.IO) {
        stopGlobalLogcat()
        stopAllAppLogcatSessions()
        addLog(LogLevel.W, "AdbDaemon", "Restarting ADB server...", "system")
        runner.run(listOf("kill-server"))
        delay(300)
        val start = runner.run(listOf("start-server"))
        _isAdbActive.value = start.success
        if (start.success) {
            reconnectSavedWirelessDevices()
            refreshDevices()
            addLog(LogLevel.I, "AdbDaemon", "ADB server restarted successfully", "system")
        } else {
            addLog(LogLevel.E, "AdbDaemon", "Failed to restart ADB: ${start.output}", "system")
        }
    }

    override fun addLog(level: LogLevel, tag: String, message: String, deviceId: String?) {
        val ms = System.currentTimeMillis()
        val ts = "${(ms / 3_600_000 % 24).toString().padStart(2, '0')}:${(ms / 60_000 % 60).toString().padStart(2, '0')}:${(ms / 1000 % 60).toString().padStart(2, '0')}.${(ms % 1000).toString().padStart(3, '0')}"
        val retention = _settings.value.logRetention.coerceAtLeast(1)
        _logs.update {
            (it + AdbLog("log_${System.currentTimeMillis()}_${it.size}", ts, tag, level, message, deviceId))
                .takeLast(retention)
        }
    }

    override fun clearLogs() {
        _logs.value = emptyList()
    }

    override suspend fun saveSettings(settings: AppSettings) {
        _settings.value = settings
        settingsStore.save(settings)
        if (settings.autoApproveKey) {
            ensureAdbKeyPair()
        }
        restartDeviceScanJob()
        addLog(LogLevel.I, "Settings", "Settings saved (ADB: ${settings.adbPath})", "system")
        if (runner.isAvailable()) {
            _isAdbActive.value = true
            refreshDevices()
        }
    }

    override suspend fun runBatchAction(
        group: FilterTab,
        actionKey: String,
        params: BatchActionParams,
    ): List<String> = withContext(Dispatchers.IO) {
        val activeDevices = activeDevicesForGroup(group)
        if (activeDevices.isEmpty()) return@withContext emptyList()

        val parallelism = _settings.value.parallelThreads.coerceIn(1, 8)
        val lines = mutableListOf("Initiating batch: $actionKey on ${activeDevices.size} device(s) [parallel=$parallelism]")
        val results = runParallel(activeDevices) { device ->
            executeBatchOnDevice(device, actionKey, params)
        }
        results.forEach { line ->
            lines += line
            val success = line.startsWith("[OK]")
            addLog(if (success) LogLevel.I else LogLevel.E, "BatchExecutor", line, "system")
        }
        lines += "Batch completed."
        lines
    }

    private fun activeDevicesForGroup(group: FilterTab): List<Device> =
        _devices.value.filter { device ->
            device.status == DeviceStatus.ONLINE && when (group) {
                FilterTab.PHYSICAL -> device.type == DeviceType.PHYSICAL
                FilterTab.EMULATORS -> device.type == DeviceType.EMULATOR
                FilterTab.ALL -> true
            }
        }

    private suspend fun <T, R> runParallel(items: List<T>, block: suspend (T) -> R): List<R> {
        if (items.isEmpty()) return emptyList()
        val parallelism = _settings.value.parallelThreads.coerceIn(1, 8)
        val semaphore = Semaphore(parallelism)
        return coroutineScope {
            items.map { item ->
                async {
                    semaphore.withPermit { block(item) }
                }
            }.awaitAll()
        }
    }

    private suspend fun executeBatchOnDevice(
        device: Device,
        actionKey: String,
        params: BatchActionParams,
    ): String {
        return when {
            actionKey.contains("install-package") -> {
                val apkPath = params.apkPath
                    ?: return "[FAIL] ${device.serial}: no APK selected"
                val apkFile = resolveApkFile(apkPath)
                    ?: return "[FAIL] ${device.serial}: APK not found"
                val metadata = apkMetadataParser.parse(apkFile.absolutePath, _settings.value.adbPath)
                val installResult = installApkOnDeviceInternal(device, apkFile, apkPath, metadata)
                val status = if (installResult.success) "OK" else "FAIL"
                "[$status] ${device.serial}: install ${apkFile.name}"
            }
            actionKey.contains("reboot") -> {
                val result = runner.run(listOf("reboot"), serial = device.serial)
                val status = if (result.success) "OK" else "FAIL"
                "[$status] ${device.serial}: reboot"
            }
            actionKey.contains("battery") -> {
                val result = runner.run(listOf("shell", "dumpsys", "battery"), serial = device.serial)
                val status = if (result.success) "OK" else "FAIL"
                "[$status] ${device.serial}: dumpsys battery"
            }
            actionKey.contains("pm clear") -> {
                val packageName = params.packageName?.trim().orEmpty().ifBlank { "com.android.settings" }
                val result = runner.run(listOf("shell", "pm", "clear", packageName), serial = device.serial)
                val status = if (result.success) "OK" else "FAIL"
                "[$status] ${device.serial}: pm clear $packageName"
            }
            else -> {
                val result = runner.run(listOf("shell", "echo", "batch:$actionKey"), serial = device.serial)
                val status = if (result.success) "OK" else "FAIL"
                "[$status] ${device.serial}: $actionKey"
            }
        }
    }

    private fun enrichDevice(parsed: ParsedAdbDevice): Device {
        if (parsed.status != DeviceStatus.ONLINE) {
            return JvmAdbDeviceParser.toDevice(parsed)
        }
        val hardwareSerialResult = runner.run(listOf("shell", "getprop", "ro.serialno"), serial = parsed.serial)
        val hardwareSerial = hardwareSerialResult.output.trim()
            .takeIf { it.isNotBlank() }
            ?: parsed.deviceName?.takeIf { it.isNotBlank() }
            ?: parsed.serial
        hardwareSerialByTransport[parsed.serial] = hardwareSerial
        val versionResult = runner.run(listOf("shell", "getprop", "ro.build.version.release"), serial = parsed.serial)
        val androidVersion = if (versionResult.success) {
            "Android ${versionResult.output.trim()}"
        } else {
            "Android"
        }
        val batteryResult = runner.run(listOf("shell", "dumpsys", "battery"), serial = parsed.serial)
        val batteryLevel = parseBatteryLevel(batteryResult.output)
        val characteristicsResult = runner.run(listOf("shell", "getprop", "ro.build.characteristics"), serial = parsed.serial)
        val windowSizeResult = runner.run(listOf("shell", "wm", "size"), serial = parsed.serial)
        val screenSize = DeviceScreenMetrics.parseWindowSize(windowSizeResult.output)
        val screenWidthPx = screenSize?.first ?: 0
        val screenHeightPx = screenSize?.second ?: 0
        val formFactor = DeviceScreenMetrics.inferFormFactor(
            characteristics = characteristicsResult.output,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
        val screenshotUrl = captureScreenshotUrl(parsed.serial)
        return JvmAdbDeviceParser.toDevice(
            parsed = parsed,
            androidVersion = androidVersion,
            batteryLevel = batteryLevel,
            screenshotUrl = screenshotUrl,
            formFactor = formFactor,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    }

    private fun captureScreenshotUrl(serial: String): String {
        val outputFile = File(screenshotDir, "${safeSerialFileName(serial)}.png")
        return if (runner.captureScreenshot(serial, outputFile)) {
            outputFile.absolutePath
        } else {
            ""
        }
    }

    private fun safeSerialFileName(serial: String): String =
        serial.replace(Regex("""[^\w.-]"""), "_")

    private fun parseBatteryLevel(output: String): Int {
        val match = Regex("""level:\s*(\d+)""").find(output)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun resolveApkFile(fileName: String): File? {
        val candidates = buildList {
            val direct = File(fileName)
            if (direct.isAbsolute && direct.exists()) add(direct)
            if (direct.exists()) add(direct)
            val inCwd = File(System.getProperty("user.dir"), fileName)
            if (inCwd.exists()) add(inCwd)
        }.distinctBy { it.absolutePath }

        candidates.firstOrNull { ApkFileDetector.isApkFile(it) }?.let { return it }

        val existing = candidates.firstOrNull()
        if (existing != null) {
            addLog(LogLevel.E, "ApkInstaller", "File is not a valid APK: ${existing.absolutePath}", "system")
        }
        return null
    }
}
