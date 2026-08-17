package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.validateAdbExecutablePath
import `fun`.abbas.wps_adb.data.validateScrcpyExecutablePath
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutablePathValidatorTest {
    @Test
    fun validateAdb_emptyOrPlaceholderIsInvalid() = runBlocking {
        assertFalse(validateAdbExecutablePath(""))
        assertFalse(validateAdbExecutablePath("adb"))
    }

    @Test
    fun validateAdb_existingExecutableIsValid() = runBlocking {
        val fake = fakeExecutable()
        assertTrue(validateAdbExecutablePath(fake.absolutePath))
    }

    @Test
    fun validateScrcpy_missingPathIsInvalid() = runBlocking {
        assertFalse(validateScrcpyExecutablePath(""))
        assertFalse(validateScrcpyExecutablePath("C:\\missing\\scrcpy.exe"))
    }

    private fun fakeExecutable(): File {
        val windows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")
        val file = if (windows) {
            File.createTempFile("fake-adb", ".cmd").also {
                it.writeText("@echo off\r\nif \"%1\"==\"version\" exit /b 0\r\nexit /b 0\r\n")
            }
        } else {
            File.createTempFile("fake-adb", ".sh").also {
                it.writeText("#!/bin/sh\nexit 0\n")
                it.setExecutable(true)
            }
        }
        file.deleteOnExit()
        return file
    }
}
