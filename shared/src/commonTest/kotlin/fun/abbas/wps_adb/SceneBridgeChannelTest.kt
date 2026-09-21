package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeTransport
import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneBridgeChannel
import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneBridgeSerializer
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeMessage
import `fun`.abbas.wps_adb.data.scene.bridge.SceneCameraDescriptor
import `fun`.abbas.wps_adb.data.scene.bridge.SceneDescriptor
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SceneBridgeChannelTest {

    private class FakeBridgeTransport : BridgeTransport {
        val incomingFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
        override val incoming: Flow<String> = incomingFlow

        val sentPayloads = mutableListOf<String>()
        var isConnected = false
        var isClosed = false
        var shouldFailConnect = false
        var shouldFailSend = false

        override suspend fun connect() {
            if (shouldFailConnect) throw IllegalStateException("Transport connect failure")
            isConnected = true
        }

        override suspend fun send(payload: String) {
            if (shouldFailSend) throw IllegalStateException("Transport send failure")
            sentPayloads.add(payload)
        }

        override suspend fun close() {
            isClosed = true
            isConnected = false
        }

        suspend fun emitIncoming(raw: String) {
            incomingFlow.emit(raw)
        }
    }

    @Test
    fun initialState_isDisconnected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val channel = DefaultSceneBridgeChannel(transport = transport, scope = backgroundScope)

        assertEquals(BridgeConnectionState.DISCONNECTED, channel.state.value)
    }

    @Test
    fun connect_transitionsToConnectedAndStartsListening() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val channel = DefaultSceneBridgeChannel(transport = transport, scope = backgroundScope)

        channel.connect()

        assertEquals(BridgeConnectionState.CONNECTED, channel.state.value)
        assertTrue(transport.isConnected)
    }

    @Test
    fun rendererReady_transitionsStateToReady() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val serializer = DefaultSceneBridgeSerializer()
        val channel = DefaultSceneBridgeChannel(transport = transport, serializer = serializer, scope = backgroundScope)

        channel.connect()
        assertEquals(BridgeConnectionState.CONNECTED, channel.state.value)

        val readyMsg = SceneBridgeMessage.RendererReady(
            protocolVersion = 1,
            rendererVersion = "1.0",
        )
        transport.emitIncoming(serializer.serialize(readyMsg))

        assertEquals(BridgeConnectionState.READY, channel.state.value)
    }

    @Test
    fun markRendererReady_transitionsStateToReadyDirectly() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val channel = DefaultSceneBridgeChannel(transport = transport, scope = backgroundScope)

        channel.connect()
        assertEquals(BridgeConnectionState.CONNECTED, channel.state.value)

        channel.markRendererReady()
        assertEquals(BridgeConnectionState.READY, channel.state.value)
    }

    @Test
    fun send_whenNotReady_queuesMessageAndFlushesOnceReady() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val serializer = DefaultSceneBridgeSerializer()
        val channel = DefaultSceneBridgeChannel(transport = transport, serializer = serializer, scope = backgroundScope)

        channel.connect()
        assertEquals(BridgeConnectionState.CONNECTED, channel.state.value)

        // Send while in CONNECTED (not yet READY)
        val initMsg = SceneBridgeMessage.InitScene(
            sceneDescriptor = SceneDescriptor(
                id = "lab_1",
                name = "Lab 1",
                environmentFileName = null,
                camera = SceneCameraDescriptor(SceneVector3.ZERO, SceneVector3.ZERO, 45.0),
                assets = emptyList(),
                bindableObjectIds = emptyList(),
            ),
        )
        channel.send(initMsg)

        // Must NOT have transmitted yet
        assertTrue(transport.sentPayloads.isEmpty())

        // Now Renderer becomes ready
        val readyMsg = SceneBridgeMessage.RendererReady(protocolVersion = 1, rendererVersion = "1.0")
        transport.emitIncoming(serializer.serialize(readyMsg))

        // State moves to READY and queued message is immediately flushed
        assertEquals(BridgeConnectionState.READY, channel.state.value)
        assertEquals(1, transport.sentPayloads.size)
        assertTrue(transport.sentPayloads.first().contains("\"type\":\"SCENE_INIT\""))
        assertTrue(transport.sentPayloads.first().contains("\"id\":\"lab_1\""))
    }

    @Test
    fun send_whenReady_transmitsImmediately() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val serializer = DefaultSceneBridgeSerializer()
        val channel = DefaultSceneBridgeChannel(transport = transport, serializer = serializer, scope = backgroundScope)

        channel.connect()
        transport.emitIncoming(serializer.serialize(SceneBridgeMessage.RendererReady(protocolVersion = 1, rendererVersion = "1.0")))
        assertEquals(BridgeConnectionState.READY, channel.state.value)

        val selectMsg = SceneBridgeMessage.SelectionChange("slot_phone", focusCamera = true)
        channel.send(selectMsg)

        assertEquals(1, transport.sentPayloads.size)
        assertTrue(transport.sentPayloads.first().contains("\"type\":\"SELECTION_CHANGE\""))
        assertTrue(transport.sentPayloads.first().contains("\"selectedObjectId\":\"slot_phone\""))
    }

    @Test
    fun incoming_emitsDeserializedMessages() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val serializer = DefaultSceneBridgeSerializer()
        val channel = DefaultSceneBridgeChannel(transport = transport, serializer = serializer, scope = backgroundScope)

        channel.connect()
        transport.emitIncoming(serializer.serialize(SceneBridgeMessage.RendererReady(protocolVersion = 1, rendererVersion = "1.0")))
        assertEquals(BridgeConnectionState.READY, channel.state.value)

        val receivedMessages = mutableListOf<SceneBridgeMessage>()
        val job = backgroundScope.launch {
            channel.incoming.collect {
                receivedMessages.add(it)
            }
        }

        val clickedMsg = SceneBridgeMessage.ObjectClicked(
            objectId = "phone_slot_1",
            screenX = 100f,
            screenY = 200f,
            isCtrlPressed = false,
            isShiftPressed = true,
        )
        transport.emitIncoming(serializer.serialize(clickedMsg))
        advanceUntilIdle()

        assertEquals(1, receivedMessages.size)
        val msg = receivedMessages.first()
        assertIs<SceneBridgeMessage.ObjectClicked>(msg)
        assertEquals("phone_slot_1", msg.objectId)
        assertEquals(100f, msg.screenX)
        assertEquals(200f, msg.screenY)
        assertTrue(msg.isShiftPressed)

        job.cancel()
    }

    @Test
    fun incoming_ignoresMalformedPayloadWithoutBreakingChannel() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val serializer = DefaultSceneBridgeSerializer()
        val channel = DefaultSceneBridgeChannel(transport = transport, serializer = serializer, scope = backgroundScope)

        channel.connect()
        transport.emitIncoming(serializer.serialize(SceneBridgeMessage.RendererReady(protocolVersion = 1, rendererVersion = "1.0")))
        assertEquals(BridgeConnectionState.READY, channel.state.value)

        var lastValidMessage: SceneBridgeMessage? = null
        val job = backgroundScope.launch {
            channel.incoming.collect {
                lastValidMessage = it
            }
        }

        // Emit garbage JSON
        transport.emitIncoming("INVALID_GARBAGE_JSON{{{::")
        advanceUntilIdle()

        // Channel state must remain intact
        assertEquals(BridgeConnectionState.READY, channel.state.value)

        // Next valid emission still processes correctly
        val hoverMsg = SceneBridgeMessage.ObjectHovered("phone_slot_2")
        transport.emitIncoming(serializer.serialize(hoverMsg))
        advanceUntilIdle()

        assertNotNull(lastValidMessage)
        assertIs<SceneBridgeMessage.ObjectHovered>(lastValidMessage)
        assertEquals("phone_slot_2", (lastValidMessage as SceneBridgeMessage.ObjectHovered).objectId)

        job.cancel()
    }

    @Test
    fun transportError_onConnect_transitionsToDisconnected() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport().apply { shouldFailConnect = true }
        val channel = DefaultSceneBridgeChannel(transport = transport, scope = backgroundScope)

        channel.connect()

        assertEquals(BridgeConnectionState.DISCONNECTED, channel.state.value)
        assertFalse(transport.isConnected)
    }

    @Test
    fun disconnect_resetsStateToDisconnectedAndClearsPendingQueue() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBridgeTransport()
        val channel = DefaultSceneBridgeChannel(transport = transport, scope = backgroundScope)

        channel.connect()
        assertEquals(BridgeConnectionState.CONNECTED, channel.state.value)

        // Add a message while in CONNECTED
        channel.send(SceneBridgeMessage.SelectionChange("test_obj"))

        // Disconnect before reaching READY
        channel.disconnect()

        assertEquals(BridgeConnectionState.DISCONNECTED, channel.state.value)
        assertTrue(transport.isClosed)

        // Reconnect and move to READY; the previous message was cleared, so nothing sent
        transport.isClosed = false
        channel.connect()
        transport.emitIncoming(DefaultSceneBridgeSerializer().serialize(SceneBridgeMessage.RendererReady(protocolVersion = 1, rendererVersion = "1.0")))
        assertEquals(BridgeConnectionState.READY, channel.state.value)
        assertTrue(transport.sentPayloads.isEmpty())
    }
}
