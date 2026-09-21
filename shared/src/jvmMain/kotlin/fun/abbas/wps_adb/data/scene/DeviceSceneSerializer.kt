package `fun`.abbas.wps_adb.data.scene

import `fun`.abbas.wps_adb.model.scene.CURRENT_SCENE_SCHEMA_VERSION
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneAssetInstance
import `fun`.abbas.wps_adb.model.scene.SceneBinding
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneEnvironment
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object DeviceSceneSerializer {

    fun serialize(scene: DeviceScene): String {
        val root = JSONObject()
        root.put("schemaVersion", scene.schemaVersion)
        root.put("id", scene.id)
        root.put("name", scene.name)
        root.put("createdAtMillis", scene.createdAtMillis)
        root.put("updatedAtMillis", scene.updatedAtMillis)

        scene.environment?.let { env ->
            val envObj = JSONObject()
            envObj.put("fileName", env.fileName)
            root.put("environment", envObj)
        }

        val cameraObj = JSONObject()
        cameraObj.put("position", vectorToJson(scene.camera.position))
        cameraObj.put("target", vectorToJson(scene.camera.target))
        cameraObj.put("fov", scene.camera.fov)
        root.put("camera", cameraObj)

        val assetsArray = JSONArray()
        for (asset in scene.assets) {
            val assetObj = JSONObject()
            assetObj.put("id", asset.id)
            assetObj.put("fileName", asset.fileName)
            assetObj.put("name", asset.name)
            val transformObj = JSONObject()
            transformObj.put("position", vectorToJson(asset.transform.position))
            transformObj.put("rotation", vectorToJson(asset.transform.rotation))
            transformObj.put("scale", vectorToJson(asset.transform.scale))
            assetObj.put("transform", transformObj)
            assetsArray.put(assetObj)
        }
        root.put("assets", assetsArray)

        val bindingsArray = JSONArray()
        for (binding in scene.bindings) {
            val bindingObj = JSONObject()
            bindingObj.put("objectId", binding.objectId)
            val identityObj = JSONObject()
            identityObj.put("value", binding.deviceIdentity.value)
            bindingObj.put("deviceIdentity", identityObj)
            bindingsArray.put(bindingObj)
        }
        root.put("bindings", bindingsArray)

        return root.toString(2)
    }

    fun deserialize(json: String): DeviceScene {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw SceneParseException("Malformed scene JSON: ${e.message}", e)
        }

        if (!root.has("schemaVersion")) {
            throw UnsupportedSchemaVersionException(actualVersion = -1)
        }
        val schemaVersion = try {
            root.getInt("schemaVersion")
        } catch (_: Exception) {
            throw UnsupportedSchemaVersionException(actualVersion = -1)
        }
        if (schemaVersion != CURRENT_SCENE_SCHEMA_VERSION) {
            throw UnsupportedSchemaVersionException(actualVersion = schemaVersion)
        }

        val id = root.optString("id", "").takeIf { it.isNotBlank() }
            ?: throw SceneParseException("Scene JSON missing or empty 'id'")
        val name = root.optString("name", id)
        val createdAtMillis = root.optLong("createdAtMillis", 0L)
        val updatedAtMillis = root.optLong("updatedAtMillis", 0L)

        val environment = root.optJSONObject("environment")?.let { envObj ->
            val fileName = envObj.optString("fileName", "").takeIf { it.isNotBlank() }
                ?: envObj.optString("glbPath", "").takeIf { it.isNotBlank() }
                ?: "environment.glb"
            SceneEnvironment(fileName = fileName)
        }

        val camera = root.optJSONObject("camera")?.let { camObj ->
            SceneCamera(
                position = camObj.optJSONObject("position")?.let(::jsonToVector) ?: SceneVector3(0.0, 5.0, 10.0),
                target = camObj.optJSONObject("target")?.let(::jsonToVector) ?: SceneVector3.ZERO,
                fov = camObj.optDouble("fov", 45.0),
            )
        } ?: SceneCamera()

        val assets = mutableListOf<SceneAssetInstance>()
        root.optJSONArray("assets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val assetObj = arr.optJSONObject(i) ?: continue
                val assetId = assetObj.optString("id", "")
                if (assetId.isBlank()) continue
                val fileName = assetObj.optString("fileName", "").takeIf { it.isNotBlank() }
                    ?: assetObj.optString("glbPath", "").takeIf { it.isNotBlank() }
                    ?: continue
                val assetName = assetObj.optString("name", assetId)
                val transformObj = assetObj.optJSONObject("transform")
                val transform = if (transformObj != null) {
                    SceneTransform(
                        position = transformObj.optJSONObject("position")?.let(::jsonToVector) ?: SceneVector3.ZERO,
                        rotation = transformObj.optJSONObject("rotation")?.let(::jsonToVector) ?: SceneVector3.ZERO,
                        scale = transformObj.optJSONObject("scale")?.let(::jsonToVector) ?: SceneVector3.ONE,
                    )
                } else {
                    SceneTransform()
                }
                assets.add(
                    SceneAssetInstance(
                        id = assetId,
                        fileName = fileName,
                        name = assetName,
                        transform = transform,
                    )
                )
            }
        }

        val bindings = mutableListOf<SceneBinding>()
        root.optJSONArray("bindings")?.let { arr ->
            for (i in 0 until arr.length()) {
                val bindingObj = arr.optJSONObject(i) ?: continue
                val objectId = bindingObj.optString("objectId", "")
                if (objectId.isBlank()) continue
                val identityValue = when {
                    bindingObj.has("deviceIdentity") && bindingObj.optJSONObject("deviceIdentity") != null -> {
                        bindingObj.getJSONObject("deviceIdentity").optString("value", "")
                    }
                    bindingObj.has("deviceIdentity") && bindingObj.optString("deviceIdentity", "").isNotBlank() -> {
                        bindingObj.getString("deviceIdentity")
                    }
                    else -> ""
                }
                bindings.add(
                    SceneBinding(
                        objectId = objectId,
                        deviceIdentity = DeviceIdentityRef(value = identityValue),
                    )
                )
            }
        }

        return DeviceScene(
            schemaVersion = schemaVersion,
            id = id,
            name = name,
            environment = environment,
            camera = camera,
            assets = assets,
            bindings = bindings,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun vectorToJson(vec: SceneVector3): JSONObject = JSONObject().apply {
        put("x", vec.x)
        put("y", vec.y)
        put("z", vec.z)
    }

    private fun jsonToVector(obj: JSONObject): SceneVector3 = SceneVector3(
        x = obj.optDouble("x", 0.0),
        y = obj.optDouble("y", 0.0),
        z = obj.optDouble("z", 0.0),
    )
}
