package `fun`.abbas.wps_adb.platform

expect object AppWindowFocus {
    fun isFocused(): Boolean
    fun setFocused(focused: Boolean)
}
