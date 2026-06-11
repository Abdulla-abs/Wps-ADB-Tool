package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.ScreenFormFactor

data class ParsedAdbDevice(
    val serial: String,
    val status: DeviceStatus,
    val product: String?,
    val model: String?,
    val deviceName: String? = null,
)

object JvmAdbDeviceParser {
    private val lineRegex = Regex("""^(\S+)\s+(device|offline|unauthorized)(?:\s+(.*))?$""")

    fun parseDevicesOutput(output: String): List<ParsedAdbDevice> {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("List of devices") }
            .mapNotNull { line ->
                val match = lineRegex.matchEntire(line) ?: return@mapNotNull null
                val (serial, state, extras) = match.destructured
                ParsedAdbDevice(
                    serial = serial,
                    status = when (state) {
                        "device" -> DeviceStatus.ONLINE
                        "offline" -> DeviceStatus.OFFLINE
                        else -> DeviceStatus.UNAUTHORIZED
                    },
                    product = extractField(extras, "product"),
                    model = extractField(extras, "model"),
                    deviceName = extractField(extras, "device"),
                )
            }
            .toList()
    }

    fun toDevice(
        parsed: ParsedAdbDevice,
        androidVersion: String = "",
        batteryLevel: Int = 0,
        screenshotUrl: String = "",
        formFactor: ScreenFormFactor = ScreenFormFactor.UNKNOWN,
        screenWidthPx: Int = 0,
        screenHeightPx: Int = 0,
    ): Device {
        val isEmulator = parsed.serial.startsWith("emulator-") ||
            parsed.model?.contains("sdk", ignoreCase = true) == true ||
            parsed.product?.contains("sdk", ignoreCase = true) == true
        val connectionType = DeviceTransportDeduplicator.resolveConnectionType(parsed.serial, isEmulator)
        val displayName = parsed.model?.replace('_', ' ') ?: parsed.product ?: parsed.serial
        return Device(
            id = parsed.serial,
            name = displayName,
            serial = parsed.serial,
            type = if (isEmulator) DeviceType.EMULATOR else DeviceType.PHYSICAL,
            connectionType = connectionType,
            status = parsed.status,
            androidVersion = androidVersion.ifBlank { "Android" },
            batteryLevel = batteryLevel,
            isCharging = false,
            storageUsed = "--",
            storageTotal = "--",
            storagePercent = 0,
            screenshotUrl = screenshotUrl,
            screenDescription = if (parsed.status == DeviceStatus.ONLINE) "Connected device" else "Device unavailable",
            formFactor = formFactor,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            activityLog = emptyList(),
        )
    }

    private fun extractField(extras: String, key: String): String? {
        val regex = Regex("""$key:([^\s]+)""")
        return regex.find(extras)?.groupValues?.getOrNull(1)
    }
}
