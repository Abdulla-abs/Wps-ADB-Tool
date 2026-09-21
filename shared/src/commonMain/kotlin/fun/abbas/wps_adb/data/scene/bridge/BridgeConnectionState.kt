package `fun`.abbas.wps_adb.data.scene.bridge

/**
 * Lifecycle states of the Scene Bridge connection between Host and Renderer.
 */
enum class BridgeConnectionState {
    /**
     * Transport is closed or not initiated.
     */
    DISCONNECTED,

    /**
     * Transport handshake or connection initialization in progress.
     */
    CONNECTING,

    /**
     * Low-level transport channel is established, but Renderer has not sent [SceneBridgeMessage.RendererReady].
     */
    CONNECTED,

    /**
     * Renderer is fully initialized, WebGL context is active, and the bridge is ready for bidirectional traffic.
     */
    READY,

    /**
     * Unrecoverable transport or communication error occurred.
     */
    ERROR,
}
