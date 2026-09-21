package `fun`.abbas.wps_adb.data.scene.bridge

import `fun`.abbas.wps_adb.model.scene.SceneVector3

/**
 * Serializer contract for converting [SceneBridgeMessage] to/from transport payloads (JSON).
 * Decouples transport format and wire mechanics from the core message contracts.
 */
interface SceneBridgeSerializer {
    fun serialize(message: SceneBridgeMessage): String
    fun deserialize(payload: String): SceneBridgeMessage?
}

class DefaultSceneBridgeSerializer : SceneBridgeSerializer {

    override fun serialize(message: SceneBridgeMessage): String = buildString {
        append("{\"type\":")
        append(quote(messageType(message)))
        append(",\"version\":")
        append(message.version)
        append(",\"timestamp\":")
        append(message.timestamp)
        append(",\"payload\":")
        when (message) {
            is SceneBridgeMessage.InitScene -> append(serializeInitScene(message))
            is SceneBridgeMessage.SyncState -> append(serializeSyncState(message))
            is SceneBridgeMessage.UpdateBinding -> append(serializeDeviceVisual(message.device))
            is SceneBridgeMessage.SelectionChange -> append(serializeSelection(message))
            is SceneBridgeMessage.CameraCommand -> append(serializeCameraCommand(message))
            is SceneBridgeMessage.RendererReady -> append(serializeRendererReady(message))
            is SceneBridgeMessage.ObjectClicked -> append(serializeObjectClicked(message))
            is SceneBridgeMessage.ObjectHovered -> append(serializeObjectHovered(message))
            is SceneBridgeMessage.ObjectTransformChanged -> append(serializeObjectTransform(message))
            is SceneBridgeMessage.RendererError -> append(serializeRendererError(message))
            is SceneBridgeMessage.Unknown -> append(message.rawPayload.ifBlank { "{}" })
        }
        append("}")
    }

    override fun deserialize(payload: String): SceneBridgeMessage? {
        if (payload.isBlank()) return null
        return try {
            val root = MiniJson.parse(payload) as? MiniJson.Obj ?: return null
            val type = (root["type"] as? MiniJson.Str)?.value ?: return null
            val version = (root["version"] as? MiniJson.Num)?.value?.toInt() ?: 1
            val timestamp = (root["timestamp"] as? MiniJson.Num)?.value?.toLong() ?: 0L
            val p = root["payload"] as? MiniJson.Obj ?: MiniJson.Obj(emptyMap())

            when (type) {
                TYPE_RENDERER_READY -> SceneBridgeMessage.RendererReady(
                    protocolVersion = (p["protocolVersion"] as? MiniJson.Num)?.value?.toInt() ?: version,
                    rendererVersion = (p["rendererVersion"] as? MiniJson.Str)?.value ?: "1.0",
                    timestamp = timestamp,
                )
                TYPE_OBJECT_CLICKED -> SceneBridgeMessage.ObjectClicked(
                    objectId = (p["objectId"] as? MiniJson.Str)?.value.orEmpty(),
                    screenX = (p["screenX"] as? MiniJson.Num)?.value?.toFloat() ?: 0f,
                    screenY = (p["screenY"] as? MiniJson.Num)?.value?.toFloat() ?: 0f,
                    isCtrlPressed = (p["isCtrlPressed"] as? MiniJson.Bool)?.value ?: false,
                    isShiftPressed = (p["isShiftPressed"] as? MiniJson.Bool)?.value ?: false,
                    timestamp = timestamp,
                )
                TYPE_OBJECT_HOVERED -> SceneBridgeMessage.ObjectHovered(
                    objectId = (p["objectId"] as? MiniJson.Str)?.value,
                    timestamp = timestamp,
                )
                TYPE_OBJECT_TRANSFORM_CHANGED -> SceneBridgeMessage.ObjectTransformChanged(
                    objectId = (p["objectId"] as? MiniJson.Str)?.value.orEmpty(),
                    position = parseVector3(p["position"] as? MiniJson.Obj),
                    rotation = parseVector3(p["rotation"] as? MiniJson.Obj),
                    scale = parseVector3(p["scale"] as? MiniJson.Obj, defaultVal = 1.0),
                    timestamp = timestamp,
                )
                TYPE_RENDERER_ERROR -> SceneBridgeMessage.RendererError(
                    code = (p["code"] as? MiniJson.Str)?.value.orEmpty(),
                    message = (p["message"] as? MiniJson.Str)?.value.orEmpty(),
                    category = (p["category"] as? MiniJson.Str)?.value,
                    timestamp = timestamp,
                )
                TYPE_SCENE_INIT -> {
                    val descObj = p["sceneDescriptor"] as? MiniJson.Obj ?: return null
                    SceneBridgeMessage.InitScene(
                        sceneDescriptor = parseSceneDescriptor(descObj),
                        timestamp = timestamp,
                    )
                }
                TYPE_STATE_SYNC -> {
                    val snapshotObj = p["snapshot"] as? MiniJson.Obj ?: p
                    SceneBridgeMessage.SyncState(
                        snapshot = parseSceneVisualSnapshot(snapshotObj),
                        timestamp = timestamp,
                    )
                }
                TYPE_BINDING_UPDATE -> {
                    val devObj = p["device"] as? MiniJson.Obj ?: p
                    SceneBridgeMessage.UpdateBinding(
                        device = parseDeviceVisual(devObj),
                        timestamp = timestamp,
                    )
                }
                TYPE_SELECTION_CHANGE -> SceneBridgeMessage.SelectionChange(
                    selectedObjectId = (p["selectedObjectId"] as? MiniJson.Str)?.value,
                    focusCamera = (p["focusCamera"] as? MiniJson.Bool)?.value ?: false,
                    timestamp = timestamp,
                )
                TYPE_CAMERA_COMMAND -> SceneBridgeMessage.CameraCommand(
                    position = parseVector3(p["position"] as? MiniJson.Obj),
                    target = parseVector3(p["target"] as? MiniJson.Obj),
                    fov = (p["fov"] as? MiniJson.Num)?.value,
                    timestamp = timestamp,
                )
                else -> SceneBridgeMessage.Unknown(
                    rawType = type,
                    rawPayload = p.toJson(),
                    timestamp = timestamp,
                )
            }
        } catch (_: Throwable) {
            // Error isolation: Malformed json safely ignored without crashing
            null
        }
    }

