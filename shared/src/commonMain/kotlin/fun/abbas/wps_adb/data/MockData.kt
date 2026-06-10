package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AdbLog
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceApp
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.LogLevel
import `fun`.abbas.wps_adb.model.ScreenFormFactor

object MockData {
    val initialDevices = listOf(
        Device(
            id = "pixel6",
            name = "Pixel 6 - Test A",
            serial = "2201117PG",
            type = DeviceType.PHYSICAL,
            connectionType = ConnectionType.WIFI,
            status = DeviceStatus.ONLINE,
            androidVersion = "Android 13",
            batteryLevel = 85,
            isCharging = true,
            storageUsed = "108GB",
            storageTotal = "128GB",
            storagePercent = 85,
            screenshotUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuArxeJhmFHsT2MVqW446qyOJToROLQXO4X_AiTgP-v5qTgDbRfIroiXMqTC-dkOh0WNuBYOkKnD4XrXSzHeNvL86yqS8ftj-FMtrPL5zuK8BU-yAp8IybcWtTqRL9aEeBKBCKG-gvcG7DHilUVRkFCVkhJT5a2DzOa3bLXvnOXzQ3-WmEvok5s9UK6lkVHHODGYHqor_rEQ7xmH7lovV6pjohHrhDgMBjsAWshByn9l4cSvY9LkurlK9MwRtC1En4Y-50c2IWbVc0Fc",
            screenDescription = "Social Feed Client UI",
            formFactor = ScreenFormFactor.PHONE,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            apps = listOf(
                DeviceApp("PhotoFlow Feed", "com.android.photoflow", "photo_library"),
                DeviceApp("System Settings", "com.android.settings", "settings"),
                DeviceApp("ADB Control Server", "org.droid.control", "terminal"),
            ),
            activityLog = listOf(
                "activity_manager: Starting activity: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] pkg=com.android.photoflow }",
                "dalvikvm: GC_CONCURRENT freed 2048K, 12% free 12340K/13824K",
                "photoflow: Refreshing network feed image indices...",
                "window_manager: Focus gained by com.android.photoflow/MainActivity",
            ),
        ),
        Device(
            id = "galaxys22",
            name = "Galaxy Tab S9 - QA",
            serial = "SM-X710",
            type = DeviceType.PHYSICAL,
            connectionType = ConnectionType.USB,
            status = DeviceStatus.ONLINE,
            androidVersion = "Android 14",
            batteryLevel = 42,
            isCharging = true,
            storageUsed = "51GB",
            storageTotal = "128GB",
            storagePercent = 40,
            screenshotUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBRiqFBo6sRacj60La-ErYR6ZJffZNwZbscZ8ByV-wgIoy4xXrIaiJsFf2X0XKiSdiP8WM2x51ii-L2ZZeHiaAB_b2hRE6sV_gJG1KGmYgxtl9RO03qvaGyZbWtdsfZU0YnqAJmUrqHtk1RdUmafPVBob_hWhVX6WKD787qoaJUdNtyXGE5pGevbvQIeirKAvjBqP2Q4Xf_h6vDCYLAoJNPvpngES9z6Mu47ufV9lAUQABhM-t1CL5glNRBPH4RMpYB2qeEaWcxBfJi",
            screenDescription = "Tablet Dashboard UI",
            formFactor = ScreenFormFactor.TABLET,
            screenWidthPx = 2560,
            screenHeightPx = 1600,
            apps = listOf(
                DeviceApp("System Analytics Dashboard", "com.sec.sys.analytics", "monitoring"),
                DeviceApp("Wi-Fi Analyzer Tool", "com.sec.wifi.analyzer", "wifi"),
            ),
            activityLog = listOf(
                "sys_analytics: Service initialized successfully.",
                "cpu_monitor: Sampling complete: Core 0-7 active, Temp: 38C",
                "gpu_monitor: Vulkan render context loaded.",
                "power_manager: State changed from normal to idle saver.",
            ),
        ),
        Device(
            id = "pixel4api30",
            name = "Pixel_4_API_30",
            serial = "emulator-5554",
            type = DeviceType.EMULATOR,
            connectionType = ConnectionType.EMULATOR,
            status = DeviceStatus.ONLINE,
            androidVersion = "Android 11",
            batteryLevel = 100,
            isCharging = true,
            storageUsed = "12GB",
            storageTotal = "64GB",
            storagePercent = 18,
            screenshotUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDN_u3BGkuFm-6_CudwuktxqPZwmeOL83hudngZsLym1zBfIlRnOe2oK8OJGLhpgPrFda8UBQvFHLlqFHkJYjciv1cOZy8aExE9mU4UXvYyCoEIUKB83X6DkKGDem9-Z1D-FE4fMgLz-OIFnHerxgu973TTOekUpRGSQWY6VgQ0Spb9vI1c_c4iQFBA9t2hrDPgrRw5WR0W4JO4UR1A7ZgPZDXZfWCHjgZ2AdA4HCCifDK_0pTBi7BFx5LdJ3xyK8FuaUbRjtwd8AI9",
            screenDescription = "ADB Log Console App",
            apps = listOf(
                DeviceApp("Log Inspector Terminal", "com.android.terminal.inspect", "code"),
                DeviceApp("AOSP Test Harness", "org.aosp.testharness", "bug_report"),
            ),
            activityLog = listOf(
                "adb_daemon: Listening on interface localhost:5554",
                "emulator_x86: IPC buffer initialized with 4MB memory range.",
                "system_server: PackageManager found 143 installed packages.",
                "logcat_capture: Streaming active stream buffer...",
            ),
        ),
        Device(
            id = "oneplus9",
            name = "OnePlus 9 - Debug",
            serial = "OP721110",
            type = DeviceType.PHYSICAL,
            connectionType = ConnectionType.USB,
            status = DeviceStatus.OFFLINE,
            androidVersion = "Android 13",
            batteryLevel = 0,
            isCharging = false,
            storageUsed = "0GB",
            storageTotal = "256GB",
            storagePercent = 0,
            screenshotUrl = "",
            screenDescription = "Device is offline",
            activityLog = listOf(
                "adb_daemon: Connection closed by target client peer.",
                "hardware_udev: USB disconnect on port 5-2: OP721110",
            ),
        ),
        Device(
            id = "redminote10",
            name = "Mi TV Stick 4K",
            serial = "192.168.0.88:5555",
            type = DeviceType.PHYSICAL,
            connectionType = ConnectionType.WIFI,
            status = DeviceStatus.ONLINE,
            androidVersion = "Android TV 11",
            batteryLevel = 100,
            isCharging = true,
            storageUsed = "4GB",
            storageTotal = "8GB",
            storagePercent = 50,
            screenshotUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCu1EKXMBtlcteTDD1nASX1ZbSjR0_rF68g3Wp4yFseOfsYCdv2G2W46A5zN3Dpx3FvhhjLApYTofuOydO8O2jtaKIFr5gG0q3zswkgOitr-Nivsij2JsRVTruJQ7Pg9Daaiux9OVLevQwgBTkbvinUNCAEmiKH22UwaUEOaMYrKttvAhmGQ2MmJZVM3zWB3jqW9RDHgQvTgktampVN0Me8KFb3XuZ5XPUr6b6wRuWn2xUPN5nD63OgzlmdBjTrwxSdOKvxaRCLmlFs",
            screenDescription = "Android TV Launcher",
            formFactor = ScreenFormFactor.TV,
            screenWidthPx = 1920,
            screenHeightPx = 1080,
            apps = listOf(
                DeviceApp("TV Launcher", "com.google.android.tvlauncher", "tv"),
                DeviceApp("YouTube", "com.google.android.youtube.tv", "play_circle"),
            ),
            activityLog = listOf(
                "tv_core: HDMI display mode 3840x2160@60Hz",
                "leanback: Home row focused on Apps row",
                "network_service: Connected over Wi-Fi 5GHz",
            ),
        ),
    )

