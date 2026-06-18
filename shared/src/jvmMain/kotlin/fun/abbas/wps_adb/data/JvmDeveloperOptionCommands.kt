package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DeveloperOptionKinds
import `fun`.abbas.wps_adb.model.EasyActionKind

object JvmDeveloperOptionCommands {
    private const val ACTIVITY_SERVICE_POKER = "1599295570"
    private const val SURFACE_FLINGER_REFRESH_RATE_CODE = "1034"
    private const val SURFACE_FLINGER_SURFACE_UPDATES_WRITE = "1002"
    private const val SURFACE_FLINGER_SURFACE_UPDATES_READ = "1010"
    private const val ACTIVITY_ALWAYS_FINISH_CODE = "43"

    fun pokeActivityService(): List<String> =
        listOf("shell", "service", "call", "activity", ACTIVITY_SERVICE_POKER)

    fun queryState(kind: EasyActionKind): List<String>? = when (kind) {
        EasyActionKind.TOGGLE_SHOW_REFRESH_RATE ->
            surfaceFlingerCall(SURFACE_FLINGER_REFRESH_RATE_CODE, "2")
        EasyActionKind.TOGGLE_POINTER_LOCATION ->
            settingsGet("system", "pointer_location")
        EasyActionKind.TOGGLE_SHOW_SURFACE_UPDATES ->
            surfaceFlingerCall(SURFACE_FLINGER_SURFACE_UPDATES_READ)
        EasyActionKind.TOGGLE_SHOW_VIEW_UPDATES ->
            getProp("debug.hwui.show_dirty_regions")
        EasyActionKind.TOGGLE_GPU_OVERDRAW ->
            getProp("debug.hwui.overdraw")
        EasyActionKind.TOGGLE_STRICT_MODE ->
            getProp("persist.sys.strictmode.visual")
        EasyActionKind.TOGGLE_GPU_PROFILE_BARS ->
            getProp("debug.hwui.profile")
        EasyActionKind.TOGGLE_DONT_KEEP_ACTIVITIES ->
            settingsGet("global", "always_finish_activities")
        EasyActionKind.TOGGLE_SHOW_LAYOUT_BOUNDS ->
            getProp("debug.layout")
        else -> null
    }

    fun applyEnabled(kind: EasyActionKind, enabled: Boolean): List<List<String>> = when (kind) {
        EasyActionKind.TOGGLE_SHOW_REFRESH_RATE -> listOf(
            surfaceFlingerCall(SURFACE_FLINGER_REFRESH_RATE_CODE, if (enabled) "1" else "0"),
        )
        EasyActionKind.TOGGLE_POINTER_LOCATION -> listOf(
            settingsPut("system", "pointer_location", if (enabled) "1" else "0"),
        )
        EasyActionKind.TOGGLE_SHOW_SURFACE_UPDATES -> listOf(
            surfaceFlingerCall(SURFACE_FLINGER_SURFACE_UPDATES_WRITE, if (enabled) "1" else "0"),
        )
        EasyActionKind.TOGGLE_SHOW_VIEW_UPDATES -> listOf(
            setProp("debug.hwui.show_dirty_regions", if (enabled) "true" else "false"),
            pokeActivityService(),
        )
        EasyActionKind.TOGGLE_GPU_OVERDRAW -> listOf(
            setProp("debug.hwui.overdraw", if (enabled) "show" else "false"),
            pokeActivityService(),
        )
        EasyActionKind.TOGGLE_STRICT_MODE -> listOf(
            setProp("persist.sys.strictmode.visual", if (enabled) "1" else "0"),
            settingsPut("global", "strict_mode", if (enabled) "1" else "0"),
        )
        EasyActionKind.TOGGLE_GPU_PROFILE_BARS -> listOf(
            setProp("debug.hwui.profile", if (enabled) "visual_bars" else "false"),
            pokeActivityService(),
        )
        EasyActionKind.TOGGLE_DONT_KEEP_ACTIVITIES -> listOf(
            settingsPut("global", "always_finish_activities", if (enabled) "1" else "0"),
            listOf("shell", "service", "call", "activity", ACTIVITY_ALWAYS_FINISH_CODE, "i32", if (enabled) "1" else "0"),
            pokeActivityService(),
        )
        EasyActionKind.TOGGLE_SHOW_LAYOUT_BOUNDS -> listOf(
            setProp("debug.layout", if (enabled) "true" else "false"),
            pokeActivityService(),
        )
        else -> emptyList()
    }

    fun parseEnabled(kind: EasyActionKind, output: String): Boolean? = when (kind) {
        EasyActionKind.TOGGLE_SHOW_REFRESH_RATE ->
            parseFirstParcelInt(output)?.let { it != 0 }
        EasyActionKind.TOGGLE_POINTER_LOCATION ->
            isTruthySettingsValue(output)
        EasyActionKind.TOGGLE_SHOW_SURFACE_UPDATES ->
            parseSurfaceUpdatesRead(output)
        EasyActionKind.TOGGLE_SHOW_VIEW_UPDATES ->
            isTruthyPropValue(output)
        EasyActionKind.TOGGLE_GPU_OVERDRAW ->
            output.trim().equals("show", ignoreCase = true)
        EasyActionKind.TOGGLE_STRICT_MODE ->
            isTruthyPropValue(output)
        EasyActionKind.TOGGLE_GPU_PROFILE_BARS ->
            output.trim().equals("visual_bars", ignoreCase = true)
        EasyActionKind.TOGGLE_DONT_KEEP_ACTIVITIES ->
            isTruthySettingsValue(output)
        EasyActionKind.TOGGLE_SHOW_LAYOUT_BOUNDS ->
            isTruthyPropValue(output)
        else -> null
    }

    fun allToggleKinds(): List<EasyActionKind> = DeveloperOptionKinds.toList()

    private fun settingsGet(namespace: String, key: String): List<String> =
        listOf("shell", "settings", "get", namespace, key)

    private fun settingsPut(namespace: String, key: String, value: String): List<String> =
        listOf("shell", "settings", "put", namespace, key, value)

    private fun setProp(key: String, value: String): List<String> =
        listOf("shell", "setprop", key, value)

    private fun getProp(key: String): List<String> =
        listOf("shell", "getprop", key)

    private fun surfaceFlingerCall(code: String, intArg: String? = null): List<String> =
        buildList {
            add("shell")
            add("service")
            add("call")
            add("SurfaceFlinger")
            add(code)
            if (intArg != null) {
                add("i32")
                add(intArg)
            }
        }

    private fun parseFirstParcelInt(output: String): Int? {
        val match = PARCEL_INT_REGEX.find(output) ?: return null
        return match.groupValues[1].toIntOrNull(16)
    }

    private fun parseSurfaceUpdatesRead(output: String): Boolean? {
        val values = PARCEL_INT_REGEX.findAll(output).map { it.groupValues[1].toIntOrNull(16) }.toList()
        val showUpdates = values.getOrNull(2) ?: return null
        return showUpdates != 0
    }

    private fun isTruthySettingsValue(output: String): Boolean {
        val value = output.trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return false
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun isTruthyPropValue(output: String): Boolean {
        val value = output.trim()
        if (value.isEmpty()) return false
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private val PARCEL_INT_REGEX = Regex("""0x[0-9a-fA-F]+:\s+([0-9a-fA-F]{8})""")
}
