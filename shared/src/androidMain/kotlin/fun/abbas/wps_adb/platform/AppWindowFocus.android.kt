package `fun`.abbas.wps_adb.platform

actual object AppWindowFocus {
    actual fun isFocused(): Boolean = true

    actual fun setFocused(focused: Boolean) = Unit
}
