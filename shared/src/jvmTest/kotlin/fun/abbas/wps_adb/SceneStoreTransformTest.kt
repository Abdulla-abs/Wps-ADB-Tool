package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.SceneStore
import `fun`.abbas.wps_adb.data.scene.SceneValidationException
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneStoreTransformTest {

    private fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "wps_store_transform_test_${System.nanoTime()}")
        dir.mkdirs()
        return dir
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
    fun updateTransform_importedAsset_updatesTransformSuccessfully() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val scene = store.createScene("Transform Test")
            val glb = File(root, "tablet.glb")
            createDummyGlb(glb)
            val withAsset = store.importAsset(scene.id, "asset_1", glb)

            val newTransform = SceneTransform(
                position = SceneVector3(1.0, 2.5, -3.0),
                rotation = SceneVector3(0.0, 90.0, 0.0),
                scale = SceneVector3(2.0, 2.0, 2.0),
            )

            val updated = store.updateTransform(scene.id, "asset_1", newTransform)
            val asset = updated.assets.single()
            assertEquals(newTransform, asset.transform)

            // Verify persisted in scene.json
            val loaded = store.loadScene(scene.id)
            assertEquals(newTransform, loaded.assets.single().transform)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun updateTransform_environmentObject_rejectedWithValidationException() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val scene = store.createScene("Env Test")

            val ex = assertFailsWith<SceneValidationException> {
                store.updateTransform(scene.id, "env_desk_object", SceneTransform())
            }
            assertTrue(ex.message!!.contains("Only imported assets can be transformed"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun updateTransform_nanOrInfinity_rejectedWithValidationException() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val scene = store.createScene("NaN Test")
            val glb = File(root, "item.glb")
            createDummyGlb(glb)
            store.importAsset(scene.id, "asset_nan", glb)

            assertFailsWith<SceneValidationException> {
                store.updateTransform(
                    scene.id,
                    "asset_nan",
                    SceneTransform(position = SceneVector3(Double.NaN, 0.0, 0.0))
                )
            }

            assertFailsWith<SceneValidationException> {
                store.updateTransform(
                    scene.id,
                    "asset_nan",
                    SceneTransform(rotation = SceneVector3(0.0, Double.POSITIVE_INFINITY, 0.0))
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun updateTransform_negativeOrZeroOrExcessiveScale_rejectedWithValidationException() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val scene = store.createScene("Scale Test")
            val glb = File(root, "item.glb")
            createDummyGlb(glb)
            store.importAsset(scene.id, "asset_scale", glb)

            // Zero scale
            assertFailsWith<SceneValidationException> {
                store.updateTransform(
                    scene.id,
                    "asset_scale",
                    SceneTransform(scale = SceneVector3(0.0, 1.0, 1.0))
                )
            }

            // Negative scale
            assertFailsWith<SceneValidationException> {
                store.updateTransform(
                    scene.id,
                    "asset_scale",
                    SceneTransform(scale = SceneVector3(1.0, -1.0, 1.0))
                )
            }

            // Excessive scale (> 1000)
            assertFailsWith<SceneValidationException> {
                store.updateTransform(
                    scene.id,
                    "asset_scale",
                    SceneTransform(scale = SceneVector3(1001.0, 1.0, 1.0))
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deleteAsset_manifestFirst_removesFromManifestAndDeletesFile() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val scene = store.createScene("Delete Test")
            val glb = File(root, "phone.glb")
            createDummyGlb(glb)

            val sceneWithAsset = store.importAsset(scene.id, "asset_to_del", glb)
            assertEquals(1, sceneWithAsset.assets.size)
            store.bindDevice(scene.id, "asset_to_del", DeviceIdentityRef("HW-123"))

            val assetFileName = sceneWithAsset.assets.single().fileName
            val assetFile = File(File(root, scene.id), assetFileName)
            assertTrue(assetFile.exists(), "Asset file should exist in managed directory")

            val afterDelete = store.deleteAsset(scene.id, "asset_to_del")
            assertTrue(afterDelete.assets.isEmpty(), "Asset should be removed from manifest")
            assertTrue(afterDelete.bindings.isEmpty(), "Associated binding should be removed from manifest")
            assertFalse(assetFile.exists(), "Asset file should be deleted from disk")

            val reloaded = store.loadScene(scene.id)
            assertTrue(reloaded.assets.isEmpty())
            assertTrue(reloaded.bindings.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
