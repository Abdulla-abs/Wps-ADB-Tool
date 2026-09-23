package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AdbProcessResult
import `fun`.abbas.wps_adb.data.DeviceTransportDeduplicator
import `fun`.abbas.wps_adb.data.JvmAdbRunner
import `fun`.abbas.wps_adb.data.JvmDeviceIdentityResolver
import `fun`.abbas.wps_adb.data.scene.DefaultSceneBindingResolver
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceIdentity
import `fun`.abbas.wps_adb.model.DeviceIdentitySource
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.scene.BindingStatus
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Validates hardware-backed stable identity resolution, USB/WiFi deduplication,
 * and SceneBinding persistence across offline/reconnect transitions.
 */
class DeviceIdentityResolutionTest {

    private class FakeAdbRunner(
        private val responses: Map<Pair<String, List<String>>, AdbProcessResult>,
    ) : JvmAdbRunner() {
        override fun run(args: List<String>, serial: String?): AdbProcessResult {
            val key = (serial ?: "") to args
            return responses[key] ?: AdbProcessResult(1, "command not found: $args")
        }
    }

    private fun createDevice(
        serial: String,
        connectionType: ConnectionType,
        identityValue: String,
        status: DeviceStatus = DeviceStatus.ONLINE,
    ): Device = Device(
        id = serial,
        name = "Test Hardware Device",
        serial = serial,
        type = DeviceType.PHYSICAL,
        connectionType = connectionType,
        status = status,
        androidVersion = "Android 14",
        batteryLevel = 90,
        isCharging = true,
        storageUsed = "--",
        storageTotal = "--",
        storagePercent = 0,
        screenshotUrl = "",
        screenDescription = "Test Device",
        identity = DeviceIdentity(
            value = identityValue,
            source = DeviceIdentitySource.RO_SERIALNO,
            rawHardwareSerial = identityValue,
        ),
    )

    @Test
    fun usbAndWifiEndpoints_resolveToSameHardwareSerial() {
        val runner = FakeAdbRunner(
            mapOf(
                ("USB_ABC_123" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(0, "HW-001\n"),
                ("192.168.1.100:5555" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(0, "HW-001\n"),
            ),
        )

        val usbIdentity = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "USB_ABC_123",
            isEmulator = false,
            deviceName = "pixel_8",
        )

        val wifiIdentity = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "192.168.1.100:5555",
            isEmulator = false,
            deviceName = "pixel_8",
        )

        assertEquals("HW-001", usbIdentity.value)
        assertEquals("HW-001", wifiIdentity.value)
        assertEquals(usbIdentity.toIdentityRef(), wifiIdentity.toIdentityRef())
    }

    @Test
    fun transportDeduplication_mergesUsbAndWifi_preservingStableIdentity() {
        val usbDevice = createDevice("USB_ABC_123", ConnectionType.USB, "HW-001")
        val wifiDevice = createDevice("192.168.1.100:5555", ConnectionType.WIFI, "HW-001")

        val deduped = DeviceTransportDeduplicator.dedupeDevices(listOf(usbDevice, wifiDevice))

        assertEquals(1, deduped.size)
        val active = deduped.single()
        assertEquals("HW-001", active.identity.value)
    }

    @Test
    fun sceneBinding_remainsValidAcrossUsbAndWifiSwitch() {
        val resolver = DefaultSceneBindingResolver()
        val scene = DeviceScene(
            id = "test_scene",
            name = "Test Scene",
            bindings = listOf(
                SceneBinding(
                    objectId = "phone_slot_01",
                    deviceIdentity = DeviceIdentityRef("HW-001"),
                )
            ),
        )

        // 1. Connected via USB
        val usbDevice = createDevice("USB_ABC_123", ConnectionType.USB, "HW-001")
        val resolvedUsb = resolver.resolve(scene, listOf(usbDevice))

        assertEquals(1, resolvedUsb.bindings.size)
        val bindingUsb = resolvedUsb.bindings.single()
        assertEquals(BindingStatus.ONLINE, bindingUsb.status)
        assertNotNull(bindingUsb.device)
        assertEquals("USB_ABC_123", bindingUsb.device?.serial)

        // 2. Unplugged USB, connected via WiFi
        val wifiDevice = createDevice("192.168.1.100:5555", ConnectionType.WIFI, "HW-001")
        val resolvedWifi = resolver.resolve(scene, listOf(wifiDevice))

        assertEquals(1, resolvedWifi.bindings.size)
        val bindingWifi = resolvedWifi.bindings.single()
        assertEquals(BindingStatus.ONLINE, bindingWifi.status)
        assertNotNull(bindingWifi.device)
        assertEquals("192.168.1.100:5555", bindingWifi.device?.serial)
        assertEquals("phone_slot_01", bindingWifi.binding.objectId)
    }

    @Test
    fun deviceOfflineAndReconnect_preservesBindingMapping() {
        val resolver = DefaultSceneBindingResolver()
        val scene = DeviceScene(
            id = "test_scene",
            name = "Test Scene",
            bindings = listOf(
                SceneBinding(
                    objectId = "phone_slot_01",
                    deviceIdentity = DeviceIdentityRef("HW-001"),
                )
            ),
        )

        // 1. Device is ONLINE
        val onlineDevice = createDevice("HW_DEV", ConnectionType.USB, "HW-001", DeviceStatus.ONLINE)
        val stateOnline = resolver.resolve(scene, listOf(onlineDevice))
        assertEquals(BindingStatus.ONLINE, stateOnline.bindings.single().status)

        // 2. Device drops OFFLINE (disconnected completely, empty device list)
        val stateOfflineEmpty = resolver.resolve(scene, emptyList())
        assertEquals(1, stateOfflineEmpty.bindings.size, "Binding mapping must not be dropped when device disconnects")
        val bindingOffline = stateOfflineEmpty.bindings.single()
        assertEquals("phone_slot_01", bindingOffline.binding.objectId)
        assertEquals("HW-001", bindingOffline.binding.deviceIdentity.value)
        assertEquals(BindingStatus.OFFLINE, bindingOffline.status)

        // 3. Device explicitly marked OFFLINE in adb devices list
        val offlineDevice = createDevice("HW_DEV", ConnectionType.USB, "HW-001", DeviceStatus.OFFLINE)
        val stateOfflineExplicit = resolver.resolve(scene, listOf(offlineDevice))
        assertEquals(BindingStatus.OFFLINE, stateOfflineExplicit.bindings.single().status)

        // 4. Device reconnects ONLINE
        val reconnectedDevice = createDevice("192.168.1.105:5555", ConnectionType.WIFI, "HW-001", DeviceStatus.ONLINE)
        val stateReconnected = resolver.resolve(scene, listOf(reconnectedDevice))
        val reconnectedBinding = stateReconnected.bindings.single()
        assertEquals(BindingStatus.ONLINE, reconnectedBinding.status)
        assertEquals("192.168.1.105:5555", reconnectedBinding.device?.serial)
    }
}
