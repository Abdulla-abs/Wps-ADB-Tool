package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmAdbDeviceParser
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmAdbDeviceParserTest {
    @Test
    fun parseDevicesOutput_parsesMultipleDevices() {
        val output = """
            List of devices attached
            emulator-5554          device product:sdk model:sdk_gphone64 device:emu64xa transport_id:1
            2201117PG              device product:redfin model:Pixel_5 device:redfin transport_id:2
            192.168.1.104:5555     offline product:oriole model:Pixel_6 device:oriole transport_id:3
            OP721110               unauthorized
        """.trimIndent()

        val parsed = JvmAdbDeviceParser.parseDevicesOutput(output)
        assertEquals(4, parsed.size)
        assertEquals("emulator-5554", parsed[0].serial)
        assertEquals(DeviceStatus.ONLINE, parsed[0].status)
        assertEquals("Pixel_5", parsed[1].model)
        assertEquals(DeviceStatus.OFFLINE, parsed[2].status)
        assertEquals(DeviceStatus.UNAUTHORIZED, parsed[3].status)
    }

    @Test
    fun toDevice_mapsConnectionTypes() {
        val emulator = JvmAdbDeviceParser.toDevice(
            JvmAdbDeviceParser.parseDevicesOutput("emulator-5554 device product:sdk model:sdk").first(),
            "Android 14",
            100,
        )
        assertEquals(DeviceType.EMULATOR, emulator.type)
        assertEquals(ConnectionType.EMULATOR, emulator.connectionType)

        val wifi = JvmAdbDeviceParser.toDevice(
            JvmAdbDeviceParser.parseDevicesOutput("192.168.1.10:5555 device product:p model:m").first(),
            "Android 13",
            80,
        )
        assertEquals(ConnectionType.WIFI, wifi.connectionType)

        val usb = JvmAdbDeviceParser.toDevice(
            JvmAdbDeviceParser.parseDevicesOutput("ABC123 device product:p model:Phone").first(),
            "Android 12",
            50,
        )
        assertEquals(ConnectionType.USB, usb.connectionType)
        assertTrue(usb.name.contains("Phone"))

        val wirelessTls = JvmAdbDeviceParser.toDevice(
            JvmAdbDeviceParser.parseDevicesOutput(
                "adb-1943c766-2fwUSM._adb-tls-connect._tcp device product:RMX3823 model:RMX3823",
            ).first(),
            "Android 14",
            90,
        )
        assertEquals(ConnectionType.WIFI, wirelessTls.connectionType)
    }
}
