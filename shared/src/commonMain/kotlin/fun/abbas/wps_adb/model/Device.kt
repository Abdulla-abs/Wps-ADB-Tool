package `fun`.abbas.wps_adb.model

import `fun`.abbas.wps_adb.model.ScreenFormFactor

data class DeviceApp(
    val name: String,
    val packageName: String,
    val iconKey: String,
)

data class Device(
    val id: String,
    val name: String,
    val serial: String,
    val type: DeviceType,
    val connectionType: ConnectionType,
    val status: DeviceStatus,
    val androidVersion: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val storageUsed: String,
    val storageTotal: String,
    val storagePercent: Int,
    val screenshotUrl: String,
    val screenDescription: String,
    val formFactor: ScreenFormFactor = ScreenFormFactor.PHONE,
    val screenWidthPx: Int = 0,
    val screenHeightPx: Int = 0,
    val apps: List<DeviceApp> = emptyList(),
    val currentAppIndex: Int = 0,
    val activityLog: List<String> = emptyList(),
)
