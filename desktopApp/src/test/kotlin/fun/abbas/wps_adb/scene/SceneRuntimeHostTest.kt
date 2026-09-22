package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.SceneRuntimeContainer
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import kotlin.test.assertFalse

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

    @Test
    fun test_incomingObjectClicked_updatesControllerSelectionAndEmitsSelectionChange() = runTest(UnconfinedTestDispatcher()) {
        val fakeTransport = FakeBridgeTransport()
        val devicesFlow = kotlinx.coroutines.flow.MutableStateFlow<List<`fun`.abbas.wps_adb.model.Device>>(emptyList())
        val controllerScope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = devicesFlow,
                scope = controllerScope,
            )

            val host = SceneRuntimeHost(
                parentScope = this,
                sceneRuntimeController = controller,
                customTransport = fakeTransport,
            )

            // Remote renderer emits RENDERER_READY
            fakeTransport.emitIncoming("""{"type":"RENDERER_READY","version":1,"timestamp":100,"payload":{"protocolVersion":1,"rendererVersion":"1.0"}}""")
            assertEquals(BridgeConnectionState.READY, host.state.value.connectionState)

            // Clear initial INIT and SYNC messages
            fakeTransport.sentMessages.clear()

            // Remote renderer emits OBJECT_CLICKED
            val clickFrame = """
                {
                    "type": "OBJECT_CLICKED",
                    "version": 1,
                    "timestamp": 200,
                    "payload": {
                        "objectId": "phone_slot_3",
                        "screenX": 100,
                        "screenY": 200,
                        "isCtrlPressed": false,
                        "isShiftPressed": false
                    }
                }
            """.trimIndent()
            fakeTransport.emitIncoming(clickFrame)

            // Verify controller updated selection
            assertEquals("phone_slot_3", controller.selectedObjectId.value)

            // Verify SELECTION_CHANGE was dispatched to confirm highlight
            val selectionMessages = fakeTransport.sentMessages.map { JSONObject(it) }.filter { it.getString("type") == "SELECTION_CHANGE" }
            assertTrue(selectionMessages.isNotEmpty(), "Expected SELECTION_CHANGE to be dispatched")
            assertEquals("phone_slot_3", selectionMessages.last().getJSONObject("payload").getString("selectedObjectId"))

            host.dispose()
        } finally {
            controllerScope.cancel()
        }
    }

    @Test
    fun test_host_dispatchesStateSync_whenRealDeviceConnectsAndControllerResolves() = runTest(UnconfinedTestDispatcher()) {
        val fakeTransport = FakeBridgeTransport()
        val devicesFlow = kotlinx.coroutines.flow.MutableStateFlow<List<`fun`.abbas.wps_adb.model.Device>>(emptyList())
        val controllerScope = CoroutineScope(coroutineContext + SupervisorJob())

        try {
            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = devicesFlow,
                scope = controllerScope,
            )

            val initialScene = `fun`.abbas.wps_adb.model.scene.DeviceScene(
                id = "scene_active",
                name = "Active Scene",
                bindings = listOf(
                    `fun`.abbas.wps_adb.model.scene.SceneBinding(
                        objectId = "phone_slot_1",
                        deviceIdentity = `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef("HW-PIXEL-8"),
                    )
                ),
            )
            controller.setScene(initialScene)

            val host = SceneRuntimeHost(
                parentScope = this,
                sceneRuntimeController = controller,
                customTransport = fakeTransport,
            )

            // Emit RENDERER_READY
            fakeTransport.emitIncoming("""{"type":"RENDERER_READY","version":1,"timestamp":100,"payload":{"protocolVersion":1,"rendererVersion":"1.0"}}""")
            assertEquals(BridgeConnectionState.READY, host.state.value.connectionState)

            // Initially, device is OFFLINE
            val syncMessagesBefore = fakeTransport.sentMessages.map { JSONObject(it) }.filter { it.getString("type") == "STATE_SYNC" }
            assertTrue(syncMessagesBefore.isNotEmpty())
            val firstDevice = syncMessagesBefore.last().getJSONObject("payload").getJSONObject("snapshot").getJSONArray("devices").getJSONObject(0)
            assertEquals("OFFLINE", firstDevice.getString("status"))

            // Real device connects via ADB flow
            val realDevice = `fun`.abbas.wps_adb.model.Device(
                id = "dev_usb_1",
                name = "Google Pixel 8",
                serial = "dev_usb_1",
                type = `fun`.abbas.wps_adb.model.DeviceType.PHYSICAL,
                connectionType = `fun`.abbas.wps_adb.model.ConnectionType.USB,
                status = `fun`.abbas.wps_adb.model.DeviceStatus.ONLINE,
                androidVersion = "14",
                batteryLevel = 90,
                isCharging = true,
                storageUsed = "10GB",
                storageTotal = "128GB",
                storagePercent = 8,
                screenshotUrl = "",
                screenDescription = "Screen",
                identity = `fun`.abbas.wps_adb.model.DeviceIdentity(
                    value = "HW-PIXEL-8",
                    source = `fun`.abbas.wps_adb.model.DeviceIdentitySource.RO_SERIALNO,
                    rawHardwareSerial = "HW-PIXEL-8",
                ),
            )
            devicesFlow.value = listOf(realDevice)

            // Verify STATE_SYNC is dispatched with ONLINE status
            val syncMessagesAfter = fakeTransport.sentMessages.map { JSONObject(it) }.filter { it.getString("type") == "STATE_SYNC" }
            val updatedDevice = syncMessagesAfter.last().getJSONObject("payload").getJSONObject("snapshot").getJSONArray("devices").getJSONObject(0)
            assertEquals("ONLINE", updatedDevice.getString("status"))
            assertEquals("phone_slot_1", updatedDevice.getString("objectId"))

            host.dispose()
        } finally {
            controllerScope.cancel()
        }
    }

    @Test
    fun test_hostController_rebindCancelsPreviousJobsWithoutLeak() = runTest(UnconfinedTestDispatcher()) {
        val fakeTransport = FakeBridgeTransport()
        val controllerScope = CoroutineScope(coroutineContext + SupervisorJob())

        try {
            val controller1 = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = controllerScope,
            )
            val controller2 = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = controllerScope,
            )

            val host = SceneRuntimeHost(
                parentScope = this,
                customTransport = fakeTransport,
            )

            // Bind controller 1
            val job1 = host.hostController.bind(controller1)
            assertTrue(job1.isActive)

            // Rebind controller 2
            val job2 = host.hostController.bind(controller2)
            assertTrue(job2.isActive)
            assertTrue(job1.isCancelled, "Rebinding must cancel previous binding job")

            host.dispose()
            assertTrue(job2.isCancelled, "Host dispose must cancel current binding job")
        } finally {
            controllerScope.cancel()
        }
    }

    @Test
    fun test_scenesRoot_consistencyBetweenSceneStoreAndHost() {
        val tempDir = java.nio.file.Files.createTempDirectory("scenes_root_test").toFile()
        try {
            val sceneStore = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            val scope = CoroutineScope(SupervisorJob())
            val host = SceneRuntimeHost(
                parentScope = scope,
                sceneRepository = sceneStore,
                customTransport = FakeBridgeTransport(),
            )

            assertEquals(tempDir.canonicalPath, sceneStore.getScenesRoot().canonicalPath)
            host.dispose()
            scope.cancel()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_sceneRuntimeContainer_injectsControllerAndRepository() {
        val controllerScope = CoroutineScope(SupervisorJob())
        try {
            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = controllerScope,
            )
            val tempDir = java.nio.file.Files.createTempDirectory("container_scene_test").toFile()
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)

            val host = SceneRuntimeContainer.getOrCreate(
                runtimeController = controller,
                repository = store,
            )

            assertSame(controller, host.sceneRuntimeController)
            assertSame(store, host.sceneRepository)

            SceneRuntimeContainer.dispose()
            tempDir.deleteRecursively()
        } finally {
            controllerScope.cancel()
        }
    }

    @Test
    fun test_startupRestoresConfiguredActiveSceneId() {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("active_scene_restore_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            var changedId: String? = null
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_2",
                onActiveSceneIdChanged = { changedId = it },
                customTransport = FakeBridgeTransport(),
            )

            assertEquals("scene_2", host.state.value.activeSceneId)
            assertEquals("scene_2", controller.activeScene.value?.id)

            host.dispose()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_startupFallsBackWhenConfiguredActiveSceneIdNotFound() {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("active_scene_fallback_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            var writtenBackId: String? = null
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "deleted_or_missing_scene",
                onActiveSceneIdChanged = { writtenBackId = it },
                customTransport = FakeBridgeTransport(),
            )

            assertEquals("scene_1", host.state.value.activeSceneId)
            assertEquals("scene_1", controller.activeScene.value?.id)
            assertEquals("scene_1", writtenBackId, "Fallback should write back valid scene id")

            host.dispose()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_selectingScene_updatesActiveSceneAndRuntimeState() {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("select_scene_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            var changedId: String? = null
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_1",
                onActiveSceneIdChanged = { changedId = it },
                customTransport = FakeBridgeTransport(),
            )

            assertEquals("scene_1", host.state.value.activeSceneId)
            assertEquals("scene_1", controller.activeScene.value?.id)

            // Select scene_2
            val success = host.selectScene("scene_2")
            assertTrue(success)
            assertEquals("scene_2", host.state.value.activeSceneId)
            assertEquals("scene_2", controller.activeScene.value?.id)
            assertEquals("scene_2", changedId)

            // Attempt to select invalid scene
            val fail = host.selectScene("non_existent")
            assertFalse(fail)
            // State should remain on scene_2
            assertEquals("scene_2", host.state.value.activeSceneId)
            assertEquals("scene_2", controller.activeScene.value?.id)

            host.dispose()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }
}