    private fun messageType(message: SceneBridgeMessage): String = when (message) {
        is SceneBridgeMessage.InitScene -> TYPE_SCENE_INIT
        is SceneBridgeMessage.SyncState -> TYPE_STATE_SYNC
        is SceneBridgeMessage.UpdateBinding -> TYPE_BINDING_UPDATE
        is SceneBridgeMessage.SelectionChange -> TYPE_SELECTION_CHANGE
        is SceneBridgeMessage.CameraCommand -> TYPE_CAMERA_COMMAND
        is SceneBridgeMessage.RendererReady -> TYPE_RENDERER_READY
        is SceneBridgeMessage.ObjectClicked -> TYPE_OBJECT_CLICKED
        is SceneBridgeMessage.ObjectHovered -> TYPE_OBJECT_HOVERED
        is SceneBridgeMessage.ObjectTransformChanged -> TYPE_OBJECT_TRANSFORM_CHANGED
        is SceneBridgeMessage.RendererError -> TYPE_RENDERER_ERROR
        is SceneBridgeMessage.Unknown -> message.rawType
    }

    private fun serializeInitScene(msg: SceneBridgeMessage.InitScene): String = buildString {
        append("{\"sceneDescriptor\":{")
        append("\"id\":").append(quote(msg.sceneDescriptor.id))
        append(",\"name\":").append(quote(msg.sceneDescriptor.name))
        append(",\"environmentFileName\":")
        if (msg.sceneDescriptor.environmentFileName != null) {
            append(quote(msg.sceneDescriptor.environmentFileName))
        } else {
            append("null")
        }
        append(",\"camera\":{")
        append("\"position\":").append(serializeVector3(msg.sceneDescriptor.camera.position))
        append(",\"target\":").append(serializeVector3(msg.sceneDescriptor.camera.target))
        append(",\"fov\":").append(msg.sceneDescriptor.camera.fov)
        append("},\"assets\":[")
        msg.sceneDescriptor.assets.forEachIndexed { i, a ->
            if (i > 0) append(",")
            append("{\"id\":").append(quote(a.id))
            append(",\"fileName\":").append(quote(a.fileName))
            append(",\"name\":").append(quote(a.name))
            append(",\"transform\":{")
            append("\"position\":").append(serializeVector3(a.transform.position))
            append(",\"rotation\":").append(serializeVector3(a.transform.rotation))
            append(",\"scale\":").append(serializeVector3(a.transform.scale))
            append("}}")
        }
        append("],\"bindableObjectIds\":[")
        msg.sceneDescriptor.bindableObjectIds.forEachIndexed { i, id ->
            if (i > 0) append(",")
            append(quote(id))
        }
        append("]}}")
    }

