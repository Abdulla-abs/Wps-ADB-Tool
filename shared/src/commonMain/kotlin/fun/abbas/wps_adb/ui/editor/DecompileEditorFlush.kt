package `fun`.abbas.wps_adb.ui.editor

/**
 * Allows ViewModel to pull the latest text from the active Swing code editor
 * before saving or reassembling DEX files.
 */
object DecompileEditorFlush {
    private var flushHandler: (() -> Unit)? = null

    fun register(handler: () -> Unit) {
        flushHandler = handler
    }

    fun unregister() {
        flushHandler = null
    }

    fun flush() {
        flushHandler?.invoke()
    }
}
