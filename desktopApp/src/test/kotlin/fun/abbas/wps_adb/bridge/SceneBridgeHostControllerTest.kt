package `fun`.abbas.wps_adb.bridge

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneVisualProjector
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeChannel
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeMessage
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
import `fun`.abbas.wps_adb.model.scene.SceneBinding
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneAssetInstance
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SceneBridgeHostControllerTest {

    private class FakeSceneBridgeChannel(
        initialState: BridgeConnectionState = BridgeConnectionState.DISCONNECTED
    ) : SceneBridgeChannel {
        private val _state = MutableStateFlow(initialState)
        override val state: StateFlow<BridgeConnectionState> = _state.asStateFlow()

        private val _incoming = MutableSharedFlow<SceneBridgeMessage>(extraBufferCapacity = 64)
        override val incoming: Flow<SceneBridgeMessage> = _incoming.asSharedFlow()

        val sentMessages = mutableListOf<SceneBridgeMessage>()

        fun setConnectionState(newState: BridgeConnectionState) {
            _state.value = newState
        }

        fun markRendererReady() {
            _state.value = BridgeConnectionState.READY
        }

        override suspend fun send(message: SceneBridgeMessage) {
            sentMessages.add(message)
        }

        override suspend fun connect() {
            _state.value = BridgeConnectionState.CONNECTED
        }

        override suspend fun disconnect() {
            _state.value = BridgeConnectionState.DISCONNECTED
        }

        suspend fun emitIncoming(message: SceneBridgeMessage) {
            _incoming.emit(message)
        }
    }

    @Test
    fun case1_rendererNotReady_stateUpdate_doesNotSend() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.CONNECTED)
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        val stateA = createResolvedState(sceneId = "scene_1", deviceName = "Pixel 8")
        controller.onSceneStateChanged(stateA)

        // No message must be sent before READY
        assertTrue(channel.sentMessages.isEmpty())
    }

    @Test
    fun case2_rendererReady_sendsInitSceneAndSyncState() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.CONNECTED)
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        val stateA = createResolvedState(sceneId = "scene_1", deviceName = "Pixel 8")
        controller.onSceneStateChanged(stateA)
        assertTrue(channel.sentMessages.isEmpty())

        // Transition to READY
        channel.markRendererReady()

        assertEquals(2, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.InitScene>(channel.sentMessages[0])
        assertEquals("scene_1", (channel.sentMessages[0] as SceneBridgeMessage.InitScene).sceneDescriptor.id)

        assertIs<SceneBridgeMessage.SyncState>(channel.sentMessages[1])
        val snapshot = (channel.sentMessages[1] as SceneBridgeMessage.SyncState).snapshot
        assertEquals("scene_1", snapshot.sceneId)
        assertEquals("Pixel 8", snapshot.devices.first().displayName)
    }

    @Test
    fun case3_stateUpdateWhileReady_sendsSyncStateSequentially() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.READY)
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        // First state while ready
        val stateA = createResolvedState(sceneId = "scene_1", deviceName = "Pixel 8")
        controller.onSceneStateChanged(stateA)

        // InitScene + SyncState A
        assertEquals(2, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.InitScene>(channel.sentMessages[0])
        assertIs<SceneBridgeMessage.SyncState>(channel.sentMessages[1])
        assertEquals("Pixel 8", (channel.sentMessages[1] as SceneBridgeMessage.SyncState).snapshot.devices.first().displayName)

        // Second state while ready (same scene ID)
        val stateB = createResolvedState(sceneId = "scene_1", deviceName = "Galaxy S24")
        controller.onSceneStateChanged(stateB)

        // Only SyncState B is appended (no extra InitScene for same scene)
        assertEquals(3, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.SyncState>(channel.sentMessages[2])
        assertEquals("Galaxy S24", (channel.sentMessages[2] as SceneBridgeMessage.SyncState).snapshot.devices.first().displayName)
    }

    @Test
    fun assetContentChange_reinitializesRenderer_butTransformOnlyChangeDoesNot() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.READY)
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        val initial = createResolvedState(sceneId = "scene_1", deviceName = "Pixel 8")
        controller.onSceneStateChanged(initial)
        assertEquals(2, channel.sentMessages.size)

        val importedAsset = SceneAssetInstance(
            id = "asset_phone",
            fileName = "assets/asset_phone-demo.glb",
            name = "Demo Phone",
        )
        val withAsset = initial.copy(scene = initial.scene.copy(assets = listOf(importedAsset)))
        controller.onSceneStateChanged(withAsset)

        assertEquals(4, channel.sentMessages.size)
        val initMessage = channel.sentMessages[2] as SceneBridgeMessage.InitScene
        assertEquals("asset_phone", initMessage.sceneDescriptor.assets.single().id)
        assertIs<SceneBridgeMessage.SyncState>(channel.sentMessages[3])

        val movedAsset = importedAsset.copy(
            transform = SceneTransform(position = SceneVector3(1.0, 0.0, 0.0)),
        )
        controller.onSceneStateChanged(withAsset.copy(scene = withAsset.scene.copy(assets = listOf(movedAsset))))

        assertEquals(5, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.SyncState>(channel.sentMessages[4])
    }

    @Test
    fun selectObject_whenReady_sendsSelectionChange() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.READY)
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        controller.selectObject("slot_pixel", focusCamera = true)

        assertEquals(1, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.SelectionChange>(channel.sentMessages.first())
        val update = channel.sentMessages.first() as SceneBridgeMessage.SelectionChange
        assertEquals("slot_pixel", update.selectedObjectId)
        assertTrue(update.focusCamera)
    }

    @Test
    fun sceneSwitch_sendsInitSceneForNewScene() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.READY)
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        val state1 = createResolvedState(sceneId = "lab_1", deviceName = "Device 1")
        controller.onSceneStateChanged(state1)
        assertEquals(2, channel.sentMessages.size)
        assertEquals("lab_1", (channel.sentMessages[0] as SceneBridgeMessage.InitScene).sceneDescriptor.id)

        val state2 = createResolvedState(sceneId = "lab_2", deviceName = "Device 2")
        controller.onSceneStateChanged(state2)

        // Switching scenes triggers InitScene for lab_2 followed by SyncState
        assertEquals(4, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.InitScene>(channel.sentMessages[2])
        assertEquals("lab_2", (channel.sentMessages[2] as SceneBridgeMessage.InitScene).sceneDescriptor.id)
        assertIs<SceneBridgeMessage.SyncState>(channel.sentMessages[3])
        assertEquals("lab_2", (channel.sentMessages[3] as SceneBridgeMessage.SyncState).snapshot.sceneId)
    }

    @Test
    fun cameraChanged_fromChannel_updatesRuntimeController() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.READY)
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val runtimeController = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )
        controller.bind(runtimeController)

        val camMsg = SceneBridgeMessage.CameraChanged(
            position = SceneVector3(5.0, 10.0, 15.0),
            target = SceneVector3(1.0, 2.0, 3.0),
            fov = 55.0,
        )
        channel.emitIncoming(camMsg)

        val currentCam = runtimeController.runtimeCamera.value
        kotlin.test.assertNotNull(currentCam)
        assertEquals(SceneVector3(5.0, 10.0, 15.0), currentCam.position)
        assertEquals(SceneVector3(1.0, 2.0, 3.0), currentCam.target)
        assertEquals(55.0, currentCam.fov)
    }

    @Test
    fun resetCamera_whenReady_sendsCameraCommand() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.READY)
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        val targetCam = SceneCamera(SceneVector3(1.0, 2.0, 3.0), SceneVector3.ZERO, 45.0)
        controller.resetCamera(targetCam)

        assertEquals(1, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.CameraCommand>(channel.sentMessages[0])
        val cmd = channel.sentMessages[0] as SceneBridgeMessage.CameraCommand
        assertEquals(targetCam.position, cmd.position)
        assertEquals(targetCam.target, cmd.target)
        assertEquals(targetCam.fov, cmd.fov)
    }

    @Test
    fun eventValidator_whenFalse_preventsRuntimeControllerMutation() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.READY)
        var validatorCalled = false
        val controller = SceneBridgeHostController(
            channel = channel,
            scope = backgroundScope,
            eventValidator = { _, _ ->
                validatorCalled = true
                false // Reject all incoming camera/transform events
            },
        )

        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        val runtimeController = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
            devicesFlow = devicesFlow,
            scope = backgroundScope,
        )
        controller.bind(runtimeController)

        val initialAsset = SceneAssetInstance(
            id = "asset_1",
            fileName = "phone.glb",
            name = "Phone",
            transform = SceneTransform(SceneVector3.ZERO, SceneVector3.ZERO, SceneVector3(1.0, 1.0, 1.0)),
        )
        val initialScene = DeviceScene(
            id = "scene_1",
            name = "Scene 1",
            camera = SceneCamera(),
            assets = listOf(initialAsset),
        )
        runtimeController.setScene(initialScene)

        val camMsg = SceneBridgeMessage.CameraChanged(
            position = SceneVector3(55.0, 66.0, 77.0),
            target = SceneVector3(1.0, 2.0, 3.0),
            fov = 60.0,
        )
        channel.emitIncoming(camMsg)

        assertTrue(validatorCalled)
        // Camera in runtimeController must remain untouched at initialScene.camera
        assertEquals(initialScene.camera, runtimeController.runtimeCamera.value)

        // Test ObjectTransformChanged rejection
        val transformMsg = SceneBridgeMessage.ObjectTransformChanged(
            objectId = "asset_1",
            position = SceneVector3(10.0, 20.0, 30.0),
            rotation = SceneVector3.ZERO,
            scale = SceneVector3(1.0, 1.0, 1.0),
        )
        channel.emitIncoming(transformMsg)

        val assetAfter = runtimeController.activeScene.value?.assets?.find { it.id == "asset_1" }
        assertEquals(SceneVector3.ZERO, assetAfter?.transform?.position)
    }

    @Test
    fun syncInitialScene_attachesEpochFromEpochProvider() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel(BridgeConnectionState.READY)
        var currentEpoch = 42L
        val controller = SceneBridgeHostController(
            channel = channel,
            scope = backgroundScope,
            epochProvider = { currentEpoch },
        )

        val state = createResolvedState("scene_test", "Pixel 7")
        controller.onSceneStateChanged(state)

        assertEquals(2, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.InitScene>(channel.sentMessages[0])
        val initMsg = channel.sentMessages[0] as SceneBridgeMessage.InitScene
        assertEquals("scene_test", initMsg.sceneDescriptor.id)
        assertEquals(42L, initMsg.epoch)

        // If epoch changes, onSceneStateChanged resends InitScene with the new epoch
        currentEpoch = 43L
        controller.onSceneStateChanged(state)
        assertEquals(4, channel.sentMessages.size)
        assertIs<SceneBridgeMessage.InitScene>(channel.sentMessages[2])
        val nextInitMsg = channel.sentMessages[2] as SceneBridgeMessage.InitScene
        assertEquals(43L, nextInitMsg.epoch)
    }

    private fun createResolvedState(sceneId: String, deviceName: String): ResolvedSceneState {
        val scene = DeviceScene(
            id = sceneId,
            name = "Test Scene $sceneId",
            camera = SceneCamera(SceneVector3.ZERO, SceneVector3.ZERO, 45.0),
            assets = emptyList(),
            bindings = listOf(SceneBinding("slot_0", DeviceIdentityRef("ID-$sceneId"))),
        )
        val identity = DeviceIdentity("ID-$sceneId", DeviceIdentitySource.RO_SERIALNO, "ID-$sceneId")
        val device = Device(
            id = "dev_1",
            name = deviceName,
            serial = "SERIAL-1",
            type = DeviceType.PHYSICAL,
            connectionType = ConnectionType.USB,
            status = DeviceStatus.ONLINE,
            androidVersion = "14",
            batteryLevel = 100,
            isCharging = false,
            storageUsed = "",
            storageTotal = "",
            storagePercent = 0,
            screenshotUrl = "",
            screenDescription = "",
            identity = identity,
        )
        val binding = ResolvedBinding(
            binding = SceneBinding("slot_0", DeviceIdentityRef("ID-$sceneId")),
            identity = identity,
            device = device,
            status = BindingStatus.ONLINE,
        )
        return ResolvedSceneState(scene = scene, bindings = listOf(binding))
    }
}