    private fun serializeSyncState(msg: SceneBridgeMessage.SyncState): String = buildString {
        append("{\"snapshot\":{")
        append("\"sceneId\":").append(quote(msg.snapshot.sceneId))
        append(",\"selectedObjectId\":")
        if (msg.snapshot.selectedObjectId != null) append(quote(msg.snapshot.selectedObjectId)) else append("null")
        append(",\"devices\":[")
        msg.snapshot.devices.forEachIndexed { i, d ->
            if (i > 0) append(",")
            append(serializeDeviceVisual(d))
        }
        append("]}}")
    }

    private fun serializeDeviceVisual(d: DeviceVisualDescriptor): String = buildString {
        append("{\"objectId\":").append(quote(d.objectId))
        append(",\"deviceIdentity\":")
        if (d.deviceIdentity != null) append(quote(d.deviceIdentity)) else append("null")
        append(",\"displayName\":").append(quote(d.displayName))
        append(",\"status\":").append(quote(d.status.name))
        append(",\"connectionType\":").append(quote(d.connectionType))
        append(",\"visual\":{")
        append("\"statusColorHex\":").append(quote(d.visual.statusColorHex))
        append(",\"isEmissive\":").append(d.visual.isEmissive)
        append(",\"emissiveIntensity\":").append(d.visual.emissiveIntensity)
        append(",\"badgeText\":").append(quote(d.visual.badgeText))
        append(",\"isDimmed\":").append(d.visual.isDimmed)
        append("}}")
    }

    private fun serializeSelection(msg: SceneBridgeMessage.SelectionChange): String = buildString {
        append("{\"selectedObjectId\":")
        if (msg.selectedObjectId != null) append(quote(msg.selectedObjectId)) else append("null")
        append(",\"focusCamera\":").append(msg.focusCamera)
        append("}")
    }

    private fun serializeCameraCommand(msg: SceneBridgeMessage.CameraCommand): String = buildString {
        append("{\"position\":").append(serializeVector3(msg.position))
        append(",\"target\":").append(serializeVector3(msg.target))
        if (msg.fov != null) append(",\"fov\":").append(msg.fov)
        append("}")
    }

    private fun serializeRendererReady(msg: SceneBridgeMessage.RendererReady): String = buildString {
        append("{\"protocolVersion\":").append(msg.protocolVersion)
        append(",\"rendererVersion\":").append(quote(msg.rendererVersion))
        append("}")
    }

    private fun serializeObjectClicked(msg: SceneBridgeMessage.ObjectClicked): String = buildString {
        append("{\"objectId\":").append(quote(msg.objectId))
        append(",\"screenX\":").append(msg.screenX)
        append(",\"screenY\":").append(msg.screenY)
        append(",\"isCtrlPressed\":").append(msg.isCtrlPressed)
        append(",\"isShiftPressed\":").append(msg.isShiftPressed)
        append("}")
    }

    private fun serializeObjectHovered(msg: SceneBridgeMessage.ObjectHovered): String = buildString {
        append("{\"objectId\":")
        if (msg.objectId != null) append(quote(msg.objectId)) else append("null")
        append("}")
    }

    private fun serializeObjectTransform(msg: SceneBridgeMessage.ObjectTransformChanged): String = buildString {
        append("{\"objectId\":").append(quote(msg.objectId))
        append(",\"position\":").append(serializeVector3(msg.position))
        append(",\"rotation\":").append(serializeVector3(msg.rotation))
        append(",\"scale\":").append(serializeVector3(msg.scale))
        append("}")
    }

    private fun serializeRendererError(msg: SceneBridgeMessage.RendererError): String = buildString {
        append("{\"code\":").append(quote(msg.code))
        append(",\"message\":").append(quote(msg.message))
        if (msg.category != null) {
            append(",\"category\":").append(quote(msg.category))
        }
        append("}")
    }

    private fun serializeVector3(v: SceneVector3): String =
        "{\"x\":${v.x},\"y\":${v.y},\"z\":${v.z}}"

    private fun parseVector3(obj: MiniJson.Obj?, defaultVal: Double = 0.0): SceneVector3 {
        if (obj == null) return SceneVector3(defaultVal, defaultVal, defaultVal)
        val x = (obj["x"] as? MiniJson.Num)?.value ?: defaultVal
        val y = (obj["y"] as? MiniJson.Num)?.value ?: defaultVal
        val z = (obj["z"] as? MiniJson.Num)?.value ?: defaultVal
        return SceneVector3(x, y, z)
    }

