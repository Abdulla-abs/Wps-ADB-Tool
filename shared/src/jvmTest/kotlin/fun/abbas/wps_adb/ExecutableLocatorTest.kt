package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ExecutableLocator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecutableLocatorTest {
    @Test
    fun resolveAdbPath_usesAbsolutePathWhenFileExists() {
        val temp = File.createTempFile("wps-adb-locator", ".bat")
        temp.deleteOnExit()
        assertEquals(temp.absolutePath, ExecutableLocator.resolveAdbPath(temp.absolutePath))
    }

    @Test
    fun resolveAdbPath_fallsBackToDiscoverOrDefaultWhenExplicitPathMissing() {
        val configured = "C:\\missing\\adb.exe"
        val resolved = ExecutableLocator.resolveAdbPath(configured)
        assertEquals(ExecutableLocator.discoverAdbPath() ?: "adb", resolved)
    }

    @Test
    fun resolveAdbPath_discoversFromDefaultPlaceholder() {
        val discovered = ExecutableLocator.discoverAdbPath()
        val resolved = ExecutableLocator.resolveAdbPath("adb")
        if (discovered != null) {
            assertEquals(discovered, resolved)
            assertTrue(File(resolved).isAbsolute)
        } else {
            assertEquals("adb", resolved)
        }
    }

    @Test
    fun resolveScrcpyPath_usesAbsolutePathWhenFileExists() {
        val temp = File.createTempFile("wps-scrcpy-locator", ".bat")
        temp.deleteOnExit()
        assertEquals(temp.absolutePath, ExecutableLocator.resolveScrcpyPath(temp.absolutePath))
    }

    @Test
    fun resolveScrcpyPath_fallsBackToDiscoverOrDefaultWhenExplicitPathMissing() {
        val configured = "C:\\missing\\scrcpy.exe"
        val resolved = ExecutableLocator.resolveScrcpyPath(configured)
        assertEquals(ExecutableLocator.discoverScrcpyPath() ?: "scrcpy", resolved)
    }
}
