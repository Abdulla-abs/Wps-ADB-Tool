package `fun`.abbas.wps_adb.data.scene

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.ResolvedSceneState

interface SceneBindingResolver {
    fun resolve(
        scene: DeviceScene,
        devices: List<Device>,
    ): ResolvedSceneState
}
