package `fun`.abbas.wps_adb.model

enum class EasyActionToastKind {
    SCREENSHOT_SAVED,
    SCREENSHOT_CLIPBOARD,
}

data class EasyActionToast(
    val kind: EasyActionToastKind,
    val deviceName: String,
    val success: Boolean,
    val filePath: String? = null,
    val id: Long = 0,
)
