package `fun`.abbas.wps_adb.bridge

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
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
class RendererReadySyncTest {

    private class FakeSceneBridgeChannel : SceneBridgeChannel {
        private val _state = MutableStateFlow(BridgeConnectionState.CONNECTED)
        override val state: StateFlow<BridgeConnectionState> = _state.asStateFlow()

        private val _incoming = MutableSharedFlow<SceneBridgeMessage>(extraBufferCapacity = 64)
        override val incoming: Flow<SceneBridgeMessage> = _incoming.asSharedFlow()

        val sentMessages = mutableListOf<SceneBridgeMessage>()

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
    fun beforeReady_multipleUpdates_sendZeroMessages_andOnReady_sendsOnlyLatestSyncState() = runTest(UnconfinedTestDispatcher()) {
        val channel = FakeSceneBridgeChannel()
        val controller = SceneBridgeHostController(channel = channel, scope = backgroundScope)

        // Multiple rapid state updates before renderer is READY
        val stateA = createResolvedState("scene_main", "State A Device")
        val stateB = createResolvedState("scene_main", "State B Device")
        val stateC = createResolvedState("scene_main", "State C Device (Final)")

        controller.onSceneStateChanged(stateA)
        controller.onSceneStateChanged(stateB)
        controller.onSceneStateChanged(stateC)

        // Verifies: before READY, state update = no message sent
        assertTrue(channel.sentMessages.isEmpty(), "Expected 0 messages sent before renderer ready, got ${channel.sentMessages.size}")

        // Now renderer signals READY
        channel.markRendererReady()

        // Verifies: exactly one InitScene and one SyncState for latest state (State C)
        assertEquals(2, channel.sentMessages.size)

        assertIs<SceneBridgeMessage.InitScene>(channel.sentMessages[0])
        assertEquals("scene_main", (channel.sentMessages[0] as SceneBridgeMessage.InitScene).sceneDescriptor.id)

        assertIs<SceneBridgeMessage.SyncState>(channel.sentMessages[1])
        val snapshot = (channel.sentMessages[1] as SceneBridgeMessage.SyncState).snapshot
        assertEquals("State C Device (Final)", snapshot.devices.first().displayName)
    }

    private fun createResolvedState(sceneId: String, deviceName: String): ResolvedSceneState {
        val scene = DeviceScene(
            id = sceneId,
            name = "Main Lab",
            camera = SceneCamera(SceneVector3.ZERO, SceneVector3.ZERO, 45.0),
            assets = emptyList(),
            bindings = listOf(SceneBinding("slot_primary", DeviceIdentityRef("ID-$deviceName"))),
        )
        val identity = DeviceIdentity("ID-$deviceName", DeviceIdentitySource.RO_SERIALNO, "ID-$deviceName")
        val device = Device(
            id = "dev_id",
            name = deviceName,
            serial = "SER-01",
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
            binding = SceneBinding("slot_primary", DeviceIdentityRef("ID-$deviceName")),
            identity = identity,
            device = device,
            status = BindingStatus.ONLINE,
        )
        return ResolvedSceneState(scene = scene, bindings = listOf(binding))
    }
}
