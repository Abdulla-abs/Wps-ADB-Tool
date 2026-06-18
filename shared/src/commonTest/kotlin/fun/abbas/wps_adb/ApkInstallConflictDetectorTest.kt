package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ApkInstallConflictDetector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkInstallConflictDetectorTest {
    @Test
    fun detectsSignatureMismatchFailure() {
        val message =
            "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.example signatures do not match previously installed version; ignoring!]"
        assertTrue(ApkInstallConflictDetector.isExistingPackageConflict(message))
    }

    @Test
    fun detectsAlreadyExistsFailure() {
        val message =
            "Failure [INSTALL_FAILED_ALREADY_EXISTS: Attempt to re-install com.example without first uninstalling.]"
        assertTrue(ApkInstallConflictDetector.isExistingPackageConflict(message))
    }

    @Test
    fun ignoresUnrelatedInstallFailure() {
        assertFalse(ApkInstallConflictDetector.isExistingPackageConflict("Failure [INSTALL_PARSE_FAILED]"))
        assertFalse(ApkInstallConflictDetector.isExistingPackageConflict("APK file not found"))
    }
}
