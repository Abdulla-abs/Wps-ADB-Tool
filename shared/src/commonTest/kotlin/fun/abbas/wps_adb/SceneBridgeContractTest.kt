package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.bridge.DefaultSceneBridgeSerializer
import `fun`.abbas.wps_adb.data.scene.bridge.DeviceVisualDescriptor
import `fun`.abbas.wps_adb.data.scene.bridge.SceneAssetDescriptor
import `fun`.abbas.wps_adb.data.scene.bridge.SceneBridgeMessage
import `fun`.abbas.wps_adb.data.scene.bridge.SceneCameraDescriptor
import `fun`.abbas.wps_adb.data.scene.bridge.SceneDescriptor
import `fun`.abbas.wps_adb.data.scene.bridge.SceneTransformDescriptor
import `fun`.abbas.wps_adb.data.scene.bridge.SceneVisualSnapshot
import `fun`.abbas.wps_adb.data.scene.bridge.VisualStyle
import `fun`.abbas.wps_adb.data.scene.bridge.VisualStatus
import `fun`.abbas.wps_adb.model.scene.SceneVector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneBridgeContractTest {

    private val serializer = DefaultSceneBridgeSerializer()

    @Test
    fun roundtrip_initScene_serializesAndDeserializesAccurately() {
        val message = SceneBridgeMessage.InitScene(
            sceneDescriptor = SceneDescriptor(
                id = "scene_alpha",
                name = "Scene Alpha",
                environmentFileName = "studio.hdr",
                camera = SceneCameraDescriptor(
                    position = SceneVector3(0.0, 5.0, 10.0),
                    target = SceneVector3(0.0, 0.0, 0.0),
                    fov = 45.0,
                ),
                assets = listOf(
                    SceneAssetDescriptor(
                        id = "phone_model",
                        fileName = "phone.glb",
                        name = "Phone Mesh",
                        transform = SceneTransformDescriptor(
                            position = SceneVector3(1.0, 2.0, 3.0),
                            rotation = SceneVector3(0.0, 90.0, 0.0),
                            scale = SceneVector3(1.0, 1.0, 1.0),
                        ),
                    ),
                ),
                bindableObjectIds = listOf("phone_model"),
            ),
            timestamp = 1000L,
        )

        val json = serializer.serialize(message)
        assertTrue(json.contains("\"type\":\"SCENE_INIT\""))

        val deserialized = serializer.deserialize(json)
        assertIs<SceneBridgeMessage.InitScene>(deserialized)
        assertEquals("scene_alpha", deserialized.sceneDescriptor.id)
        assertEquals("Scene Alpha", deserialized.sceneDescriptor.name)
        assertEquals("studio.hdr", deserialized.sceneDescriptor.environmentFileName)
        assertEquals(SceneVector3(0.0, 5.0, 10.0), deserialized.sceneDescriptor.camera.position)
        assertEquals(1, deserialized.sceneDescriptor.assets.size)
        assertEquals("phone_model", deserialized.sceneDescriptor.assets.first().id)
        assertEquals(listOf("phone_model"), deserialized.sceneDescriptor.bindableObjectIds)
    }

    @Test
    fun roundtrip_syncState_serializesAndDeserializesSnapshotWithDecoupledSelection() {
        val device = DeviceVisualDescriptor(
            objectId = "obj_pixel",
            deviceIdentity = "HW-PIXEL",
            displayName = "Pixel 8",
            status = VisualStatus.ONLINE,
            connectionType = "USB",
            visual = VisualStyle(
                statusColorHex = "#10B981",
                isEmissive = true,
                emissiveIntensity = 0.8f,
                badgeText = "ONLINE (USB)",
                isDimmed = false,
            ),
        )

        val message = SceneBridgeMessage.SyncState(
            snapshot = SceneVisualSnapshot(
                sceneId = "scene_beta",
                devices = listOf(device),
                selectedObjectId = "obj_pixel",
            ),
            timestamp = 2000L,
        )

        val json = serializer.serialize(message)
        assertTrue(json.contains("\"type\":\"STATE_SYNC\""))
        assertTrue(json.contains("\"selectedObjectId\":\"obj_pixel\""))

        val deserialized = serializer.deserialize(json)
        assertIs<SceneBridgeMessage.SyncState>(deserialized)
        assertEquals("scene_beta", deserialized.snapshot.sceneId)
        assertEquals("obj_pixel", deserialized.snapshot.selectedObjectId)
        assertEquals(1, deserialized.snapshot.devices.size)
        val dev = deserialized.snapshot.devices.first()
        assertEquals("obj_pixel", dev.objectId)
        assertEquals("HW-PIXEL", dev.deviceIdentity)
        assertEquals(VisualStatus.ONLINE, dev.status)
        assertEquals("#10B981", dev.visual.statusColorHex)
    }

    @Test
    fun roundtrip_selectionChange_serializesAndDeserializes() {
        val message = SceneBridgeMessage.UpdateSelection(
            selectedObjectId = "device_slot_3",
            focusCamera = true,
            timestamp = 3000L,
        )

        val json = serializer.serialize(message)
        val deserialized = serializer.deserialize(json)

        assertIs<SceneBridgeMessage.UpdateSelection>(deserialized)
        assertEquals("device_slot_3", deserialized.selectedObjectId)
        assertTrue(deserialized.focusCamera)
    }

    @Test
    fun roundtrip_rendererEvents_serializesAndDeserializes() {
        // 1. RendererReady
        val ready = SceneBridgeMessage.RendererReady("NVIDIA Corporation", "NVIDIA GeForce RTX 4090", 16384, 4000L)
        val readyJson = serializer.serialize(ready)
        val readyParsed = serializer.deserialize(readyJson)
        assertIs<SceneBridgeMessage.RendererReady>(readyParsed)
        assertEquals("NVIDIA Corporation", readyParsed.webglVendor)
        assertEquals("NVIDIA GeForce RTX 4090", readyParsed.webglRenderer)
        assertEquals(16384, readyParsed.maxTextureSize)

        // 2. ObjectClicked
        val clicked = SceneBridgeMessage.ObjectClicked("slot_1", 320f, 240f, isCtrlPressed = true, isShiftPressed = false, 5000L)
        val clickedJson = serializer.serialize(clicked)
        val clickedParsed = serializer.deserialize(clickedJson)
        assertIs<SceneBridgeMessage.ObjectClicked>(clickedParsed)
        assertEquals("slot_1", clickedParsed.objectId)
        assertEquals(320f, clickedParsed.screenX)
        assertTrue(clickedParsed.isCtrlPressed)

        // 3. ObjectHovered
        val hovered = SceneBridgeMessage.ObjectHovered("slot_2", 6000L)
        val hoveredJson = serializer.serialize(hovered)
        val hoveredParsed = serializer.deserialize(hoveredJson)
        assertIs<SceneBridgeMessage.ObjectHovered>(hoveredParsed)
        assertEquals("slot_2", hoveredParsed.objectId)

        // 4. ObjectTransformChanged
        val transform = SceneBridgeMessage.ObjectTransformChanged(
            objectId = "mesh_1",
            position = SceneVector3(1.0, 2.0, 3.0),
            rotation = SceneVector3(0.0, 45.0, 0.0),
            scale = SceneVector3(2.0, 2.0, 2.0),
            timestamp = 7000L,
        )
        val transformJson = serializer.serialize(transform)
        val transformParsed = serializer.deserialize(transformJson)
        assertIs<SceneBridgeMessage.ObjectTransformChanged>(transformParsed)
        assertEquals("mesh_1", transformParsed.objectId)
        assertEquals(SceneVector3(1.0, 2.0, 3.0), transformParsed.position)
        assertEquals(SceneVector3(2.0, 2.0, 2.0), transformParsed.scale)

        // 5. RendererError
        val error = SceneBridgeMessage.RendererError("WEBGL_CONTEXT_LOST", "GPU driver reset detected", 8000L)
        val errorJson = serializer.serialize(error)
        val errorParsed = serializer.deserialize(errorJson)
        assertIs<SceneBridgeMessage.RendererError>(errorParsed)
        assertEquals("WEBGL_CONTEXT_LOST", errorParsed.code)
    }

    @Test
    fun deserialize_unknownType_doesNotCrashAndReturnsUnknownFallback() {
        val futureJson = """
            {
              "type": "FUTURE_PHYSICS_SIMULATION_EVENT",
              "version": 2,
              "timestamp": 99999,
              "payload": {
                "gravity": -9.81,
                "collisions": ["obj_a", "obj_b"]
              }
            }
        """.trimIndent()

        val parsed = serializer.deserialize(futureJson)

        assertNotNull(parsed)
        assertIs<SceneBridgeMessage.Unknown>(parsed)
        assertEquals("FUTURE_PHYSICS_SIMULATION_EVENT", parsed.rawType)
        assertEquals(99999L, parsed.timestamp)
    }

    @Test
    fun deserialize_malformedJson_returnsNullGracefully() {
        val badJson = "{ broken json :: null "
        val parsed = serializer.deserialize(badJson)
        assertNull(parsed)
    }

    @Test
    fun deserialize_blankInput_returnsNullGracefully() {
        assertNull(serializer.deserialize(""))
        assertNull(serializer.deserialize("   "))
    }
}
