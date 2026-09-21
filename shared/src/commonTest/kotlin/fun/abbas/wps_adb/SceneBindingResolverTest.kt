package `fun`.abbas.wps_adb

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
import kotlin.test.assertNull

class SceneBindingResolverTest {

    private val resolver = DefaultSceneBindingResolver()

    @Test
    fun resolve_onlineMatch() {
        val binding = SceneBinding(
            objectId = "desk_phone_01",
            deviceIdentity = DeviceIdentityRef("HW-PIXEL-8"),
        )
        val scene = DeviceScene(
            id = "test_scene",
            name = "Test Scene",
            bindings = listOf(binding),
        )
        val device = createTestDevice(
            serial = "ABC12345",
            identityValue = "HW-PIXEL-8",
            status = DeviceStatus.ONLINE,
        )

        val resolved = resolver.resolve(scene, listOf(device))

        assertEquals(scene, resolved.scene)
        assertEquals(1, resolved.bindings.size)
        val resolvedBinding = resolved.bindings.single()
        assertEquals(binding, resolvedBinding.binding)
        assertEquals(BindingStatus.ONLINE, resolvedBinding.status)
        assertNotNull(resolvedBinding.device)
        assertEquals("ABC12345", resolvedBinding.device.serial)
        assertEquals("HW-PIXEL-8", resolvedBinding.identity?.value)
        assertEquals(DeviceIdentitySource.RO_SERIALNO, resolvedBinding.identity?.source)
    }

    @Test
    fun resolve_missingDevice_isOffline() {
        val binding = SceneBinding(
            objectId = "desk_phone_02",
            deviceIdentity = DeviceIdentityRef("HW-MISSING"),
        )
        val scene = DeviceScene(
            id = "test_scene",
            name = "Test Scene",
            bindings = listOf(binding),
        )

        val resolved = resolver.resolve(scene, emptyList())

        assertEquals(1, resolved.bindings.size)
        val resolvedBinding = resolved.bindings.single()
        assertEquals(binding, resolvedBinding.binding)
        assertEquals(BindingStatus.OFFLINE, resolvedBinding.status)
        assertNull(resolvedBinding.device)
        assertNull(resolvedBinding.identity)
    }

    @Test
    fun resolve_offlineMatchedDevice_isOffline() {
        val binding = SceneBinding(
            objectId = "wall_tablet_01",
            deviceIdentity = DeviceIdentityRef("HW-GALAXY-TAB"),
        )
        val scene = DeviceScene(
            id = "test_scene",
            name = "Test Scene",
            bindings = listOf(binding),
        )
        val offlineDevice = createTestDevice(
            serial = "TAB999",
            identityValue = "HW-GALAXY-TAB",
            status = DeviceStatus.OFFLINE,
        )

        val resolved = resolver.resolve(scene, listOf(offlineDevice))

        assertEquals(1, resolved.bindings.size)
        val resolvedBinding = resolved.bindings.single()
        assertEquals(BindingStatus.OFFLINE, resolvedBinding.status)
        assertNotNull(resolvedBinding.device)
        assertEquals(DeviceStatus.OFFLINE, resolvedBinding.device.status)
        assertEquals("HW-GALAXY-TAB", resolvedBinding.identity?.value)
    }

    @Test
    fun resolve_multipleBindings_resolvesCorrectStatuses() {
        val bOnline = SceneBinding("obj_1", DeviceIdentityRef("HW-1"))
        val bMissing = SceneBinding("obj_2", DeviceIdentityRef("HW-2"))
        val bOffline = SceneBinding("obj_3", DeviceIdentityRef("HW-3"))

        val scene = DeviceScene(
            id = "test_scene",
            name = "Test Scene",
            bindings = listOf(bOnline, bMissing, bOffline),
        )

        val devices = listOf(
            createTestDevice("DEV-1", "HW-1", DeviceStatus.ONLINE),
            createTestDevice("DEV-3", "HW-3", DeviceStatus.OFFLINE),
        )

        val resolved = resolver.resolve(scene, devices)

        assertEquals(3, resolved.bindings.size)
        assertEquals(BindingStatus.ONLINE, resolved.bindings[0].status)
        assertEquals(BindingStatus.OFFLINE, resolved.bindings[1].status)
        assertNull(resolved.bindings[1].device)
        assertEquals(BindingStatus.OFFLINE, resolved.bindings[2].status)
        assertNotNull(resolved.bindings[2].device)
    }

