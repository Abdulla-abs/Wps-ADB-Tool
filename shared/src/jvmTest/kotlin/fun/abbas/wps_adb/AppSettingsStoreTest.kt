package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AppSettingsStore
import `fun`.abbas.wps_adb.data.ExecutableLocator
import `fun`.abbas.wps_adb.model.AppSettings
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.ScrcpyMaxFps
import `fun`.abbas.wps_adb.model.ScrcpyMaxSize
import `fun`.abbas.wps_adb.model.ScrcpyVideoBitRate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsStoreTest {
    @Test
    fun load_returnsDefaultsForMissingFile() {
        val file = File.createTempFile("wps-adb-settings-missing", ".properties")
        file.delete()

        val loaded = AppSettingsStore(file).load()
        assertEquals(ExecutableLocator.resolveAdbPath("adb"), loaded.adbPath)
        assertEquals(ExecutableLocator.resolveScrcpyPath("scrcpy"), loaded.scrcpyPath)
        assertEquals(AppSettings().copy(adbPath = loaded.adbPath, scrcpyPath = loaded.scrcpyPath), loaded)
    }

    @Test
    fun saveAndLoad_persistsAllFields() {
        val file = File.createTempFile("wps-adb-settings", ".properties")
        file.deleteOnExit()
        val store = AppSettingsStore(file)
        val settings = AppSettings(
            adbPath = "C:\\platform-tools\\adb.exe",
            scrcpyPath = "C:\\scrcpy\\scrcpy.exe",
            scrcpyConnection = ScrcpyConnectionOptions(
                enableAudio = true,
                maxSize = ScrcpyMaxSize.P1080,
                videoBitRate = ScrcpyVideoBitRate.HIGH,
                maxFps = ScrcpyMaxFps.FPS30,
                stayAwake = true,
                turnScreenOff = true,
                showTouches = true,
                alwaysOnTop = true,
                viewOnly = true,
            ),
            minPort = 5500,
            maxPort = 5599,
            scanIntervalSec = 30,
            parallelThreads = 8,
            logRetention = 5000,
            autoApproveKey = false,
            diagnosticTelemetry = true,
        )

        store.save(settings)
        val loaded = AppSettingsStore(file).load()

        assertEquals(settings, loaded)
    }

    @Test
    fun save_overwritesPreviousSettings() {
        val file = File.createTempFile("wps-adb-settings", ".properties")
        file.deleteOnExit()
        val store = AppSettingsStore(file)

        store.save(AppSettings(adbPath = "adb-old"))
        store.save(AppSettings(adbPath = "adb-new"))

        assertEquals("adb-new", store.load().adbPath)
        assertTrue(file.exists())
    }

    @Test
    fun saveAndLoad_persistsDataCacheDir() {
        val file = File.createTempFile("wps-adb-settings", ".properties")
        file.deleteOnExit()
        val store = AppSettingsStore(file)
        store.save(AppSettings(dataCacheDir = "D:\\WpsCache"))
        assertEquals("D:\\WpsCache", AppSettingsStore(file).load().dataCacheDir)
    }
}
