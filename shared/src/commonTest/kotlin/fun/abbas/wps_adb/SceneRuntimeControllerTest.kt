package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.SceneBindingResolver
import `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceIdentity
import `fun`.abbas.wps_adb.model.DeviceIdentitySource
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.scene.BindingStatus
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState
import `fun`.abbas.wps_adb.model.scene.SceneBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SceneRuntimeControllerTest {

    @Test
    fun initialState_hasNullActiveSceneAndNullResolvedState() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )

        assertNull(controller.activeScene.value)
        assertNull(controller.resolvedState.value)
    }

    @Test
    fun setScene_activatesSceneAndResolvesImmediately() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val device = createDevice("DEV-1", "HW-PIXEL-8", DeviceStatus.ONLINE)
        devicesFlow.value = listOf(device)

        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )

        val scene = DeviceScene(
            id = "scene_alpha",
            name = "Scene Alpha",
            bindings = listOf(SceneBinding("obj_1", DeviceIdentityRef("HW-PIXEL-8"))),
        )
        controller.setScene(scene)
        advanceUntilIdle()

        assertEquals(scene, controller.activeScene.value)
        val resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals(scene, resolved.scene)
        assertEquals(1, resolved.bindings.size)
        assertEquals(BindingStatus.ONLINE, resolved.bindings.single().status)
    }

    @Test
    fun deviceOnlineTransition_updatesBindingToOnline() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )

        val scene = DeviceScene(
            id = "scene_1",
            name = "Scene 1",
            bindings = listOf(SceneBinding("desk_phone", DeviceIdentityRef("HW-100"))),
        )
        controller.setScene(scene)
        advanceUntilIdle()

        // Initially no device in ADB -> OFFLINE
        var resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals(BindingStatus.OFFLINE, resolved.bindings.single().status)

        // Device connects and goes online
        devicesFlow.value = listOf(createDevice("DEV-100", "HW-100", DeviceStatus.ONLINE))
        advanceUntilIdle()

        resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals(BindingStatus.ONLINE, resolved.bindings.single().status)
        assertEquals("DEV-100", resolved.bindings.single().device?.serial)
    }

    @Test
    fun deviceOfflineTransition_updatesBindingToOffline() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val device = createDevice("DEV-200", "HW-200", DeviceStatus.ONLINE)
        devicesFlow.value = listOf(device)

        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )

        val scene = DeviceScene(
            id = "scene_2",
            name = "Scene 2",
            bindings = listOf(SceneBinding("phone_2", DeviceIdentityRef("HW-200"))),
        )
        controller.setScene(scene)
        advanceUntilIdle()

        var resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals(BindingStatus.ONLINE, resolved.bindings.single().status)

        // Device drops to OFFLINE status
        devicesFlow.value = listOf(createDevice("DEV-200", "HW-200", DeviceStatus.OFFLINE))
        advanceUntilIdle()

        resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals(BindingStatus.OFFLINE, resolved.bindings.single().status)
        assertEquals(DeviceStatus.OFFLINE, resolved.bindings.single().device?.status)

        // Device completely disconnected from adb
        devicesFlow.value = emptyList()
        advanceUntilIdle()

        resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals(BindingStatus.OFFLINE, resolved.bindings.single().status)
        assertNull(resolved.bindings.single().device)
    }

    @Test
    fun sceneSwitch_reResolvesToNewSceneBindings() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val devA = createDevice("A", "HW-A", DeviceStatus.ONLINE)
        val devB = createDevice("B", "HW-B", DeviceStatus.ONLINE)
        devicesFlow.value = listOf(devA, devB)

        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )

        val sceneA = DeviceScene(
            id = "scene_A",
            name = "Scene A",
            bindings = listOf(SceneBinding("obj_a", DeviceIdentityRef("HW-A"))),
        )
        val sceneB = DeviceScene(
            id = "scene_B",
            name = "Scene B",
            bindings = listOf(SceneBinding("obj_b", DeviceIdentityRef("HW-B"))),
        )

        controller.setScene(sceneA)
        advanceUntilIdle()

        var resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals("scene_A", resolved.scene.id)
        assertEquals("obj_a", resolved.bindings.single().binding.objectId)

        // Switch to Scene B
        controller.setScene(sceneB)
        advanceUntilIdle()

        resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals("scene_B", resolved.scene.id)
        assertEquals("obj_b", resolved.bindings.single().binding.objectId)
    }

    @Test
    fun setSceneNull_clearsResolvedState() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )
        controller.setScene(
            DeviceScene(id = "s", name = "S", bindings = listOf(SceneBinding("o", DeviceIdentityRef("id")))),
        )
        advanceUntilIdle()
        assertNotNull(controller.resolvedState.value)

        controller.setScene(null)
        advanceUntilIdle()
        assertNull(controller.activeScene.value)
        assertNull(controller.resolvedState.value)
    }

    @Test
    fun ordering_isPreservedAcrossReactiveUpdates() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val b1 = SceneBinding("obj_1", DeviceIdentityRef("ID-1"))
        val b2 = SceneBinding("obj_2", DeviceIdentityRef("ID-2"))
        val b3 = SceneBinding("obj_3", DeviceIdentityRef("ID-3"))

        val scene = DeviceScene(
            id = "ordered_scene",
            name = "Ordered Scene",
            bindings = listOf(b1, b2, b3),
        )

        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )
        controller.setScene(scene)
        advanceUntilIdle()

        // Emit devices in reverse order
        devicesFlow.value = listOf(
            createDevice("D3", "ID-3", DeviceStatus.ONLINE),
            createDevice("D1", "ID-1", DeviceStatus.ONLINE),
            createDevice("D2", "ID-2", DeviceStatus.ONLINE),
        )
        advanceUntilIdle()

        val resolved = controller.resolvedState.value
        assertNotNull(resolved)
        assertEquals(3, resolved.bindings.size)
        assertEquals("obj_1", resolved.bindings[0].binding.objectId)
        assertEquals("obj_2", resolved.bindings[1].binding.objectId)
        assertEquals("obj_3", resolved.bindings[2].binding.objectId)
    }

    @Test
    fun resolverErrorIsolation_doesNotCrashFlow() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val faultyResolver = object : SceneBindingResolver {
            var shouldThrow = true
            override fun resolve(scene: DeviceScene, devices: List<Device>): ResolvedSceneState {
                if (shouldThrow) {
                    throw IllegalStateException("Simulated binding resolution failure")
                }
                return ResolvedSceneState(scene, emptyList())
            }
        }

        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            resolver = faultyResolver,
            scope = backgroundScope,
        )

        val scene = DeviceScene(id = "faulty_scene", name = "Faulty Scene", bindings = listOf(SceneBinding("x", DeviceIdentityRef("y"))))
        controller.setScene(scene)
        advanceUntilIdle()

        // Even though resolver threw, controller isolated error and returned safe fallback
        val fallback = controller.resolvedState.value
        assertNotNull(fallback)
        assertEquals(scene, fallback.scene)
        assertTrue(fallback.bindings.isEmpty())

        // Recovery: faultyResolver stops throwing, next emission succeeds normally
        faultyResolver.shouldThrow = false
        devicesFlow.value = listOf(createDevice("D", "ID", DeviceStatus.ONLINE))
        advanceUntilIdle()

        val recovered = controller.resolvedState.value
        assertNotNull(recovered)
        assertEquals(scene, recovered.scene)
    }

    @Test
    fun selectObject_updatesSelectedObjectIdAndClearsOnSetScene() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val controller = DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )

        assertNull(controller.selectedObjectId.value)

        // Select an object
        controller.selectObject("phone_mesh_1")
        assertEquals("phone_mesh_1", controller.selectedObjectId.value)

        // Select another object
        controller.selectObject("phone_mesh_2")
        assertEquals("phone_mesh_2", controller.selectedObjectId.value)

        // Deselect
        controller.selectObject(null)
        assertNull(controller.selectedObjectId.value)

        // Re-select then switch scene -> selection is reset to null
        controller.selectObject("phone_mesh_3")
        assertEquals("phone_mesh_3", controller.selectedObjectId.value)

        val newScene = DeviceScene(id = "scene_new", name = "New Scene")
        controller.setScene(newScene)
        assertNull(controller.selectedObjectId.value, "Switching scene must clear selectedObjectId")
    }


    private fun createDevice(serial: String, identity: String, status: DeviceStatus): Device = Device(
        id = serial,
        name = "Device $serial",
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
        identity = DeviceIdentity(
            value = identity,
            source = DeviceIdentitySource.RO_SERIALNO,
            rawHardwareSerial = identity,
        ),
    )
}
