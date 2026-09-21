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
}
