package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.SceneRuntimeContainer
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeConnectionState
import `fun`.abbas.wps_adb.data.scene.bridge.BridgeTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import `fun`.abbas.wps_adb.model.scene.SceneTransform
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
    fun test_selectingScene_updatesActiveSceneAndRuntimeState() = runTest {
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

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_sceneSwitch_flushesPendingChangesToOldScene() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("switch_flush_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_1",
                customTransport = FakeBridgeTransport(),
            )

            val newCam = SceneCamera(
                position = SceneVector3(12.0, 34.0, 56.0),
                target = SceneVector3(1.0, 2.0, 3.0),
                fov = 60.0,
            )
            // Schedule camera save on scene_1 (pending debounce)
            host.persistenceCoordinator?.scheduleCameraSave("scene_1", newCam)

            // Immediately switch to scene_2 (before debounce expires)
            val switchSuccess = host.selectScene("scene_2")
            assertTrue(switchSuccess)
            assertEquals("scene_2", host.state.value.activeSceneId)

            // Verify scene_1's disk repository file was flushed and contains new camera coordinates
            val persistedScene1 = store.loadScene("scene_1")
            assertEquals(12.0, persistedScene1.camera.position.x)
            assertEquals(34.0, persistedScene1.camera.position.y)
            assertEquals(56.0, persistedScene1.camera.position.z)
            assertEquals(60.0, persistedScene1.camera.fov)

            // Verify scene_2's camera was NOT overwritten with scene_1's camera
            val persistedScene2 = store.loadScene("scene_2")
            assertEquals(0.0, persistedScene2.camera.position.x)

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_rapidConcurrentSceneSwitches_maintainStateConsistency() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("concurrent_switch_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")
            store.createScene("Scene 3", "scene_3")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_1",
                customTransport = FakeBridgeTransport(),
            )

            // Launch concurrent switches
            val targets = listOf("scene_2", "scene_3", "scene_1", "scene_3", "scene_2")
            val switchJobs = targets.map { target ->
                async {
                    host.selectScene(target)
                }
            }
            val results = switchJobs.awaitAll()
            assertTrue(results.all { it })

            val finalActiveId = host.state.value.activeSceneId
            kotlin.test.assertNotNull(finalActiveId)
            assertEquals(finalActiveId, controller.activeScene.value?.id)
            assertTrue(targets.contains(finalActiveId))

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_deleteActiveScene_discardsPendingWrites_andSwitchesToFallback() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("delete_active_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_1",
                customTransport = FakeBridgeTransport(),
            )

            // Schedule a change on scene_1
            host.persistenceCoordinator?.scheduleCameraSave("scene_1", SceneCamera(position = SceneVector3(99.0, 99.0, 99.0)))

            // Delete scene_1 immediately
            val deleted = host.deleteScene("scene_1")
            assertTrue(deleted is SceneDeleteResult.Success)

            // Active scene must switch to scene_2
            assertEquals("scene_2", host.state.value.activeSceneId)
            assertEquals("scene_2", controller.activeScene.value?.id)

            // Scene 1 directory should no longer exist
            val remainingScenes = store.listScenes().map { it.id }
            assertEquals(listOf("scene_2"), remainingScenes)

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_gracefulClose_flushesPendingChangesBeforeShutdown() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("graceful_close_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_1",
                customTransport = FakeBridgeTransport(),
            )

            val closingCam = SceneCamera(
                position = SceneVector3(42.0, 42.0, 42.0),
                target = SceneVector3(0.0, 0.0, 0.0),
                fov = 50.0,
            )
            // Schedule camera save on scene_1 (pending debounce)
            host.persistenceCoordinator?.scheduleCameraSave("scene_1", closingCam)

            // Immediately close host
            host.close()

            // Verify camera change was flushed to disk despite closing before debounce
            val reloaded = store.loadScene("scene_1")
            assertEquals(42.0, reloaded.camera.position.x)
            assertEquals(42.0, reloaded.camera.position.y)
            assertEquals(42.0, reloaded.camera.position.z)
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_sceneSwitch_lateEventsFromOldScene_areDiscardedAndDoNotPolluteNewScene() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("late_event_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            // Switch to scene_2
            val switched = host.selectScene("scene_2")
            assertTrue(switched)
            assertEquals("scene_2", host.state.value.activeSceneId)

            // 1. Emit late event for scene_1 (should be dropped because sceneId is scene_1)
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":100,"payload":{"sceneId":"scene_1","position":{"x":99.0,"y":99.0,"z":99.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            // 2. Emit late event for scene_2 with stale epoch 0 (should be dropped because activeEpoch advanced)
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":101,"payload":{"sceneId":"scene_2","epoch":0,"position":{"x":88.0,"y":88.0,"z":88.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            kotlinx.coroutines.delay(600L)

            // Verify scene_2 and scene_1 cameras were NOT modified by the stale events
            val scene2After = store.loadScene("scene_2")
            assertEquals(0.0, scene2After.camera.position.x)

            val scene1After = store.loadScene("scene_1")
            assertEquals(0.0, scene1After.camera.position.x)

            // Crucial: Verify in-memory runtimeController was also NOT corrupted by the stale events
            assertEquals(0.0, controller.runtimeCamera.value?.position?.x ?: 0.0)

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_sceneSwitch_awayAndBack_staleEventsFromFirstVisit_rejectedByEpochAndDoNotPolluteRuntimeOrDisk() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("away_back_epoch_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            // Initial visit: scene_1 (activeEpoch = 0)
            assertEquals("scene_1", host.state.value.activeSceneId)

            // Switch to scene_2 (activeEpoch becomes 1)
            val switchedTo2 = host.selectScene("scene_2")
            assertTrue(switchedTo2)
            assertEquals("scene_2", host.state.value.activeSceneId)

            // Switch BACK to scene_1 (activeEpoch becomes 2)
            val switchedBackTo1 = host.selectScene("scene_1")
            assertTrue(switchedBackTo1)
            assertEquals("scene_1", host.state.value.activeSceneId)

            // Now emit a late event for scene_1 with stale epoch 0 (from the first visit!)
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":200,"payload":{"sceneId":"scene_1","epoch":0,"position":{"x":999.0,"y":999.0,"z":999.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            kotlinx.coroutines.delay(600L)

            // Verify stale event was rejected:
            // 1) Disk repository untouched
            val scene1AfterStale = store.loadScene("scene_1")
            assertEquals(0.0, scene1AfterStale.camera.position.x)
            // 2) In-memory runtime controller untouched
            assertEquals(0.0, controller.runtimeCamera.value?.position?.x ?: 0.0)

            // Now emit a valid event for scene_1 with activeEpoch 2
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":201,"payload":{"sceneId":"scene_1","epoch":2,"position":{"x":12.0,"y":34.0,"z":56.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            // Wait for background collector and debounce flush
            val memDeadline = System.currentTimeMillis() + 2000L
            while (controller.runtimeCamera.value?.position?.x != 12.0 && System.currentTimeMillis() < memDeadline) {
                Thread.sleep(50)
            }
            assertEquals(12.0, controller.runtimeCamera.value?.position?.x)

            val diskDeadline = System.currentTimeMillis() + 2000L
            while (store.loadScene("scene_1").camera.position.x != 12.0 && System.currentTimeMillis() < diskDeadline) {
                Thread.sleep(50)
            }
            val scene1AfterValid = store.loadScene("scene_1")
            assertEquals(12.0, scene1AfterValid.camera.position.x)

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_selectScene_abortsAndPreservesActiveScene_whenFlushFails() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("flush_fail_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            var failFlush = true
            val failingRepo = object : `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository by store {
                override fun saveCamera(sceneId: String, camera: SceneCamera): `fun`.abbas.wps_adb.model.scene.DeviceScene {
                    if (failFlush && sceneId == "scene_1") {
                        throw java.io.IOException("Simulated disk error during flush")
                    }
                    return store.saveCamera(sceneId, camera)
                }
            }

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = failingRepo,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            // Queue a pending camera change for scene_1
            val pendingCam = SceneCamera(
                position = SceneVector3(77.0, 77.0, 77.0),
                target = SceneVector3(0.0, 0.0, 0.0),
                fov = 50.0,
            )
            host.persistenceCoordinator?.scheduleCameraSave("scene_1", pendingCam)

            // Attempt to select scene_2 -> flush of scene_1 must fail and abort
            val result = host.selectScene("scene_2")
            kotlin.test.assertFalse(result, "selectScene must return false when flush fails")
            assertEquals("scene_1", host.state.value.activeSceneId, "activeSceneId must remain scene_1 after abort")

            // Verify activeEpoch was restored: emit an incoming event for scene_1 with epoch 0
            failFlush = false
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":100,"payload":{"sceneId":"scene_1","epoch":0,"position":{"x":123.0,"y":456.0,"z":789.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            val restoreDeadline = System.currentTimeMillis() + 2000L
            while (controller.runtimeCamera.value?.position?.x != 123.0 && System.currentTimeMillis() < restoreDeadline) {
                Thread.sleep(50)
            }
            assertEquals(123.0, controller.runtimeCamera.value?.position?.x, "Valid event with restored epoch must be accepted after switch failure")

            // Now retry switching to scene_2 and verify it succeeds
            val retryResult = host.selectScene("scene_2")
            assertTrue(retryResult, "selectScene must succeed after flush succeeds")
            assertEquals("scene_2", host.state.value.activeSceneId)

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_incomingEvents_missingSceneIdOrEpoch_strictlyRejectedByHost() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("strict_validation_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = store,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            // Initial visit: scene_1, activeEpoch = 0
            assertEquals("scene_1", host.state.value.activeSceneId)
            val initialX = controller.runtimeCamera.value?.position?.x ?: 0.0

            // 1. Missing sceneId (only epoch provided) -> MUST BE REJECTED
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":101,"payload":{"epoch":0,"position":{"x":901.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            // 2. Missing epoch (only sceneId provided) -> MUST BE REJECTED
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":102,"payload":{"sceneId":"scene_1","position":{"x":902.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            // 3. Both missing -> MUST BE REJECTED
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":103,"payload":{"position":{"x":903.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            // 4. Mismatched sceneId -> MUST BE REJECTED
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":104,"payload":{"sceneId":"scene_999","epoch":0,"position":{"x":904.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            // 5. Mismatched epoch -> MUST BE REJECTED
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":105,"payload":{"sceneId":"scene_1","epoch":999,"position":{"x":905.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            kotlinx.coroutines.delay(600L)

            // Verify memory and disk remain untouched
            assertEquals(initialX, controller.runtimeCamera.value?.position?.x ?: 0.0, "Invalid events must not mutate runtimeController")
            val sceneFromDisk = store.loadScene("scene_1")
            assertEquals(initialX, sceneFromDisk.camera.position.x, "Invalid events must not mutate disk repository")

            // 6. Valid event: correct sceneId and correct epoch -> MUST BE ACCEPTED
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":106,"payload":{"sceneId":"scene_1","epoch":0,"position":{"x":42.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            val validDeadline = System.currentTimeMillis() + 2000L
            while (controller.runtimeCamera.value?.position?.x != 42.0 && System.currentTimeMillis() < validDeadline) {
                Thread.sleep(50)
            }
            assertEquals(42.0, controller.runtimeCamera.value?.position?.x, "Valid event with sceneId and epoch must be accepted")

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_selectScene_cancellationDuringFlush_rollsBackEpochAndSwitchingState() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("cancel_flush_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            var cancelOnFlush = true
            val repo = object : `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository by store {
                override fun saveCamera(sceneId: String, camera: SceneCamera): `fun`.abbas.wps_adb.model.scene.DeviceScene {
                    if (cancelOnFlush && sceneId == "scene_1") {
                        throw CancellationException("Simulated coroutine cancellation during flush")
                    }
                    return store.saveCamera(sceneId, camera)
                }
            }

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = repo,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            // Queue a pending camera change so flushScene has work
            val pendingCam = SceneCamera(
                position = SceneVector3(10.0, 20.0, 30.0),
                target = SceneVector3.ZERO,
                fov = 50.0,
            )
            host.persistenceCoordinator?.scheduleCameraSave("scene_1", pendingCam)

            // Attempt to select scene_2 -> cancellation occurs during flush
            try {
                host.selectScene("scene_2")
                kotlin.test.fail("Expected CancellationException to be thrown")
            } catch (e: CancellationException) {
                assertEquals("Simulated coroutine cancellation during flush", e.message)
            }

            // Verify activeSceneId is preserved
            assertEquals("scene_1", host.state.value.activeSceneId)

            // Verify activeEpoch was rolled back: emit an incoming event for scene_1 with epoch 0
            cancelOnFlush = false
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":200,"payload":{"sceneId":"scene_1","epoch":0,"position":{"x":777.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            val deadline = System.currentTimeMillis() + 2000L
            while (controller.runtimeCamera.value?.position?.x != 777.0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertEquals(777.0, controller.runtimeCamera.value?.position?.x, "Event with restored epoch 0 must be accepted after cancelled flush")

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_selectScene_cancellationDuringLoad_rollsBackEpochAndSwitchingState() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("cancel_load_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            var cancelOnLoad = true
            val repo = object : `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository by store {
                override fun loadScene(id: String): DeviceScene {
                    if (cancelOnLoad && id == "scene_2") {
                        throw CancellationException("Simulated coroutine cancellation during loadScene")
                    }
                    return store.loadScene(id)
                }
            }

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = repo,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            // Attempt to select scene_2 -> cancellation occurs during load
            try {
                host.selectScene("scene_2")
                kotlin.test.fail("Expected CancellationException to be thrown")
            } catch (e: CancellationException) {
                assertEquals("Simulated coroutine cancellation during loadScene", e.message)
            }

            // Verify activeSceneId is preserved
            assertEquals("scene_1", host.state.value.activeSceneId)

            // Verify activeEpoch was rolled back: emit an incoming event for scene_1 with epoch 0
            cancelOnLoad = false
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":300,"payload":{"sceneId":"scene_1","epoch":0,"position":{"x":888.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            val deadline = System.currentTimeMillis() + 2000L
            while (controller.runtimeCamera.value?.position?.x != 888.0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertEquals(888.0, controller.runtimeCamera.value?.position?.x, "Event with restored epoch 0 must be accepted after cancelled load")

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_deleteScene_cancellationDuringDeletion_rollsBackEpochAndSwitchingState_andPreservesPendingSaves() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("cancel_delete_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            var cancelOnDelete = true
            val repo = object : `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository by store {
                override fun deleteScene(id: String): Boolean {
                    if (cancelOnDelete && id == "scene_1") {
                        throw CancellationException("Simulated coroutine cancellation during deleteScene")
                    }
                    return store.deleteScene(id)
                }
            }

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = repo,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            // Queue a pending camera change for scene_1 before delete attempt
            val pendingCam = SceneCamera(
                position = SceneVector3(123.0, 456.0, 789.0),
                target = SceneVector3.ZERO,
                fov = 50.0,
            )
            host.persistenceCoordinator?.scheduleCameraSave("scene_1", pendingCam)

            // Attempt to delete active scene_1 -> cancellation occurs during deleteScene
            try {
                host.deleteScene("scene_1")
                kotlin.test.fail("Expected CancellationException to be thrown")
            } catch (e: CancellationException) {
                assertEquals("Simulated coroutine cancellation during deleteScene", e.message)
            }

            // Verify activeSceneId is preserved
            assertEquals("scene_1", host.state.value.activeSceneId)

            // Verify pending writes were NOT discarded: flushScene should persist pendingCam to disk
            host.persistenceCoordinator?.flushScene("scene_1")
            val scene1FromDisk = store.loadScene("scene_1")
            assertEquals(123.0, scene1FromDisk.camera.position.x, "Pending camera save must NOT be discarded if deletion was cancelled")

            // Verify activeEpoch was rolled back: emit an incoming event for scene_1 with epoch 0
            cancelOnDelete = false
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":400,"payload":{"sceneId":"scene_1","epoch":0,"position":{"x":999.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            val deadline = System.currentTimeMillis() + 2000L
            while (controller.runtimeCamera.value?.position?.x != 999.0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertEquals(999.0, controller.runtimeCamera.value?.position?.x, "Event with restored epoch 0 must be accepted after cancelled delete")

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_deleteActiveScene_whenCancelledAfterDiskDelete_completesFallbackActivationUnderNonCancellable() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("cancel_after_delete_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            var callingJob: kotlinx.coroutines.Job? = null
            val repo = object : `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository by store {
                override fun deleteScene(id: String): Boolean {
                    val result = store.deleteScene(id)
                    // Trigger cancellation right as disk deletion finishes
                    callingJob?.cancel(CancellationException("Simulated cancel right after disk delete"))
                    return result
                }
            }

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = repo,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            assertEquals("scene_1", host.state.value.activeSceneId)

            val job = testScope.launch {
                host.deleteScene("scene_1")
            }
            callingJob = job
            job.join()

            // Crucial verification: Even though the calling job was cancelled after disk deletion,
            // the post-deletion fallback activation ran to completion under NonCancellable!
            assertEquals("scene_2", host.state.value.activeSceneId, "Fallback scene_2 must be active, not deleted scene_1")

            // Verify events for scene_2 are accepted (activeEpoch advanced to 2 upon selecting fallback scene)
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":500,"payload":{"sceneId":"scene_2","epoch":2,"position":{"x":333.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            val deadline = System.currentTimeMillis() + 2000L
            while (controller.runtimeCamera.value?.position?.x != 333.0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertEquals(333.0, controller.runtimeCamera.value?.position?.x, "Event for fallback scene_2 must be accepted")

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_deleteActiveScene_cancellationDuringDefaultFallbackCreation_doesNotDeleteCurrentScene() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("cancel_fallback_save_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            // Only scene_1 exists; deleting it requires creating and saving a default fallback scene

            var cancelOnSave = true
            val repo = object : `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository by store {
                override fun saveScene(scene: DeviceScene): DeviceScene {
                    if (cancelOnSave && scene.id != "scene_1") {
                        throw CancellationException("Simulated cancellation during saving default fallback")
                    }
                    return store.saveScene(scene)
                }
            }

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = repo,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            assertEquals("scene_1", host.state.value.activeSceneId)

            // Attempt to delete scene_1 -> cancellation occurs when trying to save default fallback
            try {
                host.deleteScene("scene_1")
                kotlin.test.fail("Expected CancellationException to be thrown")
            } catch (e: CancellationException) {
                assertEquals("Simulated cancellation during saving default fallback", e.message)
            }

            // Verify scene_1 was NOT deleted on disk!
            val sceneOnDisk = store.loadScene("scene_1")
            assertEquals("scene_1", sceneOnDisk.id, "scene_1 must remain on disk if fallback preparation was cancelled")

            // Verify host state is restored and accepts events for scene_1
            assertEquals("scene_1", host.state.value.activeSceneId)
            fakeTransport.emitIncoming(
                """{"type":"CAMERA_CHANGED","version":1,"timestamp":600,"payload":{"sceneId":"scene_1","epoch":0,"position":{"x":444.0,"y":0.0,"z":0.0},"target":{"x":0.0,"y":0.0,"z":0.0},"fov":50.0}}"""
            )

            val deadline = System.currentTimeMillis() + 2000L
            while (controller.runtimeCamera.value?.position?.x != 444.0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertEquals(444.0, controller.runtimeCamera.value?.position?.x, "Event for preserved scene_1 must be accepted")

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_deleteActiveScene_fallbackActivationFails_setsInitErrorAndReturnsFalse() = runTest {
        val testScope = CoroutineScope(SupervisorJob())
        val tempDir = java.nio.file.Files.createTempDirectory("fallback_activation_fail_test").toFile()
        try {
            val store = `fun`.abbas.wps_adb.data.scene.SceneStore(scenesRoot = tempDir)
            store.createScene("Scene 1", "scene_1")
            store.createScene("Scene 2", "scene_2")

            var failLoading = false
            val repo = object : `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository by store {
                override fun loadScene(id: String): DeviceScene {
                    if (failLoading) {
                        throw java.io.IOException("Simulated I/O failure loading fallback scene")
                    }
                    return store.loadScene(id)
                }

                override fun saveScene(scene: DeviceScene): DeviceScene {
                    if (failLoading) {
                        throw java.io.IOException("Simulated I/O failure saving emergency fallback scene")
                    }
                    return store.saveScene(scene)
                }
            }

            val controller = `fun`.abbas.wps_adb.data.scene.runtime.DefaultSceneRuntimeController(
                devicesFlow = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                scope = testScope,
            )

            val fakeTransport = FakeBridgeTransport()
            val host = SceneRuntimeHost(
                parentScope = testScope,
                sceneRuntimeController = controller,
                sceneRepository = repo,
                initialActiveSceneId = "scene_1",
                customTransport = fakeTransport,
            )

            assertEquals("scene_1", host.state.value.activeSceneId)

            // When deleting scene_1, simulate catastrophic failure during fallback loading and emergency fallback
            failLoading = true
            val deletedResult = host.deleteScene("scene_1")

            // 1. Result contract: returns DeletedFallbackFailed (not blunt false)
            kotlin.test.assertIs<SceneDeleteResult.DeletedFallbackFailed>(deletedResult)

            // 2. Repository consistency: scene_1 was indeed deleted on disk
            kotlin.test.assertFailsWith<`fun`.abbas.wps_adb.data.scene.SceneNotFoundException> {
                store.loadScene("scene_1")
            }

            // 3. Host consistency: activeSceneId is null and initError is set
            kotlin.test.assertNull(host.state.value.activeSceneId)
            kotlin.test.assertNotNull(host.state.value.initError, "initError must be set when fallback activation fails")

            // 4. Runtime Controller consistency: reset to null, avoiding dangling deleted scene
            kotlin.test.assertNull(controller.activeScene.value, "Controller activeScene must be reset to null")
            kotlin.test.assertNull(controller.resolvedState.value, "Controller resolvedState must be reset to null")

            host.close()
        } finally {
            testScope.cancel()
            tempDir.deleteRecursively()
        }
    }
}


