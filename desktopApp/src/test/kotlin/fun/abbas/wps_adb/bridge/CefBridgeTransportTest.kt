package `fun`.abbas.wps_adb.bridge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CefBridgeTransportTest {

    private class FakeCefJsBridge : CefJsBridge {
        val sentMessages = mutableListOf<String>()
        var currentListener: ((String) -> Unit)? = null

        override fun send(message: String) {
            sentMessages.add(message)
        }

        override fun setReceiveListener(listener: ((String) -> Unit)?) {
            this.currentListener = listener
        }

        fun emitFromJs(message: String) {
            currentListener?.invoke(message)
        }
    }

    @Test
    fun send_routesRawStringToCefJsBridgeWithoutDomainInspection() = runTest(UnconfinedTestDispatcher()) {
        val fakeBridge = FakeCefJsBridge()
        val transport = CefBridgeTransport(cefBridge = fakeBridge)

        val rawPayload = """{"type":"SYNC_STATE","payload":{"devices":[{"id":"dev_1"}]}}"""
        transport.send(rawPayload)

        assertEquals(1, fakeBridge.sentMessages.size)
        assertEquals(rawPayload, fakeBridge.sentMessages.first())
    }

    @Test
    fun incoming_receivesRawStringFramesFromJsRuntime() = runTest(UnconfinedTestDispatcher()) {
        val fakeBridge = FakeCefJsBridge()
        val transport = CefBridgeTransport(cefBridge = fakeBridge)

        val receivedFrames = mutableListOf<String>()
        val job = backgroundScope.launch {
            transport.incoming.collect {
                receivedFrames.add(it)
            }
        }

        fakeBridge.emitFromJs("""{"type":"RENDERER_READY","payload":{"webglVendor":"AMD"}}""")
        fakeBridge.emitFromJs("""{"type":"OBJECT_CLICKED","payload":{"objectId":"phone_1"}}""")
        advanceUntilIdle()

        assertEquals(2, receivedFrames.size)
        assertEquals("""{"type":"RENDERER_READY","payload":{"webglVendor":"AMD"}}""", receivedFrames[0])
        assertEquals("""{"type":"OBJECT_CLICKED","payload":{"objectId":"phone_1"}}""", receivedFrames[1])

        job.cancel()
    }

    @Test
    fun close_unregistersReceiveListener() = runTest(UnconfinedTestDispatcher()) {
        val fakeBridge = FakeCefJsBridge()
        val transport = CefBridgeTransport(cefBridge = fakeBridge)

        assertTrue(fakeBridge.currentListener != null)

        transport.close()

        assertNull(fakeBridge.currentListener)
    }

    @Test
    fun connect_close_connect_lifecycle_canReceiveMessagesAfterReconnect() = runTest(UnconfinedTestDispatcher()) {
        val fakeBridge = FakeCefJsBridge()
        val transport = CefBridgeTransport(cefBridge = fakeBridge)

        val received = mutableListOf<String>()
        val job = backgroundScope.launch {
            transport.incoming.collect {
                received.add(it)
            }
        }

        fakeBridge.emitFromJs("""{"type":"FIRST"}""")
        advanceUntilIdle()
        assertEquals(1, received.size)
        assertEquals("""{"type":"FIRST"}""", received[0])

        // Close transport
        transport.close()
        assertNull(fakeBridge.currentListener)

        // Reconnect
        transport.connect()
        assertTrue(fakeBridge.currentListener != null)

        fakeBridge.emitFromJs("""{"type":"SECOND"}""")
        advanceUntilIdle()
        assertEquals(2, received.size)
        assertEquals("""{"type":"SECOND"}""", received[1])

        job.cancel()
    }

    @Test
    fun incoming_buffersFirstFrame_whenEmittedBeforeCollector() = runTest(UnconfinedTestDispatcher()) {
        val fakeBridge = FakeCefJsBridge()
        val transport = CefBridgeTransport(cefBridge = fakeBridge)

        // Emit from JS BEFORE any collector has started
        fakeBridge.emitFromJs("""{"type":"RENDERER_READY","payload":{"version":1}}""")

        // Now start collector
        val received = mutableListOf<String>()
        val job = backgroundScope.launch {
            transport.incoming.collect {
                received.add(it)
            }
        }
        advanceUntilIdle()

        // Verifies the first frame was buffered in channel and received by collector
        assertEquals(1, received.size)
        assertEquals("""{"type":"RENDERER_READY","payload":{"version":1}}""", received.first())

        job.cancel()
    }
}
