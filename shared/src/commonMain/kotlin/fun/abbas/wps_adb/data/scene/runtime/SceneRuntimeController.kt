package `fun`.abbas.wps_adb.data.scene.runtime

import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState
import kotlinx.coroutines.flow.StateFlow

interface SceneRuntimeController {
    val activeScene: StateFlow<DeviceScene?>
    val resolvedState: StateFlow<ResolvedSceneState?>

    fun setScene(scene: DeviceScene?)
}
