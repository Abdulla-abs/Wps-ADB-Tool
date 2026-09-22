package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.DeviceSceneSerializer
import `fun`.abbas.wps_adb.data.scene.SceneParseException
import `fun`.abbas.wps_adb.data.scene.UnsupportedSchemaVersionException
import `fun`.abbas.wps_adb.model.scene.CURRENT_SCENE_SCHEMA_VERSION
import `fun`.abbas.wps_adb.model.scene.DeviceIdentityRef
import `fun`.abbas.wps_adb.model.scene.DeviceScene
import `fun`.abbas.wps_adb.model.scene.SceneAssetInstance
import `fun`.abbas.wps_adb.model.scene.SceneBinding
import `fun`.abbas.wps_adb.model.scene.SceneCamera
import `fun`.abbas.wps_adb.model.scene.SceneEnvironment
import `fun`.abbas.wps_adb.model.scene.SceneTransform
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeviceSceneSerializerTest {

    @Test
    fun roundTrip_fullScene() {
        val original = DeviceScene(
            schemaVersion = CURRENT_SCENE_SCHEMA_VERSION,
            id = "lab_scene",
            name = "My Lab",
            environment = SceneEnvironment(fileName = "environment.glb"),
            camera = SceneCamera(
                position = SceneVector3(1.0, 2.0, 3.0),
                target = SceneVector3(0.1, 0.2, 0.3),
                fov = 60.0,
            ),
            assets = listOf(
                SceneAssetInstance(
                    id = "desk_phone",
                    fileName = "assets/phone.glb",
                    name = "Desk Phone",
                    transform = SceneTransform(
                        position = SceneVector3(10.0, 20.0, 30.0),
                        rotation = SceneVector3(0.0, 90.0, 0.0),
                        scale = SceneVector3(2.0, 2.0, 2.0),
                    ),
                )
            ),
            bindings = listOf(
                SceneBinding(
                    objectId = "Desk_Phone_01",
                    deviceIdentity = DeviceIdentityRef(value = "device-placeholder-123"),
                )
            ),
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L,
        )

        val json = DeviceSceneSerializer.serialize(original)
        val parsed = DeviceSceneSerializer.deserialize(json)

        assertEquals(original.schemaVersion, parsed.schemaVersion)
        assertEquals(original.id, parsed.id)
        assertEquals(original.name, parsed.name)
        assertEquals(original.createdAtMillis, parsed.createdAtMillis)
        assertEquals(original.updatedAtMillis, parsed.updatedAtMillis)

        assertNotNull(parsed.environment)
        assertEquals("environment.glb", parsed.environment.fileName)

        assertEquals(1.0, parsed.camera.position.x)
        assertEquals(2.0, parsed.camera.position.y)
        assertEquals(3.0, parsed.camera.position.z)
        assertEquals(0.1, parsed.camera.target.x)
        assertEquals(0.2, parsed.camera.target.y)
        assertEquals(0.3, parsed.camera.target.z)
        assertEquals(60.0, parsed.camera.fov)

        assertEquals(1, parsed.assets.size)
        val asset = parsed.assets[0]
        assertEquals("desk_phone", asset.id)
        assertEquals("assets/phone.glb", asset.fileName)
        assertEquals("Desk Phone", asset.name)
        assertEquals(10.0, asset.transform.position.x)
        assertEquals(20.0, asset.transform.position.y)
        assertEquals(30.0, asset.transform.position.z)
        assertEquals(90.0, asset.transform.rotation.y)
        assertEquals(2.0, asset.transform.scale.x)

        assertEquals(1, parsed.bindings.size)
        val binding = parsed.bindings[0]
        assertEquals("Desk_Phone_01", binding.objectId)
        assertEquals("device-placeholder-123", binding.deviceIdentity.value)
    }

    @Test
    fun roundTrip_minimalScene_withoutEnvironmentOrAssets() {
        val original = DeviceScene(
            id = "minimal",
            name = "Minimal Scene",
        )
        val json = DeviceSceneSerializer.serialize(original)
        val parsed = DeviceSceneSerializer.deserialize(json)

        assertEquals(CURRENT_SCENE_SCHEMA_VERSION, parsed.schemaVersion)
        assertEquals("minimal", parsed.id)
        assertEquals("Minimal Scene", parsed.name)
        assertNull(parsed.environment)
        assertEquals(0, parsed.assets.size)
        assertEquals(0, parsed.bindings.size)
    }

    @Test
    fun deserialize_unsupportedSchemaVersion_throwsException() {
        val json = """
            {
              "schemaVersion": 999,
              "id": "future_scene",
              "name": "Future"
            }
        """.trimIndent()

        val ex = assertFailsWith<UnsupportedSchemaVersionException> {
            DeviceSceneSerializer.deserialize(json)
        }
        assertEquals(999, ex.actualVersion)
        assertEquals(CURRENT_SCENE_SCHEMA_VERSION, ex.supportedVersion)
    }

    @Test
    fun deserialize_missingSchemaVersion_throwsException() {
        val json = """
            {
              "id": "no_version",
              "name": "No Version"
            }
        """.trimIndent()

        val ex = assertFailsWith<UnsupportedSchemaVersionException> {
            DeviceSceneSerializer.deserialize(json)
        }
        assertEquals(-1, ex.actualVersion)
    }

    @Test
    fun deserialize_malformedJson_throwsSceneParseException() {
        assertFailsWith<SceneParseException> {
            DeviceSceneSerializer.deserialize("{ not valid json ")
        }
    }

    @Test
    fun deserialize_missingId_throwsSceneParseException() {
        val json = """
            {
              "schemaVersion": 1,
              "name": "Missing Id"
            }
        """.trimIndent()

        assertFailsWith<SceneParseException> {
            DeviceSceneSerializer.deserialize(json)
        }
    }

    @Test
    fun roundTrip_includesBindableObjectIds() {
        val original = DeviceScene(
            schemaVersion = CURRENT_SCENE_SCHEMA_VERSION,
            id = "custom_scene",
            name = "Custom Scene",
            bindableObjectIds = listOf("slot_a", "slot_b"),
        )
        val json = DeviceSceneSerializer.serialize(original)
        val parsed = DeviceSceneSerializer.deserialize(json)
        assertEquals(listOf("slot_a", "slot_b"), parsed.bindableObjectIds)
    }

    @Test
    fun deserialize_legacyDefaultSceneWithoutBindableField_migratesDefaultSlots() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "scene_default",
              "name": "Default 3D Scene"
            }
        """.trimIndent()
        val parsed = DeviceSceneSerializer.deserialize(json)
        assertEquals(
            `fun`.abbas.wps_adb.model.scene.DEFAULT_BINDABLE_OBJECT_IDS,
            parsed.bindableObjectIds,
        )
    }

    @Test
    fun deserialize_customSceneWithoutBindableField_defaultsEmptyList() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "other_scene",
              "name": "Other Scene"
            }
        """.trimIndent()
        val parsed = DeviceSceneSerializer.deserialize(json)
        assertEquals(emptyList(), parsed.bindableObjectIds)
    }
}
