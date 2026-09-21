package `fun`.abbas.wps_adb.bridge

import `fun`.abbas.wps_adb.data.scene.bridge.BridgeTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

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

    private val channel = Channel<String>(capacity = 64)
    override val incoming: Flow<String> = channel.receiveAsFlow()

    init {
        attachReceiveListener()
    }

    private fun attachReceiveListener() {
        cefBridge.setReceiveListener { rawMessage ->
            val result = channel.trySend(rawMessage)
            if (result.isFailure) {
                System.err.println("CefBridgeTransport: Dropped incoming frame, buffer full or unavailable")
            }
        }
    }

    override suspend fun connect() {
        attachReceiveListener()
    }

    override suspend fun send(payload: String) {
        cefBridge.send(payload)
    }

    override suspend fun close() {
        cefBridge.setReceiveListener(null)
        while (channel.tryReceive().isSuccess) {
            // Drain stale buffered messages on close without permanently closing the channel
        }
    }
}
