package `fun`.abbas.wps_adb.data.scene

import `fun`.abbas.wps_adb.model.scene.DeviceScene

sealed interface SceneImportResult {
    data class Success(val scene: DeviceScene) : SceneImportResult
    data class Failure(val error: SceneValidationError, val message: String) : SceneImportResult

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): DeviceScene? = (this as? Success)?.scene

    fun getOrThrow(): DeviceScene = when (this) {
        is Success -> scene
        is Failure -> throw SceneStorageException("Import failed: $message ($error)")
    }
}
