package `fun`.abbas.wps_adb

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkInstallPathTest {
    @Test
    fun usesDirectInstallForApkExtension() {
        assertTrue(File("GameHub.apk").extension.equals("apk", ignoreCase = true))
        assertTrue(File("GameHub.APK").extension.equals("apk", ignoreCase = true))
    }

    @Test
    fun usesPushInstallForNonApkExtension() {
        assertFalse(File("GameHub-2.3.47-tv19.apk(1).1").extension.equals("apk", ignoreCase = true))
        assertFalse(File("app.1").extension.equals("apk", ignoreCase = true))
    }
}
