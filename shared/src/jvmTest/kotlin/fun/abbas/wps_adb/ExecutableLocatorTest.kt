package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ExecutableLocator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutableLocatorTest {
    @Test
    fun resolveAdbPath_usesAbsolutePathWhenFileExists() {
        val temp = File.createTempFile("wps-adb-locator", ".bat")
        temp.deleteOnExit()
        assertEquals(temp.absolutePath, ExecutableLocator.resolveAdbPath(temp.absolutePath))
    }

    @Test
    fun resolveAdbPath_returnsEmptyWhenUnconfigured() {
        assertEquals("", ExecutableLocator.resolveAdbPath(""))
        assertEquals("", ExecutableLocator.resolveAdbPath("adb"))
        assertEquals("", ExecutableLocator.resolveAdbPath("  "))
    }

    @Test
    fun resolveAdbPath_keepsMissingExplicitPathWithoutDiscovering() {
        val configured = "C:\\missing\\adb.exe"
        assertEquals(configured, ExecutableLocator.resolveAdbPath(configured))
    }

    @Test
    fun resolveRunnableAdbPath_usesAbsolutePathWhenConfiguredFileExists() {
        val temp = File.createTempFile("wps-adb-locator", ".bat")
        temp.deleteOnExit()
        assertEquals(temp.absolutePath, ExecutableLocator.resolveRunnableAdbPath(temp.absolutePath))
    }

    @Test
    fun resolveRunnableAdbPath_fallsBackToDiscoveredWhenMissingOrUnconfigured() {
        val discovered = ExecutableLocator.discoverAdbPath()
        assertEquals(discovered, ExecutableLocator.resolveRunnableAdbPath(""))
        assertEquals(discovered, ExecutableLocator.resolveRunnableAdbPath("adb"))
        assertEquals(discovered, ExecutableLocator.resolveRunnableAdbPath("C:\\nonexistent\\adb.exe"))
    }

    @Test
    fun isAdbConfigured_requiresExplicitPath() {
        assertFalse(ExecutableLocator.isAdbConfigured(""))
        assertFalse(ExecutableLocator.isAdbConfigured("adb"))
        assertTrue(ExecutableLocator.isAdbConfigured("C:\\platform-tools\\adb.exe"))
    }

    @Test
    fun resolveScrcpyPath_usesAbsolutePathWhenFileExists() {
        val temp = File.createTempFile("wps-scrcpy-locator", ".bat")
        temp.deleteOnExit()
        assertEquals(temp.absolutePath, ExecutableLocator.resolveScrcpyPath(temp.absolutePath))
    }

    @Test
    fun resolveScrcpyPath_returnsEmptyWhenUnconfigured() {
        assertEquals("", ExecutableLocator.resolveScrcpyPath(""))
        assertEquals("", ExecutableLocator.resolveScrcpyPath("scrcpy"))
    }

    @Test
    fun resolveScrcpyPath_keepsMissingExplicitPathWithoutDiscovering() {
        val configured = "C:\\missing\\scrcpy.exe"
        assertEquals(configured, ExecutableLocator.resolveScrcpyPath(configured))
    }
}
