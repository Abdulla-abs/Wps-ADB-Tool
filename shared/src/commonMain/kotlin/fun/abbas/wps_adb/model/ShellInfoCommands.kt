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
    EasyActionKind.INFO_CURRENT_FOCUS,
    EasyActionKind.INFO_ACTIVITY_TOP,
    EasyActionKind.INFO_RESUMED_ACTIVITY,
    EasyActionKind.INFO_RESUMED_ACTIVITY_PACKAGE,
    EasyActionKind.INFO_MEMINFO_ALL,
    EasyActionKind.INFO_MEMINFO_PACKAGE,
    EasyActionKind.INFO_CPUINFO,
    EasyActionKind.INFO_PROCSTATS,
)

fun EasyActionKind.isShellInfoQuery(): Boolean = this in ShellInfoQueryKinds

object ShellInfoCommands {
    fun shellCommand(kind: EasyActionKind, packageName: String? = null): String? = when (kind) {
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
        EasyActionKind.INFO_CURRENT_FOCUS -> "dumpsys window | grep mCurrentFocus"
        EasyActionKind.INFO_ACTIVITY_TOP -> "dumpsys activity top | grep ACTIVITY"
        EasyActionKind.INFO_RESUMED_ACTIVITY -> """dumpsys activity | grep -i "ResumedActivity""""
        EasyActionKind.INFO_RESUMED_ACTIVITY_PACKAGE ->
            """dumpsys activity | grep -i ResumedActivity | head -1 | tr ' ' '\n' | grep '/' | head -1 | cut -d'/' -f1"""
        EasyActionKind.INFO_MEMINFO_ALL -> "dumpsys meminfo"
        EasyActionKind.INFO_MEMINFO_PACKAGE -> {
            val pkg = packageName?.trim()?.takeIf { it.isNotBlank() } ?: return null
            "dumpsys meminfo $pkg"
        }
        EasyActionKind.INFO_CPUINFO -> "dumpsys cpuinfo"
        EasyActionKind.INFO_PROCSTATS -> "dumpsys procstats"
        else -> null
    }

    fun wrappedShellInput(kind: EasyActionKind, packageName: String? = null): String? {
        val command = shellCommand(kind, packageName) ?: return null
        return "echo \"\" && echo \">> $command\" && $command"
    }
}
