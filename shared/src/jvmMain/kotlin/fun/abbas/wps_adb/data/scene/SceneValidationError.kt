package `fun`.abbas.wps_adb.data.scene

sealed interface SceneValidationError {
    data class FileNotFound(val path: String) : SceneValidationError
    data class InvalidFileExtension(val extension: String, val expected: String = "glb") : SceneValidationError
    data class FileTooLarge(val sizeBytes: Long, val maxSizeBytes: Long) : SceneValidationError
    data class InvalidGlbHeader(val details: String) : SceneValidationError
    data class SceneNotFound(val sceneId: String) : SceneValidationError
    data class InvalidId(val id: String, val details: String) : SceneValidationError
    data class StorageFailure(val details: String, val cause: Throwable? = null) : SceneValidationError
}
