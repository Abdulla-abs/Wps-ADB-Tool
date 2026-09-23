package `fun`.abbas.wps_adb.scene

import `fun`.abbas.wps_adb.data.scene.DeviceSceneRepository
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScenePersistenceCoordinatorTest {

    private class FakeSceneRepository : DeviceSceneRepository {
        val savedCameras = mutableListOf<Pair<String, SceneCamera>>()
        val savedTransforms = mutableListOf<Triple<String, String, SceneTransform>>()

        override fun listScenes(): List<DeviceScene> = emptyList()
        override fun loadScene(id: String): DeviceScene = DeviceScene(id = id, name = id)
        override fun saveScene(scene: DeviceScene): DeviceScene = scene
        override fun deleteScene(id: String): Boolean = true
        override fun createScene(name: String, id: String?): DeviceScene = DeviceScene(id = id ?: "test", name = name)
        override fun importEnvironment(sceneId: String, sourceFile: File): DeviceScene = DeviceScene(id = sceneId, name = sceneId)
        override fun importAsset(sceneId: String, assetId: String, sourceFile: File, transform: SceneTransform, customName: String?): DeviceScene =
            DeviceScene(id = sceneId, name = sceneId)
        override fun bindDevice(sceneId: String, objectId: String, deviceIdentity: DeviceIdentityRef): DeviceScene =
            DeviceScene(id = sceneId, name = sceneId)
        override fun unbindDevice(sceneId: String, objectId: String): DeviceScene =
            DeviceScene(id = sceneId, name = sceneId)
        override fun updateBinding(sceneId: String, objectId: String, deviceIdentity: DeviceIdentityRef?): DeviceScene =
            DeviceScene(id = sceneId, name = sceneId)

        var failOnSaveCamera: Boolean = false

        override fun saveCamera(sceneId: String, camera: SceneCamera): DeviceScene {
            if (failOnSaveCamera) throw java.io.IOException("Disk write failure")
            savedCameras.add(sceneId to camera)
            return DeviceScene(id = sceneId, name = sceneId, camera = camera)
        }

        override fun deleteAsset(sceneId: String, assetId: String): DeviceScene =
            DeviceScene(id = sceneId, name = sceneId)

        override fun updateTransform(sceneId: String, objectId: String, transform: SceneTransform): DeviceScene {
            savedTransforms.add(Triple(sceneId, objectId, transform))
            return DeviceScene(id = sceneId, name = sceneId)
        }
    }

    @Test
    fun debounceCoalescesMultipleCameraUpdates() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeRepo = FakeSceneRepository()
        val coordinator = ScenePersistenceCoordinator(
            repository = fakeRepo,
            parentScope = this,
            debounceMillis = 500L,
            ioDispatcher = testDispatcher,
        )

        val cam1 = SceneCamera(position = SceneVector3(1.0, 1.0, 1.0))
        val cam2 = SceneCamera(position = SceneVector3(2.0, 2.0, 2.0))
        val cam3 = SceneCamera(position = SceneVector3(3.0, 3.0, 3.0))

        coordinator.scheduleCameraSave("scene_1", cam1)
        advanceTimeBy(100L)
        coordinator.scheduleCameraSave("scene_1", cam2)
        advanceTimeBy(100L)
        coordinator.scheduleCameraSave("scene_1", cam3)

        // Before debounce expires, nothing saved
        advanceTimeBy(400L)
        assertTrue(fakeRepo.savedCameras.isEmpty())

        // Once debounce expires (total 500ms after cam3)
        advanceTimeBy(200L)
        assertEquals(1, fakeRepo.savedCameras.size)
        assertEquals("scene_1", fakeRepo.savedCameras.single().first)
        assertEquals(cam3, fakeRepo.savedCameras.single().second)

        coordinator.close()
    }

    @Test
    fun debounceCoalescesMultipleTransformUpdates() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeRepo = FakeSceneRepository()
        val coordinator = ScenePersistenceCoordinator(
            repository = fakeRepo,
            parentScope = this,
            debounceMillis = 500L,
            ioDispatcher = testDispatcher,
        )

        val t1 = SceneTransform(position = SceneVector3(1.0, 0.0, 0.0))
        val t2 = SceneTransform(position = SceneVector3(2.0, 0.0, 0.0))
        val t3 = SceneTransform(position = SceneVector3(3.0, 0.0, 0.0))

        coordinator.scheduleTransformSave("scene_1", "asset_phone", t1)
        advanceTimeBy(100L)
        coordinator.scheduleTransformSave("scene_1", "asset_phone", t2)
        advanceTimeBy(100L)
        coordinator.scheduleTransformSave("scene_1", "asset_phone", t3)

        advanceTimeBy(450L)
        assertTrue(fakeRepo.savedTransforms.isEmpty())

        advanceTimeBy(100L)
        assertEquals(1, fakeRepo.savedTransforms.size)
        val (sceneId, objectId, savedTransform) = fakeRepo.savedTransforms.single()
        assertEquals("scene_1", sceneId)
        assertEquals("asset_phone", objectId)
        assertEquals(t3, savedTransform)

        coordinator.close()
    }

    @Test
    fun flushScene_immediatelySavesPendingCameraAndTransform() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeRepo = FakeSceneRepository()
        val coordinator = ScenePersistenceCoordinator(
            repository = fakeRepo,
            parentScope = this,
            debounceMillis = 500L,
            ioDispatcher = testDispatcher,
        )

        val cam = SceneCamera(position = SceneVector3(5.0, 5.0, 5.0))
        val transform = SceneTransform(position = SceneVector3(10.0, 0.0, 0.0))

        coordinator.scheduleCameraSave("scene_1", cam)
        coordinator.scheduleTransformSave("scene_1", "asset_1", transform)

        advanceTimeBy(50L)
        assertTrue(fakeRepo.savedCameras.isEmpty())
        assertTrue(fakeRepo.savedTransforms.isEmpty())

        coordinator.flushScene("scene_1")
        assertEquals(1, fakeRepo.savedCameras.size)
        assertEquals(cam, fakeRepo.savedCameras.single().second)
        assertEquals(1, fakeRepo.savedTransforms.size)
        assertEquals(transform, fakeRepo.savedTransforms.single().third)

        // Ensure timer doesn't fire duplicate save after debounce
        advanceTimeBy(600L)
        assertEquals(1, fakeRepo.savedCameras.size)
        assertEquals(1, fakeRepo.savedTransforms.size)

        coordinator.close()
    }

    @Test
    fun sceneSwitch_flushAndCancel_doesNotPolluteNewScene() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeRepo = FakeSceneRepository()
        val coordinator = ScenePersistenceCoordinator(
            repository = fakeRepo,
            parentScope = this,
            debounceMillis = 500L,
            ioDispatcher = testDispatcher,
        )

        val camA = SceneCamera(position = SceneVector3(1.0, 1.0, 1.0))
        coordinator.scheduleCameraSave("scene_A", camA)

        // User switches from scene_A to scene_B
        coordinator.flushScene("scene_A")
        coordinator.cancelScene("scene_A")

        assertEquals(1, fakeRepo.savedCameras.size)
        assertEquals("scene_A", fakeRepo.savedCameras.single().first)

        val camB = SceneCamera(position = SceneVector3(9.0, 9.0, 9.0))
        coordinator.scheduleCameraSave("scene_B", camB)

        advanceTimeBy(600L)
        assertEquals(2, fakeRepo.savedCameras.size)
        assertEquals("scene_A", fakeRepo.savedCameras[0].first)
        assertEquals("scene_B", fakeRepo.savedCameras[1].first)
        assertEquals(camB, fakeRepo.savedCameras[1].second)

        coordinator.close()
    }

    @Test
    fun discardScene_cancelsPendingSavesAndPreventsWrites() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeRepo = FakeSceneRepository()
        val coordinator = ScenePersistenceCoordinator(
            repository = fakeRepo,
            parentScope = this,
            debounceMillis = 500L,
            ioDispatcher = testDispatcher,
        )

        val cam = SceneCamera(position = SceneVector3(7.0, 7.0, 7.0))
        val transform = SceneTransform(position = SceneVector3(3.0, 0.0, 0.0))

        coordinator.scheduleCameraSave("scene_to_delete", cam)
        coordinator.scheduleTransformSave("scene_to_delete", "asset_1", transform)

        advanceTimeBy(100L)
        assertTrue(fakeRepo.savedCameras.isEmpty())
        assertTrue(fakeRepo.savedTransforms.isEmpty())

        // Scene is deleted: discard pending saves
        coordinator.discardScene("scene_to_delete")

        advanceTimeBy(1000L)
        assertTrue(fakeRepo.savedCameras.isEmpty(), "Discarded camera save must not write to repository")
        assertTrue(fakeRepo.savedTransforms.isEmpty(), "Discarded transform save must not write to repository")

        coordinator.close()
    }

    @Test
    fun flushScene_propagatesExceptions_whenRepositoryFails() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeRepo = FakeSceneRepository()
        fakeRepo.failOnSaveCamera = true
        val coordinator = ScenePersistenceCoordinator(
            repository = fakeRepo,
            parentScope = this,
            debounceMillis = 500L,
            ioDispatcher = testDispatcher,
        )

        val cam = SceneCamera(position = SceneVector3(5.0, 5.0, 5.0))
        coordinator.scheduleCameraSave("scene_1", cam)

        var thrown = false
        try {
            coordinator.flushScene("scene_1")
        } catch (_: java.io.IOException) {
            thrown = true
        }
        assertTrue(thrown, "flushScene must propagate repository write failure")
        coordinator.close()
    }
}
