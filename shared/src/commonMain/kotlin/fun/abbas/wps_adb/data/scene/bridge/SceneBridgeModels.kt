package `fun`.abbas.wps_adb.data.scene.bridge

import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneVector3

/**
 * Camera configuration descriptor for the 3D scene.
 */
data class SceneCameraDescriptor(
    val position: SceneVector3,
    val target: SceneVector3,
    val fov: Double,
)

/**
 * Spatial transform descriptor for objects in the 3D scene.
 */
data class SceneTransformDescriptor(
    val position: SceneVector3,
    val rotation: SceneVector3,
    val scale: SceneVector3,
)

/**
 * 3D Asset instance descriptor.
 */
data class SceneAssetDescriptor(
    val id: String,
    val fileName: String,
    val name: String,
    val transform: SceneTransformDescriptor,
)

/**
 * High-level scene description passed to the 3D renderer on initialization.
 * Pure external contract decoupled from internal persistence details (like schemaVersion or timestamps).
 */
data class SceneDescriptor(
    val id: String,
    val name: String,
    val environmentFileName: String?,
    val camera: SceneCameraDescriptor,
    val assets: List<SceneAssetDescriptor>,
    val bindableObjectIds: List<String>,
)

/**
 * Runtime visual snapshot sent to the 3D renderer.
 * Keeps selection decoupled from individual device descriptors.
 */
data class SceneVisualSnapshot(
    val sceneId: String,
    val devices: List<DeviceVisualDescriptor>,
    val selectedObjectId: String? = null,
)

/**
 * Converts a persistent [DeviceScene] into an external [SceneDescriptor] for the bridge.
 */
fun DeviceScene.toDescriptor(): SceneDescriptor = SceneDescriptor(
    id = id,
    name = name,
    environmentFileName = environment?.fileName,
    camera = SceneCameraDescriptor(
        position = camera.position,
        target = camera.target,
        fov = camera.fov,
    ),
    assets = assets.map { asset ->
        SceneAssetDescriptor(
            id = asset.id,
            fileName = asset.fileName,
            name = asset.name,
            transform = SceneTransformDescriptor(
                position = asset.transform.position,
                rotation = asset.transform.rotation,
                scale = asset.transform.scale,
            ),
        )
    },
    bindableObjectIds = (bindableObjectIds + bindings.map { it.objectId }).distinct(),
)
