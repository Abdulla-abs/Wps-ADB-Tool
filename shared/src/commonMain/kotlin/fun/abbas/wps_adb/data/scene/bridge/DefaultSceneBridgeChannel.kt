package `fun`.abbas.wps_adb.data.scene.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
            _state.value = BridgeConnectionState.CONNECTED

            listenJob?.cancelAndJoin()
            listenJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    transport.incoming.collect { rawPayload ->
                        handleIncomingPayload(rawPayload)
                    }
                } catch (_: CancellationException) {
                    // Normal coroutine cancellation on disconnect/close, do not treat as error
                } catch (_: Throwable) {
                    clearPendingQueueAndSetError()
                    try {
                        transport.close()
                    } catch (_: Throwable) {
                        // Safe cleanup
                    }
                    _state.value = BridgeConnectionState.DISCONNECTED
                }
            }
            transport.connect()
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                listenJob?.cancelAndJoin()
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
            throw cancellation
        } catch (_: Throwable) {
            listenJob?.cancelAndJoin()
            listenJob = null
            clearPendingQueueAndSetError()
            try {
                transport.close()
            } catch (_: Throwable) {
                // Safe cleanup
            }
            _state.value = BridgeConnectionState.DISCONNECTED
        }
    }

    override suspend fun send(message: SceneBridgeMessage) {
        var transportFailed = false
        queueMutex.withLock {
            when (_state.value) {
                BridgeConnectionState.READY -> {
                    try {
                        val payload = serializer.serialize(message)
                        transport.send(payload)
                    } catch (_: Throwable) {
                        pendingQueue.clear()
                        _state.value = BridgeConnectionState.ERROR
                        transportFailed = true
                    }
                }
                BridgeConnectionState.CONNECTING,
                BridgeConnectionState.CONNECTED -> {
                    // Buffer message while awaiting handshake completion
                    pendingQueue.add(message)
                }
                BridgeConnectionState.ERROR,
                BridgeConnectionState.DISCONNECTED -> {
                    // Refuse/drop message in error or disconnected state to prevent unbounded memory growth
                }
            }
        }

        if (transportFailed) {
            listenJob?.cancelAndJoin()
            listenJob = null
            try {
                transport.close()
            } catch (_: Throwable) {
                // Safe cleanup
            }
            _state.value = BridgeConnectionState.DISCONNECTED
        }
    }

    override suspend fun disconnect() {
        listenJob?.cancelAndJoin()
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

    private suspend fun transitionToReady() {
        var transportFailed = false
        queueMutex.withLock {
            if (_state.value != BridgeConnectionState.CONNECTED) {
                return@withLock
            }

            val toFlush = pendingQueue.toList()
            pendingQueue.clear()
            for (message in toFlush) {
                try {
                    transport.send(serializer.serialize(message))
                } catch (_: Throwable) {
                    pendingQueue.clear()
                    _state.value = BridgeConnectionState.ERROR
                    transportFailed = true
                    return@withLock
                }
            }

            // Publish READY only after every older queued frame has been sent. This
            // prevents READY observers from sending a fresh snapshot ahead of stale data.
            _state.value = BridgeConnectionState.READY
        }

        if (transportFailed) {
            try {
                transport.close()
            } catch (_: Throwable) {
                // Safe cleanup
            }
            _state.value = BridgeConnectionState.DISCONNECTED
        }
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
                if (message.version == CURRENT_BRIDGE_PROTOCOL_VERSION &&
                    message.protocolVersion == CURRENT_BRIDGE_PROTOCOL_VERSION
                ) {
                    transitionToReady()
                } else {
                    clearPendingQueueAndSetError()
                }
            }
            _incoming.emit(message)
        }
    }

    private suspend fun clearPendingQueueAndSetError() {
        queueMutex.withLock {
            pendingQueue.clear()
            _state.value = BridgeConnectionState.ERROR
        }
    }
}
