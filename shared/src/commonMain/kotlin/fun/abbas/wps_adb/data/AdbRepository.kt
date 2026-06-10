package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.LogLevel
import kotlinx.coroutines.flow.StateFlow

interface AdbRepository {
    val devices: StateFlow<List<Device>>
    val logs: StateFlow<List<AdbLog>>
    val isAdbActive: StateFlow<Boolean>
    val settings: StateFlow<AppSettings>

    suspend fun refreshDevices()
    suspend fun pairWirelessDevice(ip: String, port: Int): Result<Device>
    suspend fun rebootDevice(deviceId: String)
    suspend fun disconnectDevice(deviceId: String)
    suspend fun reconnectDevice(deviceId: String)
    suspend fun installApk(fileName: String)
    suspend fun installApkOnDevice(deviceId: String, apkPath: String)
    suspend fun killAdbServer()
    suspend fun restartAdbServer()
    fun addLog(level: LogLevel, tag: String, message: String, deviceId: String? = null)
    fun clearLogs()
    suspend fun saveSettings(settings: AppSettings)
    suspend fun runBatchAction(group: FilterTab, actionKey: String): List<String>
}
