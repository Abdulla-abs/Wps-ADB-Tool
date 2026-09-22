package `fun`.abbas.wps_adb.data.scene

import `fun`.abbas.wps_adb.data.AppDataPaths
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneAssetInstance
import `fun`.abbas.wps_adb.model.scene.SceneBinding
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneEnvironment
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class SceneStore(
    private val scenesRoot: File = AppDataPaths.defaultScenesRoot(),
) : DeviceSceneRepository {

    private val sceneIdRegex = Regex("^[a-zA-Z0-9_-]+$")

    fun getScenesRoot(): File = scenesRoot

    override fun listScenes(): List<DeviceScene> {
        if (!scenesRoot.exists() || !scenesRoot.isDirectory) {
            return emptyList()
        }
        val subDirs = scenesRoot.listFiles { file -> file.isDirectory } ?: return emptyList()
        val result = mutableListOf<DeviceScene>()
        for (dir in subDirs) {
            val sceneJson = File(dir, "scene.json")
            if (sceneJson.exists() && sceneJson.isFile) {
                try {
                    result.add(loadScene(dir.name))
                } catch (_: Exception) {
                    // Gracefully skip corrupted or invalid scenes during list
                }
            }
        }
        return result.sortedByDescending { it.updatedAtMillis }
    }

    override fun loadScene(id: String): DeviceScene {
        validateSceneId(id)
        val sceneDir = resolveSceneDir(id)
        val manifestFile = File(sceneDir, "scene.json")
        if (!manifestFile.exists() || !manifestFile.isFile) {
            throw SceneNotFoundException(id)
        }

        val content = manifestFile.readText(Charsets.UTF_8)
        val scene = DeviceSceneSerializer.deserialize(content)

        // Validate that manifest ID matches folder ID
        if (scene.id != id) {
            throw SceneValidationException("Scene manifest ID '${scene.id}' does not match directory name '$id'")
        }

        // Validate that manifest resource references exist and do not escape
        scene.environment?.let { env ->
            validateAndResolveResource(sceneDir, env.fileName, scene.id)
        }
        for (asset in scene.assets) {
            validateAndResolveResource(sceneDir, asset.fileName, scene.id)
        }

        return scene
    }

    override fun saveScene(scene: DeviceScene): DeviceScene {
        validateSceneId(scene.id)
        val sceneDir = resolveSceneDir(scene.id)
        if (!sceneDir.exists()) {
            sceneDir.mkdirs()
        }

        // Verify that resource paths in the manifest do not escape the scene directory
        scene.environment?.let { env ->
            validateResourcePathSyntax(env.fileName)
        }
        for (asset in scene.assets) {
            validateResourcePathSyntax(asset.fileName)
        }

        val json = DeviceSceneSerializer.serialize(scene)
        val targetFile = File(sceneDir, "scene.json")
        val tempFile = File(sceneDir, "scene.json.tmp")

        try {
            FileOutputStream(tempFile).use { fos ->
                val bytes = json.toByteArray(Charsets.UTF_8)
                fos.write(bytes)
                fos.flush()
                fos.fd.sync()
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw SceneStorageException("Failed to atomically save scene '${scene.id}': ${e.message}", e)
        }

        return scene
    }

    override fun createScene(name: String, id: String?): DeviceScene {
        val finalId = if (id == null) {
            val slug = name.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(24).trim('_')
            val suffix = UUID.randomUUID().toString().take(8)
            if (slug.isNotBlank()) "${slug}_$suffix" else "scene_$suffix"
        } else {
            id
        }

        validateSceneId(finalId)
        val sceneDir = resolveSceneDir(finalId)
        if (sceneDir.exists() && File(sceneDir, "scene.json").exists()) {
            throw SceneValidationException("Scene with ID '$finalId' already exists")
        }

        sceneDir.mkdirs()
        val now = System.currentTimeMillis()
        val newScene = DeviceScene(
            id = finalId,
            name = name.trim().ifBlank { finalId },
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        return saveScene(newScene)
    }

    override fun deleteScene(id: String): Boolean {
        validateSceneId(id)
        val sceneDir = resolveSceneDir(id)
        if (!sceneDir.exists()) {
            return false
        }
        return sceneDir.deleteRecursively()
    }

    override fun importEnvironment(sceneId: String, sourceFile: File): DeviceScene {
        validateSceneId(sceneId)
        val scene = loadSceneOrExisting(sceneId)
        val sceneDir = resolveSceneDir(sceneId)

        GlbValidator.validate(sourceFile)

        val targetFile = File(sceneDir, "environment.glb")
        val tempTarget = File(sceneDir, "environment.glb.tmp")
        try {
            Files.copy(sourceFile.toPath(), tempTarget.toPath(), StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(
                    tempTarget.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempTarget.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (e: Exception) {
            tempTarget.delete()
            throw SceneStorageException("Failed to copy environment GLB into managed scene: ${e.message}", e)
        }

        val updatedScene = scene.copy(
            environment = SceneEnvironment(fileName = "environment.glb"),
            updatedAtMillis = System.currentTimeMillis(),
        )
        return saveScene(updatedScene)
    }

    override fun importAsset(
        sceneId: String,
        assetId: String,
        sourceFile: File,
        transform: SceneTransform,
        customName: String?,
    ): DeviceScene {
        validateSceneId(sceneId)
        validateSceneId(assetId)
        val scene = loadSceneOrExisting(sceneId)
        val sceneDir = resolveSceneDir(sceneId)

        GlbValidator.validate(sourceFile)

        val assetsDir = File(sceneDir, "assets")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }

        val rawName = sourceFile.name
        val safeBaseName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").let {
            if (it.endsWith(".glb", ignoreCase = true)) it else "$it.glb"
        }
        val safeAssetId = assetId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeName = "${safeAssetId}_$safeBaseName"

        val targetFile = File(assetsDir, safeName)
        val canonicalAssetsDir = assetsDir.canonicalFile
        if (!targetFile.canonicalFile.toPath().startsWith(canonicalAssetsDir.toPath())) {
            throw SceneValidationException("Asset filename escapes assets directory: $safeName")
        }

        val tempTarget = File(assetsDir, "$safeName.tmp")
        try {
            Files.copy(sourceFile.toPath(), tempTarget.toPath(), StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(
                    tempTarget.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempTarget.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (e: Exception) {
            tempTarget.delete()
            throw SceneStorageException("Failed to copy asset GLB into managed scene: ${e.message}", e)
        }

        val relativePath = "assets/$safeName"
        val newAsset = SceneAssetInstance(
            id = assetId,
            fileName = relativePath,
            name = customName ?: assetId,
            transform = transform,
        )

        val updatedAssets = scene.assets.filterNot { it.id == assetId } + newAsset
        val updatedScene = scene.copy(
            assets = updatedAssets,
            updatedAtMillis = System.currentTimeMillis(),
        )
        return saveScene(updatedScene)
    }

    override fun bindDevice(
        sceneId: String,
        objectId: String,
        deviceIdentity: DeviceIdentityRef,
    ): DeviceScene {
        validateSceneId(sceneId)
        if (objectId.isBlank()) throw SceneValidationException("Object ID cannot be blank")
        val scene = loadSceneOrExisting(sceneId)
        val newBinding = SceneBinding(
            objectId = objectId,
            deviceIdentity = deviceIdentity,
        )
        val updatedBindings = scene.bindings.filterNot { it.objectId == objectId } + newBinding
        val updatedScene = scene.copy(
            bindings = updatedBindings,
            updatedAtMillis = System.currentTimeMillis(),
        )
        return saveScene(updatedScene)
    }

    override fun unbindDevice(sceneId: String, objectId: String): DeviceScene {
        validateSceneId(sceneId)
        if (objectId.isBlank()) throw SceneValidationException("Object ID cannot be blank")
        val scene = loadSceneOrExisting(sceneId)
        val updatedBindings = scene.bindings.filterNot { it.objectId == objectId }
        val updatedScene = scene.copy(
            bindings = updatedBindings,
            updatedAtMillis = System.currentTimeMillis(),
        )
        return saveScene(updatedScene)
    }

    override fun updateBinding(
        sceneId: String,
        objectId: String,
        deviceIdentity: DeviceIdentityRef?,
    ): DeviceScene {
        return if (deviceIdentity != null) {
            bindDevice(sceneId, objectId, deviceIdentity)
        } else {
            unbindDevice(sceneId, objectId)
        }
    }

    override fun saveCamera(sceneId: String, camera: SceneCamera): DeviceScene {
        validateSceneId(sceneId)
        val scene = loadSceneOrExisting(sceneId)
        val updatedScene = scene.copy(
            camera = camera,
            updatedAtMillis = System.currentTimeMillis(),
        )
        return saveScene(updatedScene)
    }

    private fun loadSceneOrExisting(sceneId: String): DeviceScene {
        val sceneDir = resolveSceneDir(sceneId)
        val manifest = File(sceneDir, "scene.json")
        return if (manifest.exists()) {
            val content = manifest.readText(Charsets.UTF_8)
            DeviceSceneSerializer.deserialize(content)
        } else {
            throw SceneNotFoundException(sceneId)
        }
    }

    private fun validateSceneId(id: String) {
        if (id.isBlank() || !sceneIdRegex.matches(id)) {
            throw SceneValidationException(
                "Invalid scene ID '$id'. Scene IDs must be non-blank and match regex '^[a-zA-Z0-9_-]+$'"
            )
        }
    }

    private fun resolveSceneDir(id: String): File {
        val dir = File(scenesRoot, id)
        val canonicalRoot = scenesRoot.canonicalFile
        val canonicalDir = dir.canonicalFile
        if (!canonicalDir.toPath().startsWith(canonicalRoot.toPath()) || canonicalDir == canonicalRoot) {
            throw SceneValidationException("Scene ID '$id' escapes scenes root directory")
        }
        return dir
    }

    private fun validateResourcePathSyntax(relativePath: String) {
        if (relativePath.isBlank()) {
            throw SceneValidationException("Resource path in manifest cannot be blank")
        }
        if (File(relativePath).isAbsolute || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw SceneValidationException("Manifest resource path must be relative, found: $relativePath")
        }
        if (relativePath.contains("..")) {
            throw SceneValidationException("Path traversal detected in manifest resource path: $relativePath")
        }
    }

    private fun validateAndResolveResource(sceneDir: File, relativePath: String, sceneId: String): File {
        validateResourcePathSyntax(relativePath)
        val resolved = File(sceneDir, relativePath).canonicalFile
        val canonicalSceneDir = sceneDir.canonicalFile
        if (!resolved.toPath().startsWith(canonicalSceneDir.toPath())) {
            throw SceneValidationException("Resource path escapes scene directory: $relativePath")
        }
        if (!resolved.exists() || !resolved.isFile) {
            throw SceneResourceNotFoundException(sceneId = sceneId, resourcePath = relativePath)
        }
        return resolved
    }
}
