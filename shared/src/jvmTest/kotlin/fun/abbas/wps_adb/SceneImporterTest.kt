package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.DefaultSceneImporter
import `fun`.abbas.wps_adb.data.scene.SceneImportResult
import `fun`.abbas.wps_adb.data.scene.SceneStore
import `fun`.abbas.wps_adb.data.scene.SceneValidationError
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneImporterTest {

    private fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "wps_importer_test_${System.nanoTime()}")
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
    fun importEnvironment_nonExistentFile_returnsFileNotFound() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val importer = DefaultSceneImporter(store)
            val missingFile = File(root, "non_existent.glb")

            val result = importer.importEnvironment(missingFile)
            assertTrue(result.isFailure)
            val failure = result as SceneImportResult.Failure
            assertTrue(failure.error is SceneValidationError.FileNotFound)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importEnvironment_nonGlbExtension_returnsInvalidExtension() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val importer = DefaultSceneImporter(store)
            val txtFile = File(root, "model.txt").apply { writeText("hello") }

            val result = importer.importEnvironment(txtFile)
            assertTrue(result.isFailure)
            val failure = result as SceneImportResult.Failure
            assertTrue(failure.error is SceneValidationError.InvalidFileExtension)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importEnvironment_fileTooLarge_returnsFileTooLarge() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val importer = DefaultSceneImporter(store, maxFileSizeBytes = 10L)
            val glbFile = File(root, "test.glb")
            createDummyGlb(glbFile) // length is 28 bytes > 10 bytes

            val result = importer.importEnvironment(glbFile)
            assertTrue(result.isFailure)
            val failure = result as SceneImportResult.Failure
            assertTrue(failure.error is SceneValidationError.FileTooLarge)
            assertEquals(28L, (failure.error as SceneValidationError.FileTooLarge).sizeBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importEnvironment_corruptedGlb_returnsInvalidGlbHeader() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val importer = DefaultSceneImporter(store)
            val fakeGlb = File(root, "fake.glb").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)) }

            val result = importer.importEnvironment(fakeGlb)
            assertTrue(result.isFailure)
            val failure = result as SceneImportResult.Failure
            assertTrue(failure.error is SceneValidationError.InvalidGlbHeader)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importEnvironment_validGlb_createsSceneAndImports() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val importer = DefaultSceneImporter(store)
            val validGlb = File(root, "office_room.glb")
            createDummyGlb(validGlb)

            val result = importer.importEnvironment(validGlb, sceneName = "Office Staging")
            assertTrue(result.isSuccess)
            val scene = result.getOrThrow()
            assertEquals("Office Staging", scene.name)
            assertNotNull(scene.environment)
            assertEquals("environment.glb", scene.environment?.fileName)

            // Verify stored in managed directory
            val loaded = store.loadScene(scene.id)
            assertEquals(scene.id, loaded.id)
            assertEquals("environment.glb", loaded.environment?.fileName)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importAsset_sceneNotFound_returnsSceneNotFound() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val importer = DefaultSceneImporter(store)
            val validGlb = File(root, "phone.glb")
            createDummyGlb(validGlb)

            val result = importer.importAsset("missing_scene", validGlb)
            assertTrue(result.isFailure)
            val failure = result as SceneImportResult.Failure
            assertTrue(failure.error is SceneValidationError.SceneNotFound)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importAsset_validGlb_importsAssetSuccessfully() {
        val root = createTempDir()
        try {
            val store = SceneStore(scenesRoot = root)
            val importer = DefaultSceneImporter(store)
            val scene = store.createScene(name = "Target Scene")

            val validGlb = File(root, "phone.glb")
            createDummyGlb(validGlb)

            val result = importer.importAsset(scene.id, validGlb, assetName = "Pixel 8 Pro")
            assertTrue(result.isSuccess)
            val updated = result.getOrThrow()
            assertEquals(1, updated.assets.size)
            assertEquals("Pixel 8 Pro", updated.assets.single().name)
        } finally {
            root.deleteRecursively()
        }
    }
}