    private fun parseSceneDescriptor(obj: MiniJson.Obj): SceneDescriptor {
        val id = (obj["id"] as? MiniJson.Str)?.value.orEmpty()
        val name = (obj["name"] as? MiniJson.Str)?.value.orEmpty()
        val env = (obj["environmentFileName"] as? MiniJson.Str)?.value
        val camObj = obj["camera"] as? MiniJson.Obj
        val cam = SceneCameraDescriptor(
            position = parseVector3(camObj?.get("position") as? MiniJson.Obj),
            target = parseVector3(camObj?.get("target") as? MiniJson.Obj),
            fov = (camObj?.get("fov") as? MiniJson.Num)?.value ?: 45.0,
        )
        val assetsList = (obj["assets"] as? MiniJson.Arr)?.items?.mapNotNull { item ->
            val a = item as? MiniJson.Obj ?: return@mapNotNull null
            val t = a["transform"] as? MiniJson.Obj
            SceneAssetDescriptor(
                id = (a["id"] as? MiniJson.Str)?.value.orEmpty(),
                fileName = (a["fileName"] as? MiniJson.Str)?.value.orEmpty(),
                name = (a["name"] as? MiniJson.Str)?.value.orEmpty(),
                transform = SceneTransformDescriptor(
                    position = parseVector3(t?.get("position") as? MiniJson.Obj),
                    rotation = parseVector3(t?.get("rotation") as? MiniJson.Obj),
                    scale = parseVector3(t?.get("scale") as? MiniJson.Obj, defaultVal = 1.0),
                ),
            )
        }.orEmpty()

        val bindableIds = (obj["bindableObjectIds"] as? MiniJson.Arr)?.items?.mapNotNull {
            (it as? MiniJson.Str)?.value
        }.orEmpty()

        return SceneDescriptor(
            id = id,
            name = name,
            environmentFileName = env,
            camera = cam,
            assets = assetsList,
            bindableObjectIds = bindableIds,
        )
    }

    private fun parseSceneVisualSnapshot(obj: MiniJson.Obj): SceneVisualSnapshot {
        val sceneId = (obj["sceneId"] as? MiniJson.Str)?.value.orEmpty()
        val selectedObjectId = (obj["selectedObjectId"] as? MiniJson.Str)?.value
        val devices = (obj["devices"] as? MiniJson.Arr)?.items?.mapNotNull { item ->
            val devObj = item as? MiniJson.Obj ?: return@mapNotNull null
            parseDeviceVisual(devObj)
        }.orEmpty()
        return SceneVisualSnapshot(sceneId = sceneId, devices = devices, selectedObjectId = selectedObjectId)
    }

    private fun parseDeviceVisual(obj: MiniJson.Obj): DeviceVisualDescriptor {
        val objectId = (obj["objectId"] as? MiniJson.Str)?.value.orEmpty()
        val deviceIdentity = (obj["deviceIdentity"] as? MiniJson.Str)?.value
        val displayName = (obj["displayName"] as? MiniJson.Str)?.value.orEmpty()
        val statusStr = (obj["status"] as? MiniJson.Str)?.value.orEmpty()
        val status = try {
            VisualStatus.valueOf(statusStr)
        } catch (_: Throwable) {
            VisualStatus.OFFLINE
        }
        val connectionType = (obj["connectionType"] as? MiniJson.Str)?.value ?: "UNKNOWN"
        val vObj = obj["visual"] as? MiniJson.Obj
        val visual = VisualStyle(
            statusColorHex = (vObj?.get("statusColorHex") as? MiniJson.Str)?.value ?: "#64748B",
            isEmissive = (vObj?.get("isEmissive") as? MiniJson.Bool)?.value ?: false,
            emissiveIntensity = (vObj?.get("emissiveIntensity") as? MiniJson.Num)?.value?.toFloat() ?: 0.0f,
            badgeText = (vObj?.get("badgeText") as? MiniJson.Str)?.value ?: "OFFLINE",
            isDimmed = (vObj?.get("isDimmed") as? MiniJson.Bool)?.value ?: true,
        )
        return DeviceVisualDescriptor(
            objectId = objectId,
            deviceIdentity = deviceIdentity,
            displayName = displayName,
            status = status,
            connectionType = connectionType,
            visual = visual,
        )
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    companion object {
        const val TYPE_SCENE_INIT = "SCENE_INIT"
        const val TYPE_STATE_SYNC = "STATE_SYNC"
        const val TYPE_BINDING_UPDATE = "BINDING_UPDATE"
        const val TYPE_SELECTION_CHANGE = "SELECTION_CHANGE"
        const val TYPE_CAMERA_COMMAND = "CAMERA_COMMAND"
        const val TYPE_RENDERER_READY = "RENDERER_READY"
        const val TYPE_OBJECT_CLICKED = "OBJECT_CLICKED"
        const val TYPE_OBJECT_HOVERED = "OBJECT_HOVERED"
        const val TYPE_OBJECT_TRANSFORM_CHANGED = "OBJECT_TRANSFORM_CHANGED"
        const val TYPE_RENDERER_ERROR = "RENDERER_ERROR"
    }
}

/**
 * Lightweight, zero-dependency pure Kotlin JSON AST and parser.
 */
internal sealed class MiniJson {
    abstract fun toJson(): String

