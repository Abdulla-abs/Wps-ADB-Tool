package `fun`.abbas.wps_adb.model

enum class EasyActionCategory {
    SYSTEM,
    DISPLAY,
    APP_CONTROL,
}

enum class EasyActionKind {
    REBOOT,
    RECOVERY_MODE,
    CLEAR_APP_CACHE,
    TAKE_SCREENSHOT,
    TAKE_SCREENSHOT_TO_CLIPBOARD,
    SCREEN_RECORD,
    FORCE_STOP_APP,
    CLEAR_APP_DATA,
}

data class EasyActionDefinition(
    val kind: EasyActionKind,
    val category: EasyActionCategory,
    val requiresPackage: Boolean = false,
    val destructive: Boolean = false,
)

val DefaultEasyActions: List<EasyActionDefinition> = listOf(
    EasyActionDefinition(EasyActionKind.REBOOT, EasyActionCategory.SYSTEM, destructive = true),
    EasyActionDefinition(EasyActionKind.RECOVERY_MODE, EasyActionCategory.SYSTEM, destructive = true),
    EasyActionDefinition(EasyActionKind.TAKE_SCREENSHOT, EasyActionCategory.DISPLAY),
    EasyActionDefinition(EasyActionKind.TAKE_SCREENSHOT_TO_CLIPBOARD, EasyActionCategory.DISPLAY),
    EasyActionDefinition(EasyActionKind.SCREEN_RECORD, EasyActionCategory.DISPLAY),
    EasyActionDefinition(EasyActionKind.CLEAR_APP_CACHE, EasyActionCategory.APP_CONTROL, requiresPackage = true),
    EasyActionDefinition(EasyActionKind.FORCE_STOP_APP, EasyActionCategory.APP_CONTROL, requiresPackage = true),
    EasyActionDefinition(EasyActionKind.CLEAR_APP_DATA, EasyActionCategory.APP_CONTROL, requiresPackage = true, destructive = true),
)
