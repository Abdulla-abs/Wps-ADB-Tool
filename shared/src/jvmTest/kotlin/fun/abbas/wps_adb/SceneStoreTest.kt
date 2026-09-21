package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.InvalidGlbException
import `fun`.abbas.wps_adb.data.scene.SceneNotFoundException
import `fun`.abbas.wps_adb.data.scene.SceneParseException
import `fun`.abbas.wps_adb.data.scene.SceneResourceNotFoundException
import `fun`.abbas.wps_adb.data.scene.SceneStore
import `fun`.abbas.wps_adb.data.scene.SceneValidationException
import `fun`.abbas.wps_adb.data.scene.UnsupportedSchemaVersionException
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneBinding
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneStoreTest {

    private fun createTempStore(): Pair<SceneStore, File> {
        val dir = File(System.getProperty("java.io.tmpdir"), "wps_scene_test_${System.nanoTime()}")
        dir.mkdirs()
        return SceneStore(scenesRoot = dir) to dir
    }

    private fun createDummyGlb(targetFile: File) {
        val magic = 0x46546C67 // "glTF"
        val version = 2
        val length = 28
        val chunkLength = 8
        val chunkType = 0x4E4F534A // "JSON"
        val chunkData = "{\"a\":1} ".toByteArray(Charsets.US_ASCII)

        val buffer = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(magic)
        buffer.putInt(version)
        buffer.putInt(length)
        buffer.putInt(chunkLength)
        buffer.putInt(chunkType)
        buffer.put(chunkData)

        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(buffer.array())
    }

    @Test
    fun emptyStore_returnsEmptyList() {
        val (store, rootDir) = createTempStore()
        try {
            val scenes = store.listScenes()
            assertTrue(scenes.isEmpty())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun createSaveLoadRoundTrip() {
        val (store, rootDir) = createTempStore()
        try {
            val created = store.createScene(name = "Main Lab", id = "main_lab")
            assertEquals("main_lab", created.id)
            assertEquals("Main Lab", created.name)

            val loaded = store.loadScene("main_lab")
            assertEquals("main_lab", loaded.id)
            assertEquals("Main Lab", loaded.name)
            assertEquals(1, loaded.schemaVersion)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun multipleScenes_persistedIndependently() {
        val (store, rootDir) = createTempStore()
        try {
            val scene1 = store.createScene(name = "Scene One", id = "scene_1")
            val scene2 = store.createScene(name = "Scene Two", id = "scene_2")
            val scene3 = store.createScene(name = "Scene Three", id = "scene_3")

            val scenes = store.listScenes()
            assertEquals(3, scenes.size)
            val ids = scenes.map { it.id }.toSet()
            assertTrue(ids.contains("scene_1"))
            assertTrue(ids.contains("scene_2"))
            assertTrue(ids.contains("scene_3"))
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun cameraRoundTrip() {
        val (store, rootDir) = createTempStore()
        try {
            store.createScene(name = "Camera Test", id = "cam_test")
            val updated = DeviceScene(
                id = "cam_test",
                name = "Camera Test",
                camera = SceneCamera(
                    position = SceneVector3(5.5, 10.5, 15.5),
                    target = SceneVector3(1.1, 2.2, 3.3),
                    fov = 75.0,
                ),
            )
            store.saveScene(updated)

            val loaded = store.loadScene("cam_test")
            assertEquals(5.5, loaded.camera.position.x)
            assertEquals(10.5, loaded.camera.position.y)
            assertEquals(15.5, loaded.camera.position.z)
            assertEquals(1.1, loaded.camera.target.x)
            assertEquals(2.2, loaded.camera.target.y)
            assertEquals(3.3, loaded.camera.target.z)
            assertEquals(75.0, loaded.camera.fov)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun assetTransformRoundTrip() {
        val (store, rootDir) = createTempStore()
        val dummyGlb = File(rootDir, "temp_phone.glb")
        createDummyGlb(dummyGlb)
        try {
            store.createScene(name = "Asset Test", id = "asset_test")
            val transform = SceneTransform(
                position = SceneVector3(2.5, 1.0, -3.0),
                rotation = SceneVector3(0.0, 45.0, 90.0),
                scale = SceneVector3(0.5, 0.5, 0.5),
            )
            store.importAsset(
                sceneId = "asset_test",
                assetId = "phone_01",
                sourceFile = dummyGlb,
                transform = transform,
                customName = "My Pixel",
            )

            val loaded = store.loadScene("asset_test")
            assertEquals(1, loaded.assets.size)
            val asset = loaded.assets[0]
            assertEquals("phone_01", asset.id)
            assertEquals("My Pixel", asset.name)
            assertEquals("assets/temp_phone.glb", asset.fileName)
            assertEquals(2.5, asset.transform.position.x)
            assertEquals(1.0, asset.transform.position.y)
            assertEquals(-3.0, asset.transform.position.z)
            assertEquals(45.0, asset.transform.rotation.y)
            assertEquals(90.0, asset.transform.rotation.z)
            assertEquals(0.5, asset.transform.scale.x)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun bindingRoundTrip() {
        val (store, rootDir) = createTempStore()
        try {
            store.createScene(name = "Binding Test", id = "binding_test")
            val scene = DeviceScene(
                id = "binding_test",
                name = "Binding Test",
                bindings = listOf(
                    SceneBinding(
                        objectId = "Phone_Node_A",
                        deviceIdentity = DeviceIdentityRef(value = "hw-serial-ABC-123"),
                    ),
                    SceneBinding(
                        objectId = "TV_Node_B",
                        deviceIdentity = DeviceIdentityRef(value = "192.168.1.50:5555"),
                    ),
                ),
            )
            store.saveScene(scene)

            val loaded = store.loadScene("binding_test")
            assertEquals(2, loaded.bindings.size)
            val b1 = loaded.bindings.first { it.objectId == "Phone_Node_A" }
            assertEquals("hw-serial-ABC-123", b1.deviceIdentity.value)
            val b2 = loaded.bindings.first { it.objectId == "TV_Node_B" }
            assertEquals("192.168.1.50:5555", b2.deviceIdentity.value)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun environmentGlbCopied_intoManagedDirectory() {
        val (store, rootDir) = createTempStore()
        val tempSource = File(rootDir, "external_env.glb")
        createDummyGlb(tempSource)
        try {
            store.createScene(name = "Env Scene", id = "env_scene")
            store.importEnvironment(sceneId = "env_scene", sourceFile = tempSource)

            val sceneDir = File(rootDir, "env_scene")
            val managedEnvGlb = File(sceneDir, "environment.glb")
            assertTrue(managedEnvGlb.exists())
            assertTrue(managedEnvGlb.length() == tempSource.length())

            val loaded = store.loadScene("env_scene")
            assertNotNull(loaded.environment)
            assertEquals("environment.glb", loaded.environment.fileName)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun importedAssetCopied_intoAssetsDirectory() {
        val (store, rootDir) = createTempStore()
        val tempAsset = File(rootDir, "external_asset.glb")
        createDummyGlb(tempAsset)
        try {
            store.createScene(name = "Asset Scene", id = "asset_scene")
            store.importAsset(sceneId = "asset_scene", assetId = "phone1", sourceFile = tempAsset)

            val sceneDir = File(rootDir, "asset_scene")
            val managedAsset = File(sceneDir, "assets/external_asset.glb")
            assertTrue(managedAsset.exists())
            assertTrue(managedAsset.length() == tempAsset.length())

            val loaded = store.loadScene("asset_scene")
            assertEquals(1, loaded.assets.size)
            assertEquals("assets/external_asset.glb", loaded.assets[0].fileName)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun sourceFileDeletion_managedCopyRemainsValid() {
        val (store, rootDir) = createTempStore()
        val tempSource = File(rootDir, "disposable.glb")
        createDummyGlb(tempSource)
        try {
            store.createScene(name = "Delete Source Test", id = "del_test")
            store.importEnvironment(sceneId = "del_test", sourceFile = tempSource)

            // Delete original source file
            assertTrue(tempSource.delete())
            assertFalse(tempSource.exists())

            // Scene and managed copy must remain fully functional
            val loaded = store.loadScene("del_test")
            assertEquals("environment.glb", loaded.environment?.fileName)
            val managedFile = File(rootDir, "del_test/environment.glb")
            assertTrue(managedFile.exists())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun deleteRemovesManagedSceneDirectory() {
        val (store, rootDir) = createTempStore()
        val tempSource = File(rootDir, "test.glb")
        createDummyGlb(tempSource)
        try {
            store.createScene(name = "To Be Deleted", id = "to_delete")
            store.importEnvironment(sceneId = "to_delete", sourceFile = tempSource)
            store.importAsset(sceneId = "to_delete", assetId = "a1", sourceFile = tempSource)

            val sceneDir = File(rootDir, "to_delete")
            assertTrue(sceneDir.exists())

            val deleted = store.deleteScene("to_delete")
            assertTrue(deleted)
            assertFalse(sceneDir.exists())

            assertFailsWith<SceneNotFoundException> {
                store.loadScene("to_delete")
            }
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun malformedJson_throwsDomainException() {
        val (store, rootDir) = createTempStore()
        try {
            store.createScene(name = "Corrupt Test", id = "corrupt_scene")
            val manifest = File(rootDir, "corrupt_scene/scene.json")
            manifest.writeText("{ this is corrupted JSON !!!")

            assertFailsWith<SceneParseException> {
                store.loadScene("corrupt_scene")
            }
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun unsupportedSchemaVersion_throwsUnsupportedSchemaVersionException() {
        val (store, rootDir) = createTempStore()
        try {
            store.createScene(name = "Version Test", id = "version_scene")
            val manifest = File(rootDir, "version_scene/scene.json")
            manifest.writeText("""{"schemaVersion": 42, "id": "version_scene", "name": "V42"}""")

            val ex = assertFailsWith<UnsupportedSchemaVersionException> {
                store.loadScene("version_scene")
            }
            assertEquals(42, ex.actualVersion)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun invalidSceneId_pathTraversal_rejected() {
        val (store, rootDir) = createTempStore()
        try {
            val invalidIds = listOf(
                "../test",
                "..\\test",
                "",
                " ",
                "a/b",
                "a\\b",
                "scene/../escaped",
                "scene*",
                "scene:id",
            )
            for (badId in invalidIds) {
                assertFailsWith<SceneValidationException>("Should reject bad id '$badId'") {
                    store.createScene(name = "Test", id = badId)
                }
                assertFailsWith<SceneValidationException>("Should reject bad id '$badId'") {
                    store.loadScene(badId)
                }
                assertFailsWith<SceneValidationException>("Should reject bad id '$badId'") {
                    store.deleteScene(badId)
                }
            }
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun maliciousManifestResourcePath_escapingSceneDir_rejected() {
        val (store, rootDir) = createTempStore()
        try {
            store.createScene(name = "Path Traversal Test", id = "traversal_scene")
            val manifest = File(rootDir, "traversal_scene/scene.json")
            manifest.writeText(
                """
                {
                  "schemaVersion": 1,
                  "id": "traversal_scene",
                  "name": "Traversal",
                  "environment": {
                    "fileName": "../../../secret.txt"
                  }
                }
                """.trimIndent()
            )

            assertFailsWith<SceneValidationException> {
                store.loadScene("traversal_scene")
            }
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun manifestReferencingMissingResource_throwsSceneResourceNotFoundException() {
        val (store, rootDir) = createTempStore()
        try {
            store.createScene(name = "Missing Resource Test", id = "missing_res_scene")
            val manifest = File(rootDir, "missing_res_scene/scene.json")
            manifest.writeText(
                """
                {
                  "schemaVersion": 1,
                  "id": "missing_res_scene",
                  "name": "Missing Res",
                  "environment": {
                    "fileName": "missing.glb"
                  }
                }
                """.trimIndent()
            )

            val ex = assertFailsWith<SceneResourceNotFoundException> {
                store.loadScene("missing_res_scene")
            }
            assertEquals("missing_res_scene", ex.sceneId)
            assertEquals("missing.glb", ex.resourcePath)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun atomicSaveResilience_preservesExistingValidManifestOnFailure() {
        val (store, rootDir) = createTempStore()
        try {
            val scene = store.createScene(name = "Original Valid", id = "atomic_scene")
            val manifestFile = File(rootDir, "atomic_scene/scene.json")
            val originalContent = manifestFile.readText()

            // Attempt to save an invalid scene with escaping resource path
            val badScene = scene.copy(
                environment = `fun`.abbas.wps_adb.model.scene.SceneEnvironment(fileName = "../../../etc/passwd"),
            )

            assertFailsWith<SceneValidationException> {
                store.saveScene(badScene)
            }

            // Verify original file is still intact and unchanged
            assertEquals(originalContent, manifestFile.readText())
            assertFalse(File(rootDir, "atomic_scene/scene.json.tmp").exists())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun invalidGlb_rejectedOnImport() {
        val (store, rootDir) = createTempStore()
        val notGlb = File(rootDir, "fake.glb")
        notGlb.writeText("This is plain text, not binary glTF.")
        try {
            store.createScene(name = "Glb Reject", id = "glb_reject")
            assertFailsWith<InvalidGlbException> {
                store.importEnvironment(sceneId = "glb_reject", sourceFile = notGlb)
            }
        } finally {
            rootDir.deleteRecursively()
        }
    }
}
