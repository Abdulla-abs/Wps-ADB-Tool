package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DeviceStatus

object WirelessDeviceStatusReconciler {
    fun isWirelessTransport(serial: String): Boolean =
        DeviceTransportDeduplicator.isWirelessTlsSerial(serial) ||
            (':' in serial && !serial.startsWith("emulator-"))

    fun reconcileParsedStatus(
        parsed: ParsedAdbDevice,
        isUserDisconnected: Boolean,
        isReachable: (String) -> Boolean,
    ): ParsedAdbDevice {
        if (parsed.status != DeviceStatus.OFFLINE || isUserDisconnected) return parsed
        if (!isWirelessTransport(parsed.serial)) return parsed
        return if (isReachable(parsed.serial)) {
            parsed.copy(status = DeviceStatus.ONLINE)
        } else {
            parsed
        }
    }

    fun resolveSavedPlaceholderStatus(
        endpoint: String,
        isUserDisconnected: Boolean,
        isReachable: (String) -> Boolean,
    ): DeviceStatus {
        if (isUserDisconnected) return DeviceStatus.OFFLINE
        return if (isReachable(endpoint)) DeviceStatus.ONLINE else DeviceStatus.OFFLINE
    }
}
