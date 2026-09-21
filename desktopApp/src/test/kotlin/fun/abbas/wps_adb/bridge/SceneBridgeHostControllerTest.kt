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

        override fun markRendererReady() {
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
