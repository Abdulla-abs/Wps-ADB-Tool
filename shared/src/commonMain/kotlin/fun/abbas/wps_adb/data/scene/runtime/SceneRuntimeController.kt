package `fun`.abbas.wps_adb.data.scene.runtime

import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import kotlinx.coroutines.flow.StateFlow

interface SceneRuntimeController {
    val activeScene: StateFlow<DeviceScene?>
    val resolvedState: StateFlow<ResolvedSceneState?>
    val selectedObjectId: StateFlow<String?>
    val runtimeCamera: StateFlow<SceneCamera?>

    fun setScene(scene: DeviceScene?)
    fun updateScene(scene: DeviceScene)
    fun selectObject(objectId: String?)
    fun updateRuntimeCamera(camera: SceneCamera)
    fun updateRuntimeTransform(objectId: String, transform: SceneTransform)
}
