package `fun`.abbas.wps_adb.data.scene.bridge

import kotlinx.coroutines.flow.Flow

/**
 * Low-level transport abstraction for sending and receiving raw string frames across the bridge.
 * Decoupled from specific transport mechanics (JCEF IPC, WebView message handlers, WebSocket, etc.).
 */
interface BridgeTransport {

    /**
     * Stream of raw inbound payloads emitted by the remote renderer.
     */
    val incoming: Flow<String>

    /**
     * Initiates the underlying transport connection.
     */
    suspend fun connect()

    /**
     * Transmits a serialized frame to the remote renderer.
     */
    suspend fun send(payload: String)

    /**
     * Closes the underlying transport connection and releases resources.
     */
    suspend fun close()
}
