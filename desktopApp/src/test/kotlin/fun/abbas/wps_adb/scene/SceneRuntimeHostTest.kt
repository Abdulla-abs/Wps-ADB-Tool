package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.SceneRuntimeContainer
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SceneRuntimeHostTest {

    @AfterTest
    fun tearDown() {
        SceneRuntimeContainer.resetForTest()
    }

    private class FakeBridgeTransport : BridgeTransport {
        private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
        override val incoming: Flow<String> = _incoming.asSharedFlow()

        val sentMessages = mutableListOf<String>()
        var isConnected = false
        var isClosed = false
        var closeCallCount = 0

        override suspend fun connect() {
            isConnected = true
        }

        override suspend fun send(payload: String) {
            sentMessages.add(payload)
        }

        override suspend fun close() {
            closeCallCount++
            isClosed = true
            isConnected = false
        }

        suspend fun emitIncoming(payload: String) {
            _incoming.emit(payload)
        }
    }

    @Test
    fun test_rendererReadyHandshake_triggersInitSceneAndSyncState() = runTest(UnconfinedTestDispatcher()) {
        val fakeTransport = FakeBridgeTransport()
        val host = SceneRuntimeHost(
            parentScope = this,
            customTransport = fakeTransport,
        )

        // Verify initial setup
        assertEquals(BridgeConnectionState.CONNECTED, host.state.value.connectionState)
        assertTrue(fakeTransport.sentMessages.isEmpty(), "No messages should be sent before READY")

        // Remote renderer emits standard RENDERER_READY frame
        val readyFrame = """
            {
                "type": "RENDERER_READY",
                "version": 1,
                "timestamp": 12345678,
                "payload": {
                    "protocolVersion": 1,
                    "rendererVersion": "1.0"
                }
            }
        """.trimIndent()

        fakeTransport.emitIncoming(readyFrame)

        // Verify channel and host transitioned to READY
        assertEquals(BridgeConnectionState.READY, host.state.value.connectionState)

        // Verify InitScene and SyncState were sent through transport to renderer
        assertEquals(2, fakeTransport.sentMessages.size)

        val initSceneJson = JSONObject(fakeTransport.sentMessages[0])
        assertEquals("SCENE_INIT", initSceneJson.getString("type"))
        assertEquals("scene_default", initSceneJson.getJSONObject("payload").getJSONObject("sceneDescriptor").getString("id"))

        val syncStateJson = JSONObject(fakeTransport.sentMessages[1])
        assertEquals("STATE_SYNC", syncStateJson.getString("type"))
        assertEquals("scene_default", syncStateJson.getJSONObject("payload").getJSONObject("snapshot").getString("sceneId"))

        host.dispose()
    }

    @Test
    fun test_disposeLifecycle_isIdempotentAndGracefullyClosesTransport() = runTest(UnconfinedTestDispatcher()) {
        val fakeTransport = FakeBridgeTransport()
        val host = SceneRuntimeHost(
            parentScope = this,
            customTransport = fakeTransport,
        )

        assertEquals(false, fakeTransport.isClosed)

        // First teardown
        host.close()
        assertTrue(fakeTransport.isClosed)
        assertEquals(1, fakeTransport.closeCallCount)

        // Second teardown (idempotency check)
        host.close()
        host.dispose()
        assertEquals(1, fakeTransport.closeCallCount, "close() must be idempotent and not close transport multiple times")
    }

    @Test
    fun test_concurrentDisposeAndHandshake_doesNotThrow() = runTest(StandardTestDispatcher()) {
        val fakeTransport = FakeBridgeTransport()
        val host = SceneRuntimeHost(
            parentScope = this,
            customTransport = fakeTransport,
        )

        // Launch concurrent incoming emission and close
        val job1 = launch {
            fakeTransport.emitIncoming("""{"type":"RENDERER_READY","version":1,"timestamp":1,"payload":{"protocolVersion":1,"rendererVersion":"1.0"}}""")
        }
        val job2 = launch {
            host.close()
        }

        job1.join()
        job2.join()
        advanceUntilIdle()

        assertTrue(fakeTransport.isClosed)
    }

    @Test
    fun test_sceneReadySpikeCompatibility_synthesizesRendererReadyAndSendsInitScene() = runTest(UnconfinedTestDispatcher()) {
        val adapter = SceneRuntimeHost.CefHostManagerBridgeAdapter()
        var emittedToTransport: String? = null
        adapter.setReceiveListener { emittedToTransport = it }

        // Spike renderer sends legacy SCENE_READY
        adapter.handleIncomingJsMessage("""{"type":"SCENE_READY","payload":{"webglVendor":"Intel"}}""")

        // Adapter must synthesize RENDERER_READY with protocolVersion 1
        val synthesized = emittedToTransport
        assertTrue(synthesized != null)
        val json = JSONObject(synthesized)
        assertEquals("RENDERER_READY", json.getString("type"))
        assertEquals(1, json.getInt("version"))
        assertEquals(1, json.getJSONObject("payload").getInt("protocolVersion"))
    }

    @Test
    fun test_browserInitializationFailure_updatesStateWithErrorMessage() = runTest(UnconfinedTestDispatcher()) {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val host = SceneRuntimeHost(
            parentScope = this,
            cefHostManagerProvider = {
                throw IllegalStateException("Simulated JCEF platform failure")
            },
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
        )

        assertEquals(false, host.state.value.isInitializing)
        assertTrue(host.state.value.initError?.contains("Simulated JCEF platform failure") == true)

        host.dispose()
    }

    @Test
    fun test_sceneRuntimeContainer_reusesSameInstanceUntilDisposed() {
        SceneRuntimeContainer.hostFactory = { scope ->
            SceneRuntimeHost(scope, customTransport = FakeBridgeTransport())
        }

        val instance1 = SceneRuntimeContainer.getOrCreate()
        val instance2 = SceneRuntimeContainer.getOrCreate()

        assertSame(instance1, instance2, "SceneRuntimeContainer must return the exact same instance across calls")

        SceneRuntimeContainer.dispose()

        val instance3 = SceneRuntimeContainer.getOrCreate()
        assertNotSame(instance1, instance3, "SceneRuntimeContainer must create a new instance after dispose()")

        SceneRuntimeContainer.dispose()
    }
}
