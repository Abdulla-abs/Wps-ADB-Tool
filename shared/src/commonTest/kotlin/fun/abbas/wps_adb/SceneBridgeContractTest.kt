package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeErrorCodes
import `fun`.abbas.wps_adb.data.scene.bridge.CURRENT_BRIDGE_PROTOCOL_VERSION
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

    private fun assertEnvelopeInvariants(json: String, expectedType: String) {
        assertTrue(json.contains("\"type\":\"$expectedType\""), "Missing expected wire type: $expectedType")
        assertTrue(json.contains("\"version\":$CURRENT_BRIDGE_PROTOCOL_VERSION"), "Missing or invalid protocol version")
        assertTrue(json.contains("\"timestamp\":"), "Missing timestamp field in envelope")
        assertTrue(json.contains("\"payload\":{"), "Missing payload object in envelope")
    }

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
        assertEnvelopeInvariants(json, "SCENE_INIT")

        val deserialized = serializer.deserialize(json)
        assertIs<SceneBridgeMessage.InitScene>(deserialized)
        assertTrue(deserialized.timestamp >= 0L, "Timestamp must be >= 0")
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
        assertEnvelopeInvariants(json, "STATE_SYNC")
        assertTrue(json.contains("\"selectedObjectId\":\"obj_pixel\""))

        val deserialized = serializer.deserialize(json)
        assertIs<SceneBridgeMessage.SyncState>(deserialized)
        assertTrue(deserialized.timestamp >= 0L, "Timestamp must be >= 0")
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
        val message = SceneBridgeMessage.SelectionChange(
            selectedObjectId = "device_slot_3",
            focusCamera = true,
            timestamp = 3000L,
        )

        val json = serializer.serialize(message)
        assertEnvelopeInvariants(json, "SELECTION_CHANGE")

        val deserialized = serializer.deserialize(json)
        assertIs<SceneBridgeMessage.SelectionChange>(deserialized)
        assertTrue(deserialized.timestamp >= 0L, "Timestamp must be >= 0")
        assertEquals("device_slot_3", deserialized.selectedObjectId)
        assertTrue(deserialized.focusCamera)
    }

    @Test
    fun roundtrip_rendererReady_serializesDecoupledProtocolHandshake() {
        val ready = SceneBridgeMessage.RendererReady(
            protocolVersion = CURRENT_BRIDGE_PROTOCOL_VERSION,
            rendererVersion = "1.0",
            timestamp = 4000L,
        )
        val readyJson = serializer.serialize(ready)
        assertEnvelopeInvariants(readyJson, "RENDERER_READY")
        assertTrue(readyJson.contains("\"protocolVersion\":$CURRENT_BRIDGE_PROTOCOL_VERSION"))
        assertTrue(readyJson.contains("\"rendererVersion\":\"1.0\""))

        val readyParsed = serializer.deserialize(readyJson)
        assertIs<SceneBridgeMessage.RendererReady>(readyParsed)
        assertTrue(readyParsed.timestamp >= 0L, "Timestamp must be >= 0")
        assertEquals(CURRENT_BRIDGE_PROTOCOL_VERSION, readyParsed.version)
        assertEquals(CURRENT_BRIDGE_PROTOCOL_VERSION, readyParsed.protocolVersion)
        assertEquals("1.0", readyParsed.rendererVersion)
    }

    @Test
    fun deserialize_rendererReady_preservesDistinctEnvelopeAndProtocolVersions() {
        val rawJson = """
            {
              "type": "RENDERER_READY",
              "version": 2,
              "timestamp": 5000,
              "payload": {
                "protocolVersion": 3,
                "rendererVersion": "2.0"
              }
            }
        """.trimIndent()
        val parsed = serializer.deserialize(rawJson)
        assertIs<SceneBridgeMessage.RendererReady>(parsed)
        assertEquals(2, parsed.version)
        assertEquals(3, parsed.protocolVersion)
        assertEquals("2.0", parsed.rendererVersion)
    }

    @Test
    fun roundtrip_deviceVisual_supportsUnboundStatus() {
        val unboundDevice = DeviceVisualDescriptor(
            objectId = "slot_empty",
            deviceIdentity = null,
            displayName = "slot_empty",
            status = VisualStatus.UNBOUND,
            connectionType = "NONE",
            visual = VisualStyle(
                statusColorHex = "#3B82F6",
                isEmissive = false,
                emissiveIntensity = 0f,
                badgeText = "UNBOUND",
                isDimmed = false,
            ),
        )
        val message = SceneBridgeMessage.UpdateBinding(device = unboundDevice, timestamp = 3500L)
        val json = serializer.serialize(message)
        assertTrue(json.contains("\"status\":\"UNBOUND\""))

        val deserialized = serializer.deserialize(json)
        assertIs<SceneBridgeMessage.UpdateBinding>(deserialized)
        assertEquals(VisualStatus.UNBOUND, deserialized.device.status)
        assertEquals("slot_empty", deserialized.device.displayName)
        assertEquals("#3B82F6", deserialized.device.visual.statusColorHex)
    }

    @Test
    fun roundtrip_rendererError_serializesBaseCodeAndCategory() {
        val error = SceneBridgeMessage.RendererError(
            code = BridgeErrorCodes.PROTOCOL_VERSION_MISMATCH,
            message = "Bridge protocol version 2 not supported, expected 1",
            category = "HANDSHAKE",
            timestamp = 8000L,
        )
        val errorJson = serializer.serialize(error)
        assertEnvelopeInvariants(errorJson, "RENDERER_ERROR")
        assertTrue(errorJson.contains("\"code\":\"PROTOCOL_VERSION_MISMATCH\""))
        assertTrue(errorJson.contains("\"category\":\"HANDSHAKE\""))

        val errorParsed = serializer.deserialize(errorJson)
        assertIs<SceneBridgeMessage.RendererError>(errorParsed)
        assertTrue(errorParsed.timestamp >= 0L, "Timestamp must be >= 0")
        assertEquals(BridgeErrorCodes.PROTOCOL_VERSION_MISMATCH, errorParsed.code)
        assertEquals("Bridge protocol version 2 not supported, expected 1", errorParsed.message)
        assertEquals("HANDSHAKE", errorParsed.category)
    }

    @Test
    fun roundtrip_otherRendererEvents_serializesAndDeserializes() {
        // 1. ObjectClicked
        val clicked = SceneBridgeMessage.ObjectClicked("slot_1", 320f, 240f, isCtrlPressed = true, isShiftPressed = false, 5000L)
        val clickedJson = serializer.serialize(clicked)
        assertEnvelopeInvariants(clickedJson, "OBJECT_CLICKED")
        val clickedParsed = serializer.deserialize(clickedJson)
        assertIs<SceneBridgeMessage.ObjectClicked>(clickedParsed)
        assertTrue(clickedParsed.timestamp >= 0L)
        assertEquals("slot_1", clickedParsed.objectId)
        assertEquals(320f, clickedParsed.screenX)
        assertTrue(clickedParsed.isCtrlPressed)

        // 2. ObjectHovered
        val hovered = SceneBridgeMessage.ObjectHovered("slot_2", 6000L)
        val hoveredJson = serializer.serialize(hovered)
        assertEnvelopeInvariants(hoveredJson, "OBJECT_HOVERED")
        val hoveredParsed = serializer.deserialize(hoveredJson)
        assertIs<SceneBridgeMessage.ObjectHovered>(hoveredParsed)
        assertTrue(hoveredParsed.timestamp >= 0L)
        assertEquals("slot_2", hoveredParsed.objectId)

        // 3. ObjectTransformChanged
        val transform = SceneBridgeMessage.ObjectTransformChanged(
            objectId = "mesh_1",
            position = SceneVector3(1.0, 2.0, 3.0),
            rotation = SceneVector3(0.0, 45.0, 0.0),
            scale = SceneVector3(2.0, 2.0, 2.0),
            timestamp = 7000L,
        )
        val transformJson = serializer.serialize(transform)
        assertEnvelopeInvariants(transformJson, "OBJECT_TRANSFORM_CHANGED")
        val transformParsed = serializer.deserialize(transformJson)
        assertIs<SceneBridgeMessage.ObjectTransformChanged>(transformParsed)
        assertTrue(transformParsed.timestamp >= 0L)
        assertEquals("mesh_1", transformParsed.objectId)
        assertEquals(SceneVector3(1.0, 2.0, 3.0), transformParsed.position)
        assertEquals(SceneVector3(2.0, 2.0, 2.0), transformParsed.scale)
    }

    @Test
    fun deserialize_unknownFields_succeedsWithoutFailing() {
        val jsonWithExtraFields = """
            {
              "type": "STATE_SYNC",
              "version": 1,
              "timestamp": 123456,
              "futureHostMetadata": "some_extra_val",
              "payload": {
                "futurePayloadHeader": 100,
                "snapshot": {
                  "sceneId": "scene_resilient",
                  "extraFieldOnSnapshot": true,
                  "devices": [
                    {
                      "objectId": "phone_slot_x",
                      "deviceIdentity": "SERIAL_X",
                      "displayName": "Device X",
                      "status": "ONLINE",
                      "connectionType": "WIFI",
                      "futureDeviceTag": "beta",
                      "visual": {
                        "statusColorHex": "#10B981",
                        "isEmissive": true,
                        "emissiveIntensity": 0.5,
                        "badgeText": "ONLINE",
                        "isDimmed": false,
                        "unknownStyleAttribute": 999
                      }
                    }
                  ],
                  "selectedObjectId": "phone_slot_x"
                }
              }
            }
        """.trimIndent()

        val parsed = serializer.deserialize(jsonWithExtraFields)
        assertNotNull(parsed)
        assertIs<SceneBridgeMessage.SyncState>(parsed)
        assertTrue(parsed.timestamp >= 0L)
        assertEquals("scene_resilient", parsed.snapshot.sceneId)
        assertEquals("phone_slot_x", parsed.snapshot.selectedObjectId)
        assertEquals(1, parsed.snapshot.devices.size)
        assertEquals("Device X", parsed.snapshot.devices.first().displayName)
        assertEquals("#10B981", parsed.snapshot.devices.first().visual.statusColorHex)
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
        assertTrue(parsed.timestamp >= 0L)
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
