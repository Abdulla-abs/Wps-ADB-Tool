package `fun`.abbas.wps_adb.platform

import java.util.concurrent.atomic.AtomicBoolean

actual object AppWindowFocus {
    private val focused = AtomicBoolean(true)

    actual fun isFocused(): Boolean = focused.get()

    actual fun setFocused(focused: Boolean) {
        this.focused.set(focused)
    }
}
