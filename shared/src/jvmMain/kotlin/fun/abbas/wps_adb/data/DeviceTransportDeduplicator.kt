package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceStatus

object DeviceTransportDeduplicator {
    fun isWirelessTlsSerial(serial: String): Boolean =
        serial.startsWith("adb-") && "_adb-tls-" in serial

    fun resolveConnectionType(serial: String, isEmulator: Boolean): ConnectionType = when {
        isEmulator -> ConnectionType.EMULATOR
        ':' in serial || isWirelessTlsSerial(serial) -> ConnectionType.WIFI
        else -> ConnectionType.USB
    }

    fun dedupeParsedDevices(parsed: List<ParsedAdbDevice>): List<ParsedAdbDevice> {
        val (online, other) = parsed.partition { it.status == DeviceStatus.ONLINE }
        if (online.size < 2) return parsed

        val dedupedOnline = online
            .groupBy(::parsedIdentityKey)
            .values
            .flatMap { group -> if (group.size <= 1) group else listOf(selectPreferredParsed(group)) }

        return dedupedOnline + other
    }

    fun dedupeDevices(
        devices: List<Device>,
        hardwareSerialByTransport: Map<String, String>,
    ): List<Device> {
        val online = devices.filter { it.status == DeviceStatus.ONLINE }
        if (online.size < 2) return devices

        val groups = online.groupBy { hardwareSerialByTransport[it.serial] ?: it.serial }
        val toRemove = mutableSetOf<String>()
        for ((_, group) in groups) {
            if (group.size <= 1) continue
            val keeper = selectPreferredDevice(group)
            group.filter { it.serial != keeper.serial }.forEach { toRemove.add(it.serial) }
        }
        return devices.filterNot { it.serial in toRemove }
    }

    internal fun transportRank(serial: String, connectionType: ConnectionType): Int = when {
        connectionType == ConnectionType.WIFI && ':' in serial -> 0
        connectionType == ConnectionType.WIFI -> 1
        connectionType == ConnectionType.USB && !isWirelessTlsSerial(serial) -> 2
        else -> 3
    }

    private fun parsedIdentityKey(parsed: ParsedAdbDevice): String {
        parsed.deviceName?.takeIf { it.isNotBlank() }?.let { return "device:$it" }
        parsed.model?.takeIf { it.isNotBlank() }?.let { return "model:$it" }
        return "serial:${parsed.serial}"
    }

    private fun selectPreferredParsed(group: List<ParsedAdbDevice>): ParsedAdbDevice =
        group.minWith(
            compareBy<ParsedAdbDevice> { transportRank(it.serial, resolveConnectionType(it.serial, false)) }
                .thenBy { it.serial },
        )

    private fun selectPreferredDevice(group: List<Device>): Device =
        group.minWith(
            compareBy<Device> { transportRank(it.serial, it.connectionType) }
                .thenBy { it.serial },
        )
}
