package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AdbProcessResult
import `fun`.abbas.wps_adb.data.DeviceTransportDeduplicator
import `fun`.abbas.wps_adb.data.JvmAdbDeviceParser
import `fun`.abbas.wps_adb.data.JvmAdbRunner
import `fun`.abbas.wps_adb.data.JvmDeviceIdentityResolver
import `fun`.abbas.wps_adb.data.MockData
import `fun`.abbas.wps_adb.data.ParsedAdbDevice
import `fun`.abbas.wps_adb.data.SavedWirelessDevice
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceIdentity
import `fun`.abbas.wps_adb.model.DeviceIdentitySource
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceIdentityTest {

    private class FakeAdbRunner(
        private val responses: Map<Pair<String, List<String>>, AdbProcessResult>,
    ) : JvmAdbRunner() {
        override fun run(args: List<String>, serial: String?): AdbProcessResult {
            val key = (serial ?: "") to args
            return responses[key] ?: AdbProcessResult(1, "command not found: $args")
        }
    }

    @Test
    fun resolveIdentity_prefersRoSerialnoForUsbDevice() {
        val runner = FakeAdbRunner(
            mapOf(
                ("ABC12345" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(0, "HW-SERIAL-001\n"),
            ),
        )

        val identity = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "ABC12345",
            isEmulator = false,
            deviceName = "pixel_phone",
        )

        assertEquals("HW-SERIAL-001", identity.value)
        assertEquals(DeviceIdentitySource.RO_SERIALNO, identity.source)
        assertEquals("HW-SERIAL-001", identity.rawHardwareSerial)
        assertEquals("HW-SERIAL-001", identity.toIdentityRef().value)
    }

    @Test
    fun resolveIdentity_fallsBackToRoBootSerialnoWhenRoSerialnoUnknown() {
        val runner = FakeAdbRunner(
            mapOf(
                ("ABC12345" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(0, "unknown\n"),
                ("ABC12345" to listOf("shell", "getprop", "ro.boot.serialno")) to AdbProcessResult(0, "BOOT-SERIAL-999\n"),
            ),
        )

        val identity = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "ABC12345",
            isEmulator = false,
            deviceName = "pixel_phone",
        )

        assertEquals("BOOT-SERIAL-999", identity.value)
        assertEquals(DeviceIdentitySource.RO_BOOT_SERIALNO, identity.source)
        assertEquals("BOOT-SERIAL-999", identity.rawHardwareSerial)
    }

    @Test
    fun resolveIdentity_fallsBackToAvdNameForEmulator() {
        val runner = FakeAdbRunner(
            mapOf(
                ("emulator-5554" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(1, ""),
                ("emulator-5554" to listOf("shell", "getprop", "ro.boot.serialno")) to AdbProcessResult(1, ""),
                ("emulator-5554" to listOf("emu", "avd", "name")) to AdbProcessResult(0, "Pixel_7_API_34\nOK\n"),
            ),
        )

        val identity = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "emulator-5554",
            isEmulator = true,
            deviceName = "emulator",
        )

        assertEquals("Pixel_7_API_34", identity.value)
        assertEquals(DeviceIdentitySource.EMULATOR_FALLBACK, identity.source)
    }

    @Test
    fun resolveIdentity_fallsBackToTransportWhenNoHardwareOrAvd() {
        val runner = FakeAdbRunner(emptyMap())

        val identity = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "MY_FALLBACK_DEVICE",
            isEmulator = false,
            deviceName = "my_custom_device",
        )

        assertEquals("my_custom_device", identity.value)
        assertEquals(DeviceIdentitySource.TRANSPORT_FALLBACK, identity.source)
    }

    @Test
    fun wifiReconnect_maintainsSameStableIdentityAsUsb() {
        val usbDevice = sampleDevice(
            serial = "ABC12345",
            connectionType = ConnectionType.USB,
            identity = DeviceIdentity(
                value = "STABLE-HW-SERIAL",
                source = DeviceIdentitySource.RO_SERIALNO,
                rawHardwareSerial = "STABLE-HW-SERIAL",
            ),
        )
        val wifiDevice = sampleDevice(
            serial = "192.168.1.100:5555",
            connectionType = ConnectionType.WIFI,
            identity = DeviceIdentity(
                value = "STABLE-HW-SERIAL",
                source = DeviceIdentitySource.RO_SERIALNO,
                rawHardwareSerial = "STABLE-HW-SERIAL",
            ),
        )

        assertEquals(usbDevice.identity, wifiDevice.identity)
        assertEquals(usbDevice.identity.toIdentityRef(), wifiDevice.identity.toIdentityRef())

        val deduped = DeviceTransportDeduplicator.dedupeDevices(listOf(usbDevice, wifiDevice))
        assertEquals(1, deduped.size)
        assertEquals("192.168.1.100:5555", deduped.single().serial)
        assertEquals("STABLE-HW-SERIAL", deduped.single().identity.value)
    }

    @Test
    fun offlineDevice_preservesIdentity() {
        val originalIdentity = DeviceIdentity(
            value = "OFFLINE-STABLE-HW",
            source = DeviceIdentitySource.RO_SERIALNO,
            rawHardwareSerial = "OFFLINE-STABLE-HW",
        )

        val parsed = ParsedAdbDevice(
            serial = "192.168.0.50:5555",
            status = DeviceStatus.OFFLINE,
            product = "test_product",
            model = "test_model",
        )
        val device = JvmAdbDeviceParser.toDevice(parsed, identity = originalIdentity)

        assertEquals(DeviceStatus.OFFLINE, device.status)
        assertEquals("OFFLINE-STABLE-HW", device.identity.value)
        assertEquals(DeviceIdentitySource.RO_SERIALNO, device.identity.source)

        val savedWireless = SavedWirelessDevice(host = "192.168.0.50", port = 5555, name = "My Wireless Phone")
        val offlineFromSaved = savedWireless.toOfflineDevice(originalIdentity)
        assertEquals("OFFLINE-STABLE-HW", offlineFromSaved.identity.value)
        assertEquals(DeviceIdentitySource.RO_SERIALNO, offlineFromSaved.identity.source)
    }

    @Test
    fun mockData_containsStableIdentities() {
        assertTrue(MockData.initialDevices.isNotEmpty())
        for (device in MockData.initialDevices) {
            assertNotNull(device.identity)
            assertTrue(device.identity.value.isNotBlank(), "Device ${device.id} identity must not be blank")
        }
    }

    private fun sampleDevice(
        serial: String,
        connectionType: ConnectionType,
        identity: DeviceIdentity,
    ): Device = Device(
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
        identity = identity,
    )
}
