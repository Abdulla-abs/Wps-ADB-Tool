package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.ApkInstallResult
import `fun`.abbas.wps_adb.model.ApkMetadata
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.BatchActionParams
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.QrPairingEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AdbRepository {
    val devices: StateFlow<List<Device>>
    val logs: StateFlow<List<AdbLog>>
    val logcatLogs: StateFlow<List<AdbLog>>
    val isAdbActive: StateFlow<Boolean>
    val isScanningDevices: StateFlow<Boolean>
    val settings: StateFlow<AppSettings>

    suspend fun refreshDevices()
    suspend fun pairWirelessDevice(ip: String, port: Int): Result<Device>
    fun pairWirelessViaQr(): Flow<QrPairingEvent>
    fun cancelQrPairing()
    suspend fun rebootDevice(deviceId: String)
    suspend fun disconnectDevice(deviceId: String)
    suspend fun reconnectDevice(deviceId: String)
    suspend fun removeDevice(deviceId: String)
    suspend fun installApk(fileName: String)
    suspend fun installApkOnDevice(deviceId: String, apkPath: String): ApkInstallResult
    suspend fun parseApkMetadata(apkPath: String): ApkMetadata?
    suspend fun launchApp(
        deviceId: String,
        packageName: String,
        launchActivity: String? = null,
    ): Result<Unit>
    suspend fun uninstallApp(deviceId: String, packageName: String): Result<Unit>
    fun startAppLogcat(deviceId: String, packageName: String, tabId: String): Flow<AdbLog>
    fun stopAppLogcat(tabId: String)
    fun stopAllAppLogcatSessions()
    fun startGlobalLogcat(deviceId: String? = null)
    fun stopGlobalLogcat()
    fun clearLogcatLogs()
    suspend fun killAdbServer()
    suspend fun restartAdbServer()
    fun addLog(level: LogLevel, tag: String, message: String, deviceId: String? = null)
    fun clearLogs()
    suspend fun saveSettings(settings: AppSettings)
    suspend fun runBatchAction(
        group: FilterTab,
        actionKey: String,
        params: BatchActionParams = BatchActionParams(),
    ): List<String>
}
