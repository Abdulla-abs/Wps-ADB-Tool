package `fun`.abbas.wps_adb.model.scene

const val CURRENT_SCENE_SCHEMA_VERSION = 1

data class SceneVector3(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
) {
    companion object {
        val ZERO = SceneVector3(0.0, 0.0, 0.0)
        val ONE = SceneVector3(1.0, 1.0, 1.0)
    }
}

data class SceneTransform(
    val position: SceneVector3 = SceneVector3.ZERO,
    val rotation: SceneVector3 = SceneVector3.ZERO,
    val scale: SceneVector3 = SceneVector3.ONE,
)

data class SceneCamera(
    val position: SceneVector3 = SceneVector3(0.0, 5.0, 10.0),
    val target: SceneVector3 = SceneVector3.ZERO,
    val fov: Double = 45.0,
)

data class SceneEnvironment(
    val fileName: String = "environment.glb",
)

data class DeviceIdentityRef(
    val value: String,
)

data class SceneBinding(
    val objectId: String,
    val deviceIdentity: DeviceIdentityRef,
)

data class SceneAssetInstance(
    val id: String,
    val fileName: String,
    val name: String = id,
    val transform: SceneTransform = SceneTransform(),
)

data class DeviceScene(
    val schemaVersion: Int = CURRENT_SCENE_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val environment: SceneEnvironment? = null,
    val camera: SceneCamera = SceneCamera(),
    val assets: List<SceneAssetInstance> = emptyList(),
    val bindings: List<SceneBinding> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)
