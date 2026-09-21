package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.DefaultSceneBindingResolver
import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneVisualProjector
import `fun`.abbas.wps_adb.data.scene.bridge.VisualStatus
import `fun`.abbas.wps_adb.data.scene.bridge.toDescriptor
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceIdentity
import `fun`.abbas.wps_adb.model.DeviceIdentitySource
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.scene.BindingStatus
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.ResolvedBinding
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState
import `fun`.abbas.wps_adb.model.scene.SceneAssetInstance
import `fun`.abbas.wps_adb.model.scene.SceneBinding
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneVisualProjectorTest {

    private val projector = DefaultSceneVisualProjector()

    @Test
    fun projectBinding_onlineDevice_producesOnlineVisualDescriptorWithoutSerial() {
        val identity = DeviceIdentity("HW-12345", DeviceIdentitySource.RO_SERIALNO, "HW-12345")
        val device = createDevice(serial = "USB-001", name = "Pixel 8 Pro", identity = identity, status = DeviceStatus.ONLINE)
        val binding = ResolvedBinding(
            binding = SceneBinding("phone_slot_1", DeviceIdentityRef("HW-12345")),
            identity = identity,
            device = device,
            status = BindingStatus.ONLINE,
        )

        val descriptor = projector.projectBinding(binding)

        assertEquals("phone_slot_1", descriptor.objectId)
        assertEquals("HW-12345", descriptor.deviceIdentity)
        assertEquals("Pixel 8 Pro", descriptor.displayName)
        assertEquals(VisualStatus.ONLINE, descriptor.status)
        assertEquals("USB", descriptor.connectionType)
        assertEquals(DefaultSceneVisualProjector.COLOR_ONLINE, descriptor.visual.statusColorHex)
        assertTrue(descriptor.visual.isEmissive)
        assertEquals(0.8f, descriptor.visual.emissiveIntensity)
        assertEquals("ONLINE (USB)", descriptor.visual.badgeText)
        assertFalse(descriptor.visual.isDimmed)
    }

    @Test
    fun projectBinding_offlineDevice_producesOfflineVisualDescriptor() {
        val identity = DeviceIdentity("HW-67890", DeviceIdentitySource.RO_SERIALNO, "HW-67890")
        val device = createDevice(serial = "USB-002", name = "Galaxy S24", identity = identity, status = DeviceStatus.OFFLINE)
        val binding = ResolvedBinding(
            binding = SceneBinding("phone_slot_2", DeviceIdentityRef("HW-67890")),
            identity = identity,
            device = device,
            status = BindingStatus.OFFLINE,
        )

        val descriptor = projector.projectBinding(binding)

        assertEquals("phone_slot_2", descriptor.objectId)
        assertEquals("HW-67890", descriptor.deviceIdentity)
        assertEquals("Galaxy S24", descriptor.displayName)
        assertEquals(VisualStatus.OFFLINE, descriptor.status)
        assertEquals(DefaultSceneVisualProjector.COLOR_OFFLINE, descriptor.visual.statusColorHex)
        assertFalse(descriptor.visual.isEmissive)
        assertEquals("OFFLINE", descriptor.visual.badgeText)
        assertTrue(descriptor.visual.isDimmed)
    }

    @Test
    fun projectBinding_missingDevice_producesOfflineWithPlaceholderName() {
        val identity = DeviceIdentity("HW-MISSING", DeviceIdentitySource.RO_SERIALNO, "HW-MISSING")
        val binding = ResolvedBinding(
            binding = SceneBinding("phone_slot_3", DeviceIdentityRef("HW-MISSING")),
            identity = identity,
            device = null,
            status = BindingStatus.OFFLINE,
        )

        val descriptor = projector.projectBinding(binding)

        assertEquals("phone_slot_3", descriptor.objectId)
        assertEquals("HW-MISSING", descriptor.deviceIdentity)
        assertEquals("phone_slot_3", descriptor.displayName)
        assertEquals(VisualStatus.OFFLINE, descriptor.status)
        assertTrue(descriptor.visual.isDimmed)
    }

    @Test
    fun fullChain_missingDevice_projectsAsOfflineNotUnbound() {
        val resolver = DefaultSceneBindingResolver()
        val scene = DeviceScene(
            id = "chain_scene_1",
            name = "Chain Scene",
            bindings = listOf(
                SceneBinding("slot_1", DeviceIdentityRef("HW-OFFLINE-01")),
            ),
        )

        // Resolver runs with empty physical devices -> device is null, identity is null, status is OFFLINE
        val resolvedState = resolver.resolve(scene, emptyList())
        assertEquals(1, resolvedState.bindings.size)
        val resolvedBinding = resolvedState.bindings.first()
        assertEquals(BindingStatus.OFFLINE, resolvedBinding.status)
        assertNull(resolvedBinding.device)
        assertNull(resolvedBinding.identity)

        // Full chain projection: must project as OFFLINE, retaining deviceIdentity, NOT UNBOUND
        val snapshot = projector.project(resolvedState)
        assertEquals(1, snapshot.devices.size)
        val descriptor = snapshot.devices.first()
        assertEquals("slot_1", descriptor.objectId)
        assertEquals("HW-OFFLINE-01", descriptor.deviceIdentity)
        assertEquals(VisualStatus.OFFLINE, descriptor.status)
        assertEquals(DefaultSceneVisualProjector.COLOR_OFFLINE, descriptor.visual.statusColorHex)
        assertTrue(descriptor.visual.isDimmed)
    }

    @Test
    fun fullChain_emptyIdentityBinding_projectsAsUnbound() {
        val resolver = DefaultSceneBindingResolver()
        val scene = DeviceScene(
            id = "chain_scene_2",
            name = "Chain Scene 2",
            bindings = listOf(
                SceneBinding("slot_unbound", DeviceIdentityRef("")),
            ),
        )

        val resolvedState = resolver.resolve(scene, emptyList())
        val snapshot = projector.project(resolvedState)
        assertEquals(1, snapshot.devices.size)
        val descriptor = snapshot.devices.first()
        assertEquals("slot_unbound", descriptor.objectId)
        assertNull(descriptor.deviceIdentity)
        assertEquals(VisualStatus.UNBOUND, descriptor.status)
        assertEquals(DefaultSceneVisualProjector.COLOR_UNBOUND, descriptor.visual.statusColorHex)
        assertEquals("UNBOUND", descriptor.visual.badgeText)
        assertFalse(descriptor.visual.isDimmed)
    }

    @Test
    fun project_fullSnapshot_retainsSceneIdAndDecoupledSelection() {
        val identity = DeviceIdentity("HW-A", DeviceIdentitySource.RO_SERIALNO, "HW-A")
        val device = createDevice(serial = "DEV-A", name = "Device A", identity = identity, status = DeviceStatus.ONLINE)
        val scene = DeviceScene(
            id = "test_scene_101",
            name = "Test Scene",
            bindings = listOf(SceneBinding("slot_a", DeviceIdentityRef("HW-A"))),
        )
        val state = ResolvedSceneState(
            scene = scene,
            bindings = listOf(
                ResolvedBinding(
                    binding = SceneBinding("slot_a", DeviceIdentityRef("HW-A")),
                    identity = identity,
                    device = device,
                    status = BindingStatus.ONLINE,
                ),
            ),
        )

        val snapshot = projector.project(state, selectedObjectId = "slot_a")

        assertEquals("test_scene_101", snapshot.sceneId)
        assertEquals("slot_a", snapshot.selectedObjectId)
        assertEquals(1, snapshot.devices.size)
        assertEquals("slot_a", snapshot.devices.single().objectId)
    }

    @Test
    fun toDescriptor_convertsDeviceSceneWithoutPersistencePollution() {
        val scene = DeviceScene(
            id = "lab_room",
            name = "Lab Room",
            camera = SceneCamera(
                position = SceneVector3(1.0, 2.0, 3.0),
                target = SceneVector3(0.0, 0.0, 0.0),
                fov = 60.0,
            ),
            assets = listOf(
                SceneAssetInstance(
                    id = "desk_asset",
                    fileName = "desk.glb",
                    name = "Desk",
                    transform = SceneTransform(
                        position = SceneVector3(0.0, 0.0, 0.0),
                        rotation = SceneVector3(0.0, 0.0, 0.0),
                        scale = SceneVector3(1.0, 1.0, 1.0),
                    ),
                ),
            ),
            bindings = listOf(SceneBinding("obj_phone", DeviceIdentityRef("HW-XYZ"))),
        )

        val descriptor = scene.toDescriptor()

        assertEquals("lab_room", descriptor.id)
        assertEquals("Lab Room", descriptor.name)
        assertNull(descriptor.environmentFileName)
        assertEquals(SceneVector3(1.0, 2.0, 3.0), descriptor.camera.position)
        assertEquals(60.0, descriptor.camera.fov)
        assertEquals(1, descriptor.assets.size)
        assertEquals("desk_asset", descriptor.assets.first().id)
        assertEquals(listOf("obj_phone"), descriptor.bindableObjectIds)
    }

    private fun createDevice(serial: String, name: String, identity: DeviceIdentity, status: DeviceStatus): Device = Device(
        id = serial,
        name = name,
        serial = serial,
        type = DeviceType.PHYSICAL,
        connectionType = ConnectionType.USB,
        status = status,
        androidVersion = "Android 14",
        batteryLevel = 100,
        isCharging = false,
        storageUsed = "--",
        storageTotal = "--",
        storagePercent = 0,
        screenshotUrl = "",
        screenDescription = "Screen",
        identity = identity,
    )
}
