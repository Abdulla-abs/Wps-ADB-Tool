package `fun`.abbas.wps_adb.data.scene.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default implementation of [SceneBridgeChannel] connecting Host and Renderer.
 * Manages message serialization, inbound typed dispatching, pending queue buffering,
 * and transport lifecycle transitions.
 */
class DefaultSceneBridgeChannel(
    private val transport: BridgeTransport,
    private val serializer: SceneBridgeSerializer = DefaultSceneBridgeSerializer(),
    private val scope: CoroutineScope,
) : SceneBridgeChannel {

    private val _state = MutableStateFlow(BridgeConnectionState.DISCONNECTED)
    override val state: StateFlow<BridgeConnectionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<SceneBridgeMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<SceneBridgeMessage> = _incoming.asSharedFlow()

    private val pendingQueue = mutableListOf<SceneBridgeMessage>()
    private val queueMutex = Mutex()
    private var listenJob: Job? = null

    override suspend fun connect() {
        if (_state.value == BridgeConnectionState.CONNECTED || _state.value == BridgeConnectionState.READY) {
            return
        }

        _state.value = BridgeConnectionState.CONNECTING
        try {
            transport.connect()
            _state.value = BridgeConnectionState.CONNECTED

            listenJob?.cancel()
            listenJob = scope.launch {
                try {
                    transport.incoming.collect { rawPayload ->
                        handleIncomingPayload(rawPayload)
                    }
                } catch (_: Throwable) {
                    _state.value = BridgeConnectionState.ERROR
                    _state.value = BridgeConnectionState.DISCONNECTED
                }
            }
        } catch (_: Throwable) {
            _state.value = BridgeConnectionState.ERROR
            _state.value = BridgeConnectionState.DISCONNECTED
        }
    }

    override suspend fun send(message: SceneBridgeMessage) {
        queueMutex.withLock {
            if (_state.value == BridgeConnectionState.READY) {
                try {
                    val payload = serializer.serialize(message)
                    transport.send(payload)
                } catch (_: Throwable) {
                    _state.value = BridgeConnectionState.ERROR
                    _state.value = BridgeConnectionState.DISCONNECTED
                }
            } else {
                // Buffer message if renderer is not ready yet
                pendingQueue.add(message)
            }
        }
    }

    override suspend fun disconnect() {
        listenJob?.cancel()
        listenJob = null

        try {
            transport.close()
        } catch (_: Throwable) {
            // Safe cleanup
        }

        queueMutex.withLock {
            pendingQueue.clear()
        }
        _state.value = BridgeConnectionState.DISCONNECTED
    }

    private suspend fun handleIncomingPayload(rawPayload: String) {
        val message = try {
            serializer.deserialize(rawPayload)
        } catch (_: Throwable) {
            // Error isolation: malformed incoming string does not crash the pipeline
            null
        }

        if (message != null) {
            if (message is SceneBridgeMessage.RendererReady) {
                _state.value = BridgeConnectionState.READY
                flushPendingQueue()
            }
            _incoming.emit(message)
        }
    }

    private suspend fun flushPendingQueue() {
        queueMutex.withLock {
            if (pendingQueue.isNotEmpty()) {
                val toFlush = pendingQueue.toList()
                pendingQueue.clear()
                for (msg in toFlush) {
                    try {
                        val payload = serializer.serialize(msg)
                        transport.send(payload)
                    } catch (_: Throwable) {
                        _state.value = BridgeConnectionState.ERROR
                        _state.value = BridgeConnectionState.DISCONNECTED
                        break
                    }
                }
            }
        }
    }
}
