package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceScreenMetrics
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
import kotlinx.coroutines.withContext
import `fun`.abbas.wps_adb.platform.ApkFileDetector
import java.io.File

class JvmAdbRepository(
    private val wirelessStore: WirelessDeviceStore = WirelessDeviceStore(),
) : AdbRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _settings = MutableStateFlow(AppSettings())
    private val screenshotDir = File(System.getProperty("java.io.tmpdir"), "wps-adb-screenshots").apply { mkdirs() }

    private val runner: JvmAdbRunner
        get() = JvmAdbRunner { _settings.value.adbPath }

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _logs = MutableStateFlow<List<AdbLog>>(emptyList())
    override val logs: StateFlow<List<AdbLog>> = _logs.asStateFlow()

    private val _isAdbActive = MutableStateFlow(true)
    override val isAdbActive: StateFlow<Boolean> = _isAdbActive.asStateFlow()

    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        scope.launch {
            if (runner.isAvailable()) {
                addLog(LogLevel.I, "AdbDaemon", "ADB server connected (desktop real mode)", "system")
                reconnectSavedWirelessDevices()
                refreshDevices()
            } else {
                addLog(LogLevel.W, "AdbDaemon", "ADB binary not found — check Settings > ADB path", "system")
                _isAdbActive.value = false
            }
        }
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
        val result = runner.run(listOf("devices", "-l"))
        if (!result.success) {
            addLog(LogLevel.E, "DeviceTracker", "adb devices failed: ${result.output}", "system")
            return@withContext
        }
        val parsed = JvmAdbDeviceParser.parseDevicesOutput(result.output)
        val enriched = parsed.map { enrichDevice(it) }
        enriched.filter { it.connectionType == ConnectionType.WIFI }.forEach { device ->
            wirelessStore.updateFromDevice(device)
        }
        _devices.value = mergeWithSavedWirelessDevices(enriched)
        addLog(LogLevel.I, "DeviceTracker", "Discovered ${_devices.value.size} device(s)", "system")
    }

    private fun mergeWithSavedWirelessDevices(connected: List<Device>): List<Device> {
        val connectedSerials = connected.map { it.serial }.toSet()
        val offlineSaved = wirelessStore.load()
            .filter { saved -> saved.endpoint !in connectedSerials }
            .map { it.toOfflineDevice() }
        return connected + offlineSaved
    }

    override suspend fun pairWirelessDevice(ip: String, port: Int): Result<Device> = withContext(Dispatchers.IO) {
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
            wirelessStore.updateFromDevice(device)
            addLog(LogLevel.I, "AdbDaemon", "Wireless device paired: ${device.name}", device.id)
            Result.success(device)
        } else {
            Result.failure(IllegalStateException("Device not found after connect"))
        }
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
        online.forEach { device ->
            installApkOnDeviceInternal(device, apkFile)
        }
    }

    override suspend fun installApkOnDevice(deviceId: String, apkPath: String) = withContext(Dispatchers.IO) {
        val apkFile = resolveApkFile(apkPath) ?: run {
            addLog(LogLevel.E, "ApkInstaller", "APK file not found: $apkPath", deviceId)
            return@withContext
        }
        val device = _devices.value.find { it.id == deviceId } ?: run {
            addLog(LogLevel.E, "ApkInstaller", "Device not found: $deviceId", "system")
            return@withContext
        }
        if (device.status != DeviceStatus.ONLINE) {
            addLog(LogLevel.W, "ApkInstaller", "Device offline, cannot install: ${device.serial}", deviceId)
            return@withContext
        }
        addLog(LogLevel.I, "ApkInstaller", "Installing ${apkFile.name} on ${device.serial}...", deviceId)
        installApkOnDeviceInternal(device, apkFile)
    }

    private fun installApkOnDeviceInternal(device: Device, apkFile: File) {
        val result = runner.installApk(apkFile, device.serial)
        val output = result.output.ifBlank { "No output" }
        val level = when {
            result.success && ("Success" in output || "success" in output.lowercase()) -> LogLevel.I
            else -> LogLevel.E
        }
        if (!apkFile.extension.equals("apk", ignoreCase = true)) {
            addLog(LogLevel.I, "ApkInstaller", "Using push+pm install for non-.apk file: ${apkFile.name}", device.id)
        }
        addLog(level, "ApkInstaller", "${device.serial}: $output", device.id)
    }

    override suspend fun killAdbServer() = withContext(Dispatchers.IO) {
        runner.run(listOf("kill-server"))
        _isAdbActive.value = false
        _devices.value = emptyList()
        addLog(LogLevel.E, "AdbDaemon", "ADB server killed", "system")
    }

    override suspend fun restartAdbServer() = withContext(Dispatchers.IO) {
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
        _logs.update {
            it + AdbLog("log_${System.currentTimeMillis()}_${it.size}", ts, tag, level, message, deviceId)
        }
    }

    override fun clearLogs() {
        _logs.value = emptyList()
    }

    override suspend fun saveSettings(settings: AppSettings) {
        _settings.value = settings
        addLog(LogLevel.I, "Settings", "ADB path updated to: ${settings.adbPath}", "system")
        if (runner.isAvailable()) {
            _isAdbActive.value = true
            refreshDevices()
        }
    }

    override suspend fun runBatchAction(group: FilterTab, actionKey: String): List<String> = withContext(Dispatchers.IO) {
        val activeDevices = _devices.value.filter { device ->
            device.status == DeviceStatus.ONLINE && when (group) {
                FilterTab.PHYSICAL -> device.type == DeviceType.PHYSICAL
                FilterTab.EMULATORS -> device.type == DeviceType.EMULATOR
                FilterTab.ALL -> true
            }
        }
        if (activeDevices.isEmpty()) return@withContext emptyList()

        val lines = mutableListOf("Initiating batch: $actionKey on ${activeDevices.size} device(s)")
        activeDevices.forEach { device ->
            val result = when {
                actionKey.contains("reboot") -> runner.run(listOf("reboot"), serial = device.serial)
                actionKey.contains("battery") -> runner.run(listOf("shell", "dumpsys", "battery"), serial = device.serial)
                actionKey.contains("pm clear") -> runner.run(listOf("shell", "pm", "clear", "com.android.settings"), serial = device.serial)
                else -> runner.run(listOf("shell", "echo", "batch:$actionKey"), serial = device.serial)
            }
            val status = if (result.success) "OK" else "FAIL"
            lines += "[$status] ${device.serial}: $actionKey"
            addLog(if (result.success) LogLevel.I else LogLevel.E, "BatchExecutor", lines.last(), device.id)
        }
        lines += "Batch completed."
        lines
    }

    private fun enrichDevice(parsed: ParsedAdbDevice): Device {
        if (parsed.status != DeviceStatus.ONLINE) {
            return JvmAdbDeviceParser.toDevice(parsed)
        }
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
