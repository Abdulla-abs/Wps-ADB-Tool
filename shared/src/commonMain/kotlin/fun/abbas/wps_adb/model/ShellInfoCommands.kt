package `fun`.abbas.wps_adb.model

val ShellInfoQueryKinds: Set<EasyActionKind> = setOf(
    EasyActionKind.INFO_DEVICE_MODEL,
    EasyActionKind.INFO_DEVICE_BRAND,
    EasyActionKind.INFO_DEVICE_MANUFACTURER,
    EasyActionKind.INFO_HARDWARE_PLATFORM,
    EasyActionKind.INFO_CPU_ABI,
    EasyActionKind.INFO_SCREEN_SIZE,
    EasyActionKind.INFO_SCREEN_DENSITY,
    EasyActionKind.INFO_BATTERY_LEVEL,
    EasyActionKind.INFO_BATTERY_STATUS,
    EasyActionKind.INFO_ANDROID_VERSION,
    EasyActionKind.INFO_SDK_VERSION,
    EasyActionKind.INFO_SECURITY_PATCH,
    EasyActionKind.INFO_BUILD_ID,
    EasyActionKind.INFO_KERNEL_VERSION,
    EasyActionKind.INFO_DATA_STORAGE,
    EasyActionKind.INFO_TOTAL_MEMORY,
    EasyActionKind.INFO_AVAILABLE_MEMORY,
)

fun EasyActionKind.isShellInfoQuery(): Boolean = this in ShellInfoQueryKinds

object ShellInfoCommands {
    fun shellCommand(kind: EasyActionKind): String? = when (kind) {
        EasyActionKind.INFO_DEVICE_MODEL -> "getprop ro.product.model"
        EasyActionKind.INFO_DEVICE_BRAND -> "getprop ro.product.brand"
        EasyActionKind.INFO_DEVICE_MANUFACTURER -> "getprop ro.product.manufacturer"
        EasyActionKind.INFO_HARDWARE_PLATFORM -> "getprop ro.hardware"
        EasyActionKind.INFO_CPU_ABI -> "getprop ro.product.cpu.abi"
        EasyActionKind.INFO_SCREEN_SIZE -> "wm size"
        EasyActionKind.INFO_SCREEN_DENSITY -> "wm density"
        EasyActionKind.INFO_BATTERY_LEVEL -> "dumpsys battery | grep level"
        EasyActionKind.INFO_BATTERY_STATUS -> "dumpsys battery | grep status"
        EasyActionKind.INFO_ANDROID_VERSION -> "getprop ro.build.version.release"
        EasyActionKind.INFO_SDK_VERSION -> "getprop ro.build.version.sdk"
        EasyActionKind.INFO_SECURITY_PATCH -> "getprop ro.build.version.security_patch"
        EasyActionKind.INFO_BUILD_ID -> "getprop ro.build.display.id"
        EasyActionKind.INFO_KERNEL_VERSION -> "uname -a"
        EasyActionKind.INFO_DATA_STORAGE -> "df -h /data"
        EasyActionKind.INFO_TOTAL_MEMORY -> "free -h 2>/dev/null || head -3 /proc/meminfo"
        EasyActionKind.INFO_AVAILABLE_MEMORY -> "cat /proc/meminfo | grep MemAvailable"
        else -> null
    }

    fun wrappedShellInput(kind: EasyActionKind): String? {
        val command = shellCommand(kind) ?: return null
        return "echo \"\" && echo \">> $command\" && $command"
    }
}