    @Test
    fun resolve_identityMismatch_isOffline() {
        val binding = SceneBinding("obj_1", DeviceIdentityRef("TARGET-HW"))
        val scene = DeviceScene(
            id = "test_scene",
            name = "Test Scene",
            bindings = listOf(binding),
        )
        val unrelatedDevices = listOf(
            createTestDevice("DEV-A", "OTHER-HW-A", DeviceStatus.ONLINE),
            createTestDevice("DEV-B", "OTHER-HW-B", DeviceStatus.ONLINE),
        )

        val resolved = resolver.resolve(scene, unrelatedDevices)

        assertEquals(1, resolved.bindings.size)
        val resolvedBinding = resolved.bindings.single()
        assertEquals(BindingStatus.OFFLINE, resolvedBinding.status)
        assertNull(resolvedBinding.device)
    }

    @Test
    fun resolve_preservesBindingOrder() {
        val b1 = SceneBinding("obj_alpha", DeviceIdentityRef("ID-1"))
        val b2 = SceneBinding("obj_beta", DeviceIdentityRef("ID-2"))
        val b3 = SceneBinding("obj_gamma", DeviceIdentityRef("ID-3"))
        val b4 = SceneBinding("obj_delta", DeviceIdentityRef("ID-4"))

        val scene = DeviceScene(
            id = "scene_order",
            name = "Order Test Scene",
            bindings = listOf(b1, b2, b3, b4),
        )

        // Runtime devices in completely different order
        val devices = listOf(
            createTestDevice("DEV-4", "ID-4", DeviceStatus.ONLINE),
            createTestDevice("DEV-2", "ID-2", DeviceStatus.OFFLINE),
            createTestDevice("DEV-1", "ID-1", DeviceStatus.ONLINE),
        )

        val resolved = resolver.resolve(scene, devices)

        assertEquals(4, resolved.bindings.size)
        assertEquals("obj_alpha", resolved.bindings[0].binding.objectId)
        assertEquals(BindingStatus.ONLINE, resolved.bindings[0].status)

        assertEquals("obj_beta", resolved.bindings[1].binding.objectId)
        assertEquals(BindingStatus.OFFLINE, resolved.bindings[1].status)

        assertEquals("obj_gamma", resolved.bindings[2].binding.objectId)
        assertEquals(BindingStatus.OFFLINE, resolved.bindings[2].status)

        assertEquals("obj_delta", resolved.bindings[3].binding.objectId)
        assertEquals(BindingStatus.ONLINE, resolved.bindings[3].status)
    }

    @Test
    fun resolve_emptyBindings_returnsEmptyResolvedBindings() {
        val scene = DeviceScene(
            id = "empty_scene",
            name = "Empty Scene",
            bindings = emptyList(),
        )
        val devices = listOf(
            createTestDevice("DEV-1", "HW-1", DeviceStatus.ONLINE),
        )

        val resolved = resolver.resolve(scene, devices)

        assertEquals(scene, resolved.scene)
        assertEquals(0, resolved.bindings.size)
    }

    private fun createTestDevice(
        serial: String,
        identityValue: String,
        status: DeviceStatus,
    ): Device = Device(
        id = serial,
        name = "Test Device $serial",
        serial = serial,
        type = DeviceType.PHYSICAL,
        connectionType = ConnectionType.USB,
        status = status,
        androidVersion = "Android 14",
        batteryLevel = 85,
        isCharging = false,
        storageUsed = "--",
        storageTotal = "--",
        storagePercent = 0,
        screenshotUrl = "",
        screenDescription = "Test Screen",
        identity = DeviceIdentity(
            value = identityValue,
            source = DeviceIdentitySource.RO_SERIALNO,
            rawHardwareSerial = identityValue,
        ),
    )
}
