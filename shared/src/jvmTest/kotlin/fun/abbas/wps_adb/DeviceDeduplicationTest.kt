package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.DeviceTransportDeduplicator
import `fun`.abbas.wps_adb.data.JvmAdbDeviceParser
import `fun`.abbas.wps_adb.data.ParsedAdbDevice
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.ScreenFormFactor
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceDeduplicationTest {
    @Test
    fun deduplicate_prefersWifiTransportOverUsb() {
        val usb = sampleDevice("ABC123", ConnectionType.USB)
        val wifi = sampleDevice("192.168.1.105:41235", ConnectionType.WIFI)
        val hardwareSerialByTransport = mapOf(
            "ABC123" to "HW-SERIAL-001",
            "192.168.1.105:41235" to "HW-SERIAL-001",
        )

        val result = DeviceTransportDeduplicator.dedupeDevices(listOf(usb, wifi), hardwareSerialByTransport)

        assertEquals(1, result.size)
        assertEquals(ConnectionType.WIFI, result.single().connectionType)
        assertEquals("192.168.1.105:41235", result.single().serial)
    }

    @Test
    fun deduplicate_prefersIpPortOverMdnsTlsTransport() {
        val ipPort = sampleDevice("192.168.3.176:42523", ConnectionType.WIFI)
        val mdns = sampleDevice("adb-1943c766-2fwUSM._adb-tls-connect._tcp", ConnectionType.WIFI)
        val hardwareSerialByTransport = mapOf(
            "192.168.3.176:42523" to "HW-SERIAL-001",
            "adb-1943c766-2fwUSM._adb-tls-connect._tcp" to "HW-SERIAL-001",
        )

        val result = DeviceTransportDeduplicator.dedupeDevices(listOf(ipPort, mdns), hardwareSerialByTransport)

        assertEquals(1, result.size)
        assertEquals("192.168.3.176:42523", result.single().serial)
    }

    @Test
    fun deduplicate_keepsDistinctHardware() {
        val deviceA = sampleDevice("ABC123", ConnectionType.USB)
        val deviceB = sampleDevice("DEF456", ConnectionType.USB)
        val hardwareSerialByTransport = mapOf(
            "ABC123" to "HW-A",
            "DEF456" to "HW-B",
        )

        val result = DeviceTransportDeduplicator.dedupeDevices(listOf(deviceA, deviceB), hardwareSerialByTransport)

        assertEquals(2, result.size)
    }

    @Test
    fun dedupeParsedDevices_mergesDualTransportsBeforeEnrichment() {
        val parsed = listOf(
            ParsedAdbDevice(
                serial = "adb-1943c766-2fwUSM._adb-tls-connect._tcp",
                status = DeviceStatus.ONLINE,
                product = "RMX3823",
                model = "RMX3823",
                deviceName = "RE5CA6L1",
            ),
            ParsedAdbDevice(
                serial = "192.168.3.176:42523",
                status = DeviceStatus.ONLINE,
                product = "RMX3823",
                model = "RMX3823",
                deviceName = "RE5CA6L1",
            ),
        )

        val result = DeviceTransportDeduplicator.dedupeParsedDevices(parsed)

        assertEquals(1, result.size)
        assertEquals("192.168.3.176:42523", result.single().serial)
    }

    @Test
    fun toDevice_mapsWirelessTlsSerialAsWifi() {
        val parsed = ParsedAdbDevice(
            serial = "adb-1943c766-2fwUSM._adb-tls-connect._tcp",
            status = DeviceStatus.ONLINE,
            product = "RMX3823",
            model = "RMX3823",
            deviceName = "RE5CA6L1",
        )

        val device = JvmAdbDeviceParser.toDevice(parsed)

        assertEquals(ConnectionType.WIFI, device.connectionType)
    }

    private fun sampleDevice(serial: String, connectionType: ConnectionType): Device = Device(
        id = serial,
        name = "Test Phone",
        serial = serial,
        type = DeviceType.PHYSICAL,
        connectionType = connectionType,
        status = DeviceStatus.ONLINE,
        androidVersion = "Android 14",
        batteryLevel = 80,
        isCharging = false,
        storageUsed = "--",
        storageTotal = "--",
        storagePercent = 0,
        screenshotUrl = "",
        screenDescription = "Connected device",
        formFactor = ScreenFormFactor.PHONE,
    )
}
