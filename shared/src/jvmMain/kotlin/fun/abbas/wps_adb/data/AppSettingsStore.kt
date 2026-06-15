package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.ScrcpyMaxFps
import `fun`.abbas.wps_adb.model.ScrcpyMaxSize
import `fun`.abbas.wps_adb.model.ScrcpyVideoBitRate
import java.io.File
import java.util.Properties

class AppSettingsStore(
    private val storeFile: File = defaultStoreFile(),
) {
    fun load(): AppSettings {
        val settings = if (!storeFile.exists()) {
            AppSettings()
        } else {
            val props = Properties().apply {
                storeFile.inputStream().buffered().use(::load)
            }
            AppSettings(
                adbPath = props.getProperty(KEY_ADB_PATH, "adb"),
                scrcpyPath = props.getProperty(KEY_SCRCPY_PATH, "scrcpy"),
                scrcpyConnection = ScrcpyConnectionOptions(
                    enableAudio = props.getProperty(KEY_SCRCPY_ENABLE_AUDIO, "false").toBooleanStrictOrNull() ?: false,
                    maxSize = enumOrDefault(props.getProperty(KEY_SCRCPY_MAX_SIZE), ScrcpyMaxSize.ORIGINAL),
                    videoBitRate = enumOrDefault(props.getProperty(KEY_SCRCPY_VIDEO_BIT_RATE), ScrcpyVideoBitRate.DEFAULT),
                    maxFps = enumOrDefault(props.getProperty(KEY_SCRCPY_MAX_FPS), ScrcpyMaxFps.DEFAULT),
                    stayAwake = props.getProperty(KEY_SCRCPY_STAY_AWAKE, "false").toBooleanStrictOrNull() ?: false,
                    turnScreenOff = props.getProperty(KEY_SCRCPY_TURN_SCREEN_OFF, "false").toBooleanStrictOrNull() ?: false,
                    showTouches = props.getProperty(KEY_SCRCPY_SHOW_TOUCHES, "false").toBooleanStrictOrNull() ?: false,
                    alwaysOnTop = props.getProperty(KEY_SCRCPY_ALWAYS_ON_TOP, "false").toBooleanStrictOrNull() ?: false,
                    viewOnly = props.getProperty(KEY_SCRCPY_VIEW_ONLY, "false").toBooleanStrictOrNull() ?: false,
                ),
                minPort = props.getProperty(KEY_MIN_PORT, "5555").toIntOrNull() ?: 5555,
                maxPort = props.getProperty(KEY_MAX_PORT, "5585").toIntOrNull() ?: 5585,
                scanIntervalSec = props.getProperty(KEY_SCAN_INTERVAL_SEC, "15").toIntOrNull() ?: 15,
                parallelThreads = props.getProperty(KEY_PARALLEL_THREADS, "4").toIntOrNull() ?: 4,
                logRetention = props.getProperty(KEY_LOG_RETENTION, "2500").toIntOrNull() ?: 2500,
                autoApproveKey = props.getProperty(KEY_AUTO_APPROVE_KEY, "true").toBooleanStrictOrNull() ?: true,
                diagnosticTelemetry = props.getProperty(KEY_DIAGNOSTIC_TELEMETRY, "false").toBooleanStrictOrNull() ?: false,
                dataCacheDir = props.getProperty(KEY_DATA_CACHE_DIR, ""),
            )
        }
        return settings.withResolvedExecutablePaths()
    }

    private fun AppSettings.withResolvedExecutablePaths(): AppSettings {
        val resolvedAdb = if (adbPath.isBlank() || adbPath == "adb") {
            ExecutableLocator.resolveAdbPath(adbPath)
        } else {
            adbPath
        }
        val resolvedScrcpy = if (scrcpyPath.isBlank() || scrcpyPath == "scrcpy") {
            ExecutableLocator.resolveScrcpyPath(scrcpyPath)
        } else {
            scrcpyPath
        }
        val resolved = copy(adbPath = resolvedAdb, scrcpyPath = resolvedScrcpy)
        if (resolved.adbPath != adbPath || resolved.scrcpyPath != scrcpyPath) {
            save(resolved)
        }
        return resolved
    }

    fun save(settings: AppSettings) {
        val props = Properties().apply {
            setProperty(KEY_ADB_PATH, settings.adbPath)
            setProperty(KEY_SCRCPY_PATH, settings.scrcpyPath)
            setProperty(KEY_SCRCPY_ENABLE_AUDIO, settings.scrcpyConnection.enableAudio.toString())
            setProperty(KEY_SCRCPY_MAX_SIZE, settings.scrcpyConnection.maxSize.name)
            setProperty(KEY_SCRCPY_VIDEO_BIT_RATE, settings.scrcpyConnection.videoBitRate.name)
            setProperty(KEY_SCRCPY_MAX_FPS, settings.scrcpyConnection.maxFps.name)
            setProperty(KEY_SCRCPY_STAY_AWAKE, settings.scrcpyConnection.stayAwake.toString())
            setProperty(KEY_SCRCPY_TURN_SCREEN_OFF, settings.scrcpyConnection.turnScreenOff.toString())
            setProperty(KEY_SCRCPY_SHOW_TOUCHES, settings.scrcpyConnection.showTouches.toString())
            setProperty(KEY_SCRCPY_ALWAYS_ON_TOP, settings.scrcpyConnection.alwaysOnTop.toString())
            setProperty(KEY_SCRCPY_VIEW_ONLY, settings.scrcpyConnection.viewOnly.toString())
            setProperty(KEY_MIN_PORT, settings.minPort.toString())
            setProperty(KEY_MAX_PORT, settings.maxPort.toString())
            setProperty(KEY_SCAN_INTERVAL_SEC, settings.scanIntervalSec.toString())
            setProperty(KEY_PARALLEL_THREADS, settings.parallelThreads.toString())
            setProperty(KEY_LOG_RETENTION, settings.logRetention.toString())
            setProperty(KEY_AUTO_APPROVE_KEY, settings.autoApproveKey.toString())
            setProperty(KEY_DIAGNOSTIC_TELEMETRY, settings.diagnosticTelemetry.toString())
            setProperty(KEY_DATA_CACHE_DIR, settings.dataCacheDir)
        }
        storeFile.parentFile?.mkdirs()
        storeFile.outputStream().buffered().use { props.store(it, "WpsAdbTool settings") }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T {
        if (value.isNullOrBlank()) return default
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }

    companion object {
        private const val KEY_ADB_PATH = "adbPath"
        private const val KEY_SCRCPY_PATH = "scrcpyPath"
        private const val KEY_SCRCPY_ENABLE_AUDIO = "scrcpy.enableAudio"
        private const val KEY_SCRCPY_MAX_SIZE = "scrcpy.maxSize"
        private const val KEY_SCRCPY_VIDEO_BIT_RATE = "scrcpy.videoBitRate"
        private const val KEY_SCRCPY_MAX_FPS = "scrcpy.maxFps"
        private const val KEY_SCRCPY_STAY_AWAKE = "scrcpy.stayAwake"
        private const val KEY_SCRCPY_TURN_SCREEN_OFF = "scrcpy.turnScreenOff"
        private const val KEY_SCRCPY_SHOW_TOUCHES = "scrcpy.showTouches"
        private const val KEY_SCRCPY_ALWAYS_ON_TOP = "scrcpy.alwaysOnTop"
        private const val KEY_SCRCPY_VIEW_ONLY = "scrcpy.viewOnly"
        private const val KEY_MIN_PORT = "minPort"
        private const val KEY_MAX_PORT = "maxPort"
        private const val KEY_SCAN_INTERVAL_SEC = "scanIntervalSec"
        private const val KEY_PARALLEL_THREADS = "parallelThreads"
        private const val KEY_LOG_RETENTION = "logRetention"
        private const val KEY_AUTO_APPROVE_KEY = "autoApproveKey"
        private const val KEY_DIAGNOSTIC_TELEMETRY = "diagnosticTelemetry"
        private const val KEY_DATA_CACHE_DIR = "dataCacheDir"

        fun defaultStoreFile(): File {
            val dir = File(System.getProperty("user.home"), ".wps-adb-tool")
            dir.mkdirs()
            return File(dir, "settings.properties")
        }
    }
}
