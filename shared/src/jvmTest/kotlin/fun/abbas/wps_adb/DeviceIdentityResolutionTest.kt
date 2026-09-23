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
import `fun`.abbas.wps_adb.data.scene.SceneStore
import `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun realSceneStoreAndController_preservesBindingAcrossUsbWifiAndOfflineTransitions() = runTest(UnconfinedTestDispatcher()) {
        val tempDir = Files.createTempDirectory("scene_store_identity_test").toFile()
        try {
            val store = SceneStore(scenesRoot = tempDir)
            val scene = store.createScene("Office Desk", "office_desk")
            val identityRef = DeviceIdentityRef("HW-STABLE-XYZ")

            // Bind device identity to phone slot and verify disk write
            store.bindDevice(scene.id, "slot_phone_01", identityRef)

            // Re-load directly from disk to verify true file persistence
            val reloaded = store.loadScene(scene.id)
            assertEquals(1, reloaded.bindings.size)
            assertEquals("slot_phone_01", reloaded.bindings.single().objectId)
            assertEquals("HW-STABLE-XYZ", reloaded.bindings.single().deviceIdentity.value)

            val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
            val controller = DefaultSceneRuntimeController(
                devicesFlow = devicesFlow,
                scope = backgroundScope,
            )
            controller.setScene(reloaded)
            advanceUntilIdle()

            // Step 1: Initial state (no devices online) -> slot is OFFLINE
            val state0 = controller.resolvedState.value
            assertNotNull(state0)
            assertEquals(1, state0.bindings.size)
            assertEquals(BindingStatus.OFFLINE, state0.bindings.single().status)

            // Step 2: Physical device connects via USB
            val usbDevice = createDevice("USB_PHONE_1", ConnectionType.USB, "HW-STABLE-XYZ", DeviceStatus.ONLINE)
            devicesFlow.value = listOf(usbDevice)
            advanceUntilIdle()

            val stateUsb = controller.resolvedState.value
            assertNotNull(stateUsb)
            val bindingUsb = stateUsb.bindings.single()
            assertEquals(BindingStatus.ONLINE, bindingUsb.status)
            assertEquals("USB_PHONE_1", bindingUsb.device?.serial)
            assertEquals(ConnectionType.USB, bindingUsb.device?.connectionType)

            // Step 3: Unplug USB, connects via WiFi (different serial endpoint, same stable identity)
            val wifiDevice = createDevice("192.168.1.120:5555", ConnectionType.WIFI, "HW-STABLE-XYZ", DeviceStatus.ONLINE)
            devicesFlow.value = listOf(wifiDevice)
            advanceUntilIdle()

            val stateWifi = controller.resolvedState.value
            assertNotNull(stateWifi)
            val bindingWifi = stateWifi.bindings.single()
            assertEquals(BindingStatus.ONLINE, bindingWifi.status)
            assertEquals("192.168.1.120:5555", bindingWifi.device?.serial)
            assertEquals(ConnectionType.WIFI, bindingWifi.device?.connectionType)
            assertEquals("slot_phone_01", bindingWifi.binding.objectId)

            // Step 4: Device disconnects (WiFi drops)
            devicesFlow.value = emptyList()
            advanceUntilIdle()

            val stateOffline = controller.resolvedState.value
            assertNotNull(stateOffline)
            val bindingOffline = stateOffline.bindings.single()
            assertEquals(BindingStatus.OFFLINE, bindingOffline.status)
            assertEquals("slot_phone_01", bindingOffline.binding.objectId)
            assertEquals("HW-STABLE-XYZ", bindingOffline.binding.deviceIdentity.value)

            // Step 5: Device reconnects via WiFi
            devicesFlow.value = listOf(wifiDevice)
            advanceUntilIdle()

            val stateRestored = controller.resolvedState.value
            assertNotNull(stateRestored)
            assertEquals(BindingStatus.ONLINE, stateRestored.bindings.single().status)
            assertEquals("192.168.1.120:5555", stateRestored.bindings.single().device?.serial)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun noHardwareSerial_fallbackRangeVerification_maintainsTransportStability() {
        val runner = FakeAdbRunner(
            mapOf(
                ("USB_STRIPPED_1" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(0, "unknown\n"),
                ("USB_STRIPPED_1" to listOf("shell", "getprop", "ro.boot.serialno")) to AdbProcessResult(0, "\n"),
                ("192.168.1.150:5555" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(0, ""),
                ("192.168.1.150:5555" to listOf("shell", "getprop", "ro.boot.serialno")) to AdbProcessResult(1, "permission denied\n"),
            ),
        )

        // Case A: When deviceName is present (e.g. product model "custom_tablet_pro")
        // Both USB and WiFi resolve to the same stable TRANSPORT_FALLBACK identity!
        val usbWithModel = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "USB_STRIPPED_1",
            isEmulator = false,
            deviceName = "custom_tablet_pro",
        )
        val wifiWithModel = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "192.168.1.150:5555",
            isEmulator = false,
            deviceName = "custom_tablet_pro",
        )
        assertEquals(DeviceIdentitySource.TRANSPORT_FALLBACK, usbWithModel.source)
        assertEquals(DeviceIdentitySource.TRANSPORT_FALLBACK, wifiWithModel.source)
        assertEquals("custom_tablet_pro", usbWithModel.value)
        assertEquals("custom_tablet_pro", wifiWithModel.value)
        assertEquals(usbWithModel.toIdentityRef(), wifiWithModel.toIdentityRef())

        // Case B: When deviceName is missing or blank, fallback strictly scopes to transport serial
        val usbNoModel = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "USB_STRIPPED_1",
            isEmulator = false,
            deviceName = null,
        )
        assertEquals(DeviceIdentitySource.TRANSPORT_FALLBACK, usbNoModel.source)
        assertEquals("USB_STRIPPED_1", usbNoModel.value)
    }

    @Test
    fun emulatorAvdFallback_resistsDynamicPortNumberChanges() {
        val runner = FakeAdbRunner(
            mapOf(
                ("emulator-5554" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(1, ""),
                ("emulator-5554" to listOf("shell", "getprop", "ro.boot.serialno")) to AdbProcessResult(1, ""),
                ("emulator-5554" to listOf("emu", "avd", "name")) to AdbProcessResult(0, "Pixel_Fold_API_35\nOK\n"),
                ("emulator-5556" to listOf("shell", "getprop", "ro.serialno")) to AdbProcessResult(1, ""),
                ("emulator-5556" to listOf("shell", "getprop", "ro.boot.serialno")) to AdbProcessResult(1, ""),
                ("emulator-5556" to listOf("emu", "avd", "name")) to AdbProcessResult(0, "Pixel_Fold_API_35\nOK\n"),
            ),
        )

        // When emulator runs on port 5554
        val id5554 = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "emulator-5554",
            isEmulator = true,
            deviceName = "Android SDK built for x86",
        )

        // When emulator restarts and gets port 5556
        val id5556 = JvmDeviceIdentityResolver.resolveIdentity(
            runner = runner,
            serial = "emulator-5556",
            isEmulator = true,
            deviceName = "Android SDK built for x86",
        )

        assertEquals("Pixel_Fold_API_35", id5554.value)
        assertEquals("Pixel_Fold_API_35", id5556.value)
        assertEquals(DeviceIdentitySource.EMULATOR_FALLBACK, id5554.source)
        assertEquals(id5554.toIdentityRef(), id5556.toIdentityRef())
    }
}
