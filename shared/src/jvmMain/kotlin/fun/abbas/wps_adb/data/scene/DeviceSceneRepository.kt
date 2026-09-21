package `fun`.abbas.wps_adb.data.scene

import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import java.io.File

interface DeviceSceneRepository {
    fun listScenes(): List<DeviceScene>
    fun loadScene(id: String): DeviceScene
    fun saveScene(scene: DeviceScene): DeviceScene
    fun deleteScene(id: String): Boolean
    fun createScene(name: String, id: String? = null): DeviceScene
    fun importEnvironment(sceneId: String, sourceFile: File): DeviceScene
    fun importAsset(
        sceneId: String,
        assetId: String,
        sourceFile: File,
        transform: SceneTransform = SceneTransform(),
        customName: String? = null,
    ): DeviceScene
}
