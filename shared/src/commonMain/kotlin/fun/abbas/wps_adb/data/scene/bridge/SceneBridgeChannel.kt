package `fun`.abbas.wps_adb.data.scene.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level message channel managing bidirectional typed [SceneBridgeMessage] flow and connection lifecycle.
 */
interface SceneBridgeChannel {

    /**
     * Current connection state of the bridge channel.
     */
    val state: StateFlow<BridgeConnectionState>

    /**
     * Stream of typed inbound messages emitted by the renderer.
     */
    val incoming: Flow<SceneBridgeMessage>

    /**
     * Transmits a typed message to the renderer.
     * If the channel is not yet in [BridgeConnectionState.READY], the message is queued and flushed upon reaching READY.
     */
    suspend fun send(message: SceneBridgeMessage)

    /**
     * Connects the bridge channel and starts listening to the underlying transport.
     */
    suspend fun connect()

    /**
     * Disconnects the channel, cancels active listeners, and closes the transport.
     */
    suspend fun disconnect()
}