    data class Obj(val map: Map<String, MiniJson>) : MiniJson() {
        operator fun get(key: String): MiniJson? = map[key]
        override fun toJson(): String = buildString {
            append("{")
            map.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) append(",")
                append("\"").append(k).append("\":").append(v.toJson())
            }
            append("}")
        }
    }
    data class Arr(val items: List<MiniJson>) : MiniJson() {
        override fun toJson(): String = buildString {
            append("[")
            items.forEachIndexed { i, item ->
                if (i > 0) append(",")
                append(item.toJson())
            }
            append("]")
        }
    }
    data class Str(val value: String) : MiniJson() {
        override fun toJson(): String = "\"${value.replace("\"", "\\\"")}\""
    }
    data class Num(val value: Double) : MiniJson() {
        override fun toJson(): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
    data class Bool(val value: Boolean) : MiniJson() {
        override fun toJson(): String = value.toString()
    }
    object Null : MiniJson() {
        override fun toJson(): String = "null"
    }

    companion object {
        fun parse(input: String): MiniJson {
            val parser = Parser(input)
            return parser.parseValue()
        }

        private class Parser(private val src: String) {
            private var pos = 0

            fun parseValue(): MiniJson {
                skipWhitespace()
                if (pos >= src.length) return Null
                return when (val ch = src[pos]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    't', 'f' -> parseBoolean()
                    'n' -> parseNull()
                    '-', in '0'..'9' -> parseNumber()
                    else -> throw IllegalArgumentException("Unexpected char '$ch' at $pos")
                }
            }

            private fun parseObject(): Obj {
                expect('{')
                val map = mutableMapOf<String, MiniJson>()
                skipWhitespace()
                if (peek() == '}') {
                    pos++
                    return Obj(map)
                }
                while (pos < src.length) {
                    skipWhitespace()
                    val key = parseRawString()
                    skipWhitespace()
                    expect(':')
                    val value = parseValue()
                    map[key] = value
                    skipWhitespace()
                    if (peek() == '}') {
                        pos++
                        break
                    }
                    expect(',')
                }
                return Obj(map)
            }

            private fun parseArray(): Arr {
                expect('[')
                val list = mutableListOf<MiniJson>()
                skipWhitespace()
                if (peek() == ']') {
                    pos++
                    return Arr(list)
                }
                while (pos < src.length) {
                    val item = parseValue()
                    list.add(item)
                    skipWhitespace()
                    if (peek() == ']') {
                        pos++
                        break
                    }
                    expect(',')
                }
                return Arr(list)
            }

            private fun parseRawString(): String {
                expect('"')
                val sb = StringBuilder()
                while (pos < src.length) {
                    val ch = src[pos++]
                    if (ch == '"') return sb.toString()
                    if (ch == '\\' && pos < src.length) {
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> sb.append(esc)
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                return sb.toString()
            }

            private fun parseString(): Str = Str(parseRawString())

            private fun parseBoolean(): Bool {
                return if (src.startsWith("true", pos)) {
                    pos += 4
                    Bool(true)
                } else if (src.startsWith("false", pos)) {
                    pos += 5
                    Bool(false)
                } else {
                    throw IllegalArgumentException("Expected boolean at $pos")
                }
            }

            private fun parseNull(): Null {
                if (src.startsWith("null", pos)) {
                    pos += 4
                    return Null
                }
                throw IllegalArgumentException("Expected null at $pos")
            }

            private fun parseNumber(): Num {
                val start = pos
                if (src[pos] == '-') pos++
                while (pos < src.length && (src[pos] in '0'..'9' || src[pos] == '.' || src[pos] == 'e' || src[pos] == 'E' || src[pos] == '+' || src[pos] == '-')) {
                    pos++
                }
                val numStr = src.substring(start, pos)
                return Num(numStr.toDouble())
            }

            private fun skipWhitespace() {
                while (pos < src.length && src[pos].isWhitespace()) pos++
            }

            private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

            private fun expect(expected: Char) {
                skipWhitespace()
                if (pos >= src.length || src[pos] != expected) {
                    throw IllegalArgumentException("Expected '$expected' at $pos, but found '${peek()}'")
                }
                pos++
            }
        }
    }
}
