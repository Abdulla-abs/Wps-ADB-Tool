package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.ScreenFormFactor

data class SavedWirelessDevice(
    val host: String,
    val port: Int,
    val name: String = "",
    val formFactor: ScreenFormFactor = ScreenFormFactor.PHONE,
    val screenWidthPx: Int = 0,
    val screenHeightPx: Int = 0,
) {
    val endpoint: String get() = "$host:$port"

    fun toOfflineDevice(): Device = Device(
        id = endpoint,
        name = name.ifBlank { endpoint },
        serial = endpoint,
        type = DeviceType.PHYSICAL,
        connectionType = ConnectionType.WIFI,
        status = DeviceStatus.OFFLINE,
        androidVersion = "Android",
        batteryLevel = 0,
        isCharging = false,
        storageUsed = "--",
        storageTotal = "--",
        storagePercent = 0,
        screenshotUrl = "",
        screenDescription = "Saved wireless device",
        formFactor = formFactor,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        activityLog = emptyList(),
    )
}
