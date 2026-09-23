package `fun`.abbas.wps_adb.data.scene

import java.io.File
import java.util.UUID

interface SceneImporter {
    fun importEnvironment(sourceFile: File, sceneName: String? = null): SceneImportResult
    fun importAsset(sceneId: String, sourceFile: File, assetName: String? = null): SceneImportResult
}

class DefaultSceneImporter(
    private val sceneStore: SceneStore,
    private val maxFileSizeBytes: Long = DEFAULT_MAX_GLB_FILE_SIZE_BYTES,
) : SceneImporter {

    companion object {
        const val DEFAULT_MAX_GLB_FILE_SIZE_BYTES: Long = 100 * 1024 * 1024L // 100 MB
    }

    override fun importEnvironment(sourceFile: File, sceneName: String?): SceneImportResult {
        val validationError = validateFile(sourceFile)
        if (validationError != null) {
            return validationError
        }

        val cleanName = sceneName?.trim()?.ifBlank { null }
            ?: sourceFile.nameWithoutExtension.trim().ifBlank { "Imported Scene" }

        return try {
            val newScene = sceneStore.createScene(name = cleanName)
            val updated = sceneStore.importEnvironment(newScene.id, sourceFile)
            SceneImportResult.Success(updated)
        } catch (e: Exception) {
            SceneImportResult.Failure(
                error = SceneValidationError.StorageFailure(e.message ?: "Failed to import environment", e),
                message = e.message ?: "Storage failure during environment import",
            )
        }
    }

    override fun importAsset(sceneId: String, sourceFile: File, assetName: String?): SceneImportResult {
        val validationError = validateFile(sourceFile)
        if (validationError != null) {
            return validationError
        }

        try {
            sceneStore.loadScene(sceneId)
        } catch (_: SceneNotFoundException) {
            return SceneImportResult.Failure(
                error = SceneValidationError.SceneNotFound(sceneId),
                message = "Scene with ID '$sceneId' does not exist",
            )
        } catch (e: Exception) {
            return SceneImportResult.Failure(
                error = SceneValidationError.StorageFailure("Failed to load scene '$sceneId': ${e.message}", e),
                message = e.message ?: "Storage failure loading scene",
            )
        }

        val assetId = "asset_" + UUID.randomUUID().toString().replace("-", "").take(8)
        val cleanAssetName = assetName?.trim()?.ifBlank { null } ?: sourceFile.nameWithoutExtension

        return try {
            val updated = sceneStore.importAsset(
                sceneId = sceneId,
                assetId = assetId,
                sourceFile = sourceFile,
                customName = cleanAssetName,
            )
            SceneImportResult.Success(updated)
        } catch (e: Exception) {
            SceneImportResult.Failure(
                error = SceneValidationError.StorageFailure(e.message ?: "Failed to import asset", e),
                message = e.message ?: "Storage failure during asset import",
            )
        }
    }

    private fun validateFile(file: File): SceneImportResult.Failure? {
        if (!file.exists() || !file.isFile) {
            return SceneImportResult.Failure(
                error = SceneValidationError.FileNotFound(file.absolutePath),
                message = "File does not exist: ${file.absolutePath}",
            )
        }

        if (!file.name.endsWith(".glb", ignoreCase = true)) {
            val ext = file.extension.ifBlank { "<none>" }
            return SceneImportResult.Failure(
                error = SceneValidationError.InvalidFileExtension(ext),
                message = "Invalid file extension '$ext'. Only .glb files are supported.",
            )
        }

        val length = file.length()
        if (length > maxFileSizeBytes) {
            return SceneImportResult.Failure(
                error = SceneValidationError.FileTooLarge(length, maxFileSizeBytes),
                message = "File size of $length bytes exceeds maximum limit of $maxFileSizeBytes bytes",
            )
        }

        try {
            GlbValidator.validate(file)
        } catch (e: Exception) {
            return SceneImportResult.Failure(
                error = SceneValidationError.InvalidGlbHeader(e.message ?: "Invalid GLB header"),
                message = "GLB binary validation failed: ${e.message}",
            )
        }

        return null
    }
}
