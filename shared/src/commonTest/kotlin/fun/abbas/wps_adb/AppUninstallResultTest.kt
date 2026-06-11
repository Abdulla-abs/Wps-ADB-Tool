package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AppUninstallResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUninstallResultTest {
    @Test
    fun isSuccessful_acceptsZeroExitCode() {
        assertTrue(AppUninstallResult.isSuccessful(0, "Success"))
    }

    @Test
    fun isSuccessful_acceptsEmptyOutputWithZeroExit() {
        assertTrue(AppUninstallResult.isSuccessful(0, ""))
    }

    @Test
    fun isSuccessful_acceptsSuccessInOutputDespiteNonZeroExit() {
        assertTrue(AppUninstallResult.isSuccessful(1, "Success"))
    }

    @Test
    fun isSuccessful_rejectsUnknownPackage() {
        assertFalse(AppUninstallResult.isSuccessful(1, "Failure [DELETE_FAILED_INTERNAL_ERROR]"))
    }

    @Test
    fun isSuccessful_rejectsNotInstalled() {
        assertFalse(AppUninstallResult.isSuccessful(1, "Package com.example.demo is not installed"))
    }

    @Test
    fun isSuccessful_rejectsPermissionDenial() {
        assertFalse(AppUninstallResult.isSuccessful(255, "Permission Denial: not allowed to delete package"))
    }
}
