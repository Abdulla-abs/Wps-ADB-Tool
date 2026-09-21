package `fun`.abbas.wps_adb.bridge

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Desktop CEF implementation of [BridgeTransport].
 *
 * Responsibilities:
 * - Pure transport adapter wrapping [CefJsBridge].
 * - Forwards incoming raw String messages from [CefJsBridge] to [incoming] flow.
 * - Forwards outbound raw String payloads to [CefJsBridge.send].
 *
 * Boundary Guarantees:
 * - Does NOT reference SceneRuntimeController.
 * - Does NOT parse or reference SceneBridgeMessage, SceneRuntimeState, or DeviceScene.
 */
class CefBridgeTransport(
    private val cefBridge: CefJsBridge
) : BridgeTransport {

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incoming: Flow<String> = _incoming.asSharedFlow()

    init {
        cefBridge.setReceiveListener { rawMessage ->
            _incoming.tryEmit(rawMessage)
        }
    }

    override suspend fun connect() {
        // Transport ready for communication
    }

    override suspend fun send(payload: String) {
        cefBridge.send(payload)
    }

    override suspend fun close() {
        cefBridge.setReceiveListener(null)
    }
}