    val initialLogs = listOf(
        AdbLog("1", "10:14:19.402", "AdbDaemon", LogLevel.I, "ADB Server version 1.0.41 starting...", "system"),
        AdbLog("2", "10:14:19.421", "AdbDaemon", LogLevel.I, "Binding socket listener to port localhost:5037", "system"),
        AdbLog("3", "10:14:20.105", "ServiceTracker", LogLevel.D, "Discovered connected device via USB: type physical [SM-S901B]", "galaxys22"),
        AdbLog("4", "10:14:20.334", "EmulatorControl", LogLevel.D, "Attaching local socket port 5554 to emulator daemon", "pixel4api30"),
        AdbLog("5", "10:14:21.011", "ActivityManager", LogLevel.I, "Device pixel6 is connected over local network (192.168.1.104:5555)", "pixel6"),
        AdbLog("6", "10:14:21.554", "DeviceTracker", LogLevel.W, "Connection timeout exceeded on interface OP721110. Flagging: OFFLINE", "oneplus9"),
        AdbLog("7", "10:14:22.091", "ApkInstaller", LogLevel.I, "Acomplished package scanning on user-pushed file tree", "system"),
        AdbLog("8", "10:14:23.003", "LogcatService", LogLevel.V, "Piping main system ring logs buffer containing 1,842 events...", "system"),
    )
}
