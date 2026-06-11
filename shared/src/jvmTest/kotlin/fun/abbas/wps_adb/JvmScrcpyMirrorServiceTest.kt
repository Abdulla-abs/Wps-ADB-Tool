package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ExecutableLocator
import `fun`.abbas.wps_adb.data.JvmScrcpyMirrorService
import `fun`.abbas.wps_adb.model.ScrcpyConnectionOptions
import `fun`.abbas.wps_adb.model.ScrcpyMaxFps
import `fun`.abbas.wps_adb.model.ScrcpyMaxSize
import `fun`.abbas.wps_adb.model.ScrcpyVideoBitRate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmScrcpyMirrorServiceTest {

    @Test
    fun buildCommand_appendsConnectionOptions() {
        val command = JvmScrcpyMirrorService.buildCommand(
            scrcpyPath = "scrcpy",
            serial = "emulator-5554",
            deviceName = "Pixel 7",
            options = ScrcpyConnectionOptions(
                enableAudio = true,
                maxSize = ScrcpyMaxSize.P1080,
                videoBitRate = ScrcpyVideoBitRate.MEDIUM,
                maxFps = ScrcpyMaxFps.FPS30,
                stayAwake = true,
            ),
        )
        assertTrue("--no-audio" !in command)
        assertTrue("--max-size=1080" in command)
        assertTrue("--video-bit-rate=4M" in command)
        assertTrue("--max-fps=30" in command)
        assertTrue("--stay-awake" in command)
    }

    @Test
    fun buildCommand_defaultOptionsDisableAudio() {
        val command = JvmScrcpyMirrorService.buildCommand(
            scrcpyPath = "scrcpy",
            serial = "serial",
            deviceName = "Device",
        )
        assertTrue("--no-audio" in command)
    }

    @Test
    fun buildCommand_includesSerialAndWindowTitle() {
        val command = JvmScrcpyMirrorService.buildCommand(
            scrcpyPath = "scrcpy",
            serial = "emulator-5554",
            deviceName = "Pixel 7",
        )
        assertEquals(
            listOf("scrcpy", "-s", "emulator-5554", "--window-title", "WpsAdbTool - Pixel 7", "--no-audio"),
            command,
        )
    }

    @Test
    fun resolveScrcpyPath_usesAbsolutePathWhenFileExists() {
        val temp = File.createTempFile("scrcpy-test", ".exe")
        temp.deleteOnExit()
        assertEquals(temp.absolutePath, JvmScrcpyMirrorService.resolveScrcpyPath(temp.absolutePath))
    }

    @Test
    fun resolveScrcpyPath_discoversFromPathOrFallsBackToDefaultName() {
        val expected = ExecutableLocator.discoverScrcpyPath() ?: "scrcpy"
        assertEquals(expected, JvmScrcpyMirrorService.resolveScrcpyPath(""))
        assertEquals(expected, JvmScrcpyMirrorService.resolveScrcpyPath("scrcpy"))
        assertEquals(expected, JvmScrcpyMirrorService.resolveScrcpyPath("C:\\missing\\scrcpy.exe"))
    }

    @Test
    fun start_isIdempotentWhenAlreadyRunning() {
        val service = JvmScrcpyMirrorService(
            scrcpyPathProvider = { "scrcpy" },
            adbPathProvider = { "adb" },
            processStarter = { _, _ -> longRunningProcess() },
        )
        service.setExitListener { _, _, _ -> }

        val first = service.start("tab-1", "serial-1", "Device", ScrcpyConnectionOptions())
        val second = service.start("tab-1", "serial-1", "Device", ScrcpyConnectionOptions())

        assertTrue(first.success)
        assertTrue(second.success)
        assertEquals("Already running", second.message)
        service.stopAll()
    }

    @Test
    fun stop_removesSession() {
        val service = JvmScrcpyMirrorService(
            scrcpyPathProvider = { "scrcpy" },
            adbPathProvider = { "adb" },
            processStarter = { _, _ -> longRunningProcess() },
        )
        service.setExitListener { _, _, _ -> }
        service.start("tab-1", "serial-1", "Device", ScrcpyConnectionOptions())
        assertTrue(service.isRunning("tab-1"))

        service.stop("tab-1")
        assertFalse(service.isRunning("tab-1"))
    }

    @Test
    fun start_passesAdbEnvironmentVariable() {
        val adbFile = File.createTempFile("adb-test", ".exe")
        adbFile.deleteOnExit()
        var capturedEnv: Map<String, String>? = null
        val service = JvmScrcpyMirrorService(
            scrcpyPathProvider = { "scrcpy" },
            adbPathProvider = { adbFile.absolutePath },
            processStarter = { _, env ->
                capturedEnv = env
                longRunningProcess()
            },
        )
        service.setExitListener { _, _, _ -> }
        service.start("tab-1", "serial-1", "Device", ScrcpyConnectionOptions())

        assertEquals(adbFile.absolutePath, capturedEnv?.get("ADB"))
        service.stopAll()
    }

    private fun longRunningProcess(): Process {
        val os = System.getProperty("os.name").orEmpty()
        return if (os.lowercase().contains("windows")) {
            ProcessBuilder("cmd", "/c", "ping", "127.0.0.1", "-n", "60").start()
        } else {
            ProcessBuilder("sleep", "60").start()
        }
    }
}
