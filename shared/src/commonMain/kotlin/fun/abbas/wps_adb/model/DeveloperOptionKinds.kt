package `fun`.abbas.wps_adb.model

val DeveloperOptionKinds: Set<EasyActionKind> = setOf(
    EasyActionKind.TOGGLE_SHOW_REFRESH_RATE,
    EasyActionKind.TOGGLE_POINTER_LOCATION,
    EasyActionKind.TOGGLE_SHOW_SURFACE_UPDATES,
    EasyActionKind.TOGGLE_SHOW_VIEW_UPDATES,
    EasyActionKind.TOGGLE_GPU_OVERDRAW,
    EasyActionKind.TOGGLE_STRICT_MODE,
    EasyActionKind.TOGGLE_GPU_PROFILE_BARS,
    EasyActionKind.TOGGLE_DONT_KEEP_ACTIVITIES,
    EasyActionKind.TOGGLE_SHOW_LAYOUT_BOUNDS,
)

fun EasyActionKind.isDeveloperOptionToggle(): Boolean = this in DeveloperOptionKinds
