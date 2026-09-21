package `fun`.abbas.wps_adb.bridge

/**
 * Low-level interface for sending and receiving raw string frames
 * across the desktop CEF JavaScript bridge.
 */
interface CefJsBridge {

    /**
     * Sends a raw message string into the CEF JavaScript environment.
     */
    fun send(message: String)

    /**
     * Registers or unregisters a listener to receive raw message strings from the CEF JavaScript runtime.
     */
    fun setReceiveListener(listener: ((String) -> Unit)?)
}
