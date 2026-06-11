package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ApkBadgingParser
import `fun`.abbas.wps_adb.data.ApkMetadataResolver
import `fun`.abbas.wps_adb.data.AndroidSdkToolLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.io.File

class ApkBadgingParserTest {

    @Test
    fun parseAaptOutput_extractsPackageLabelAndVersion() {
        val output = """
            package: name='com.example.demo' versionCode='42' versionName='2.1.0'
            application-label:'Demo App'
        """.trimIndent()

        val metadata = ApkBadgingParser.parseAaptOutput(output)

        assertNotNull(metadata)
        assertEquals("com.example.demo", metadata.packageName)
        assertEquals("Demo App", metadata.appLabel)
        assertEquals("2.1.0", metadata.versionName)
        assertEquals(null, metadata.launchActivity)
    }

    @Test
    fun parseAaptOutput_extractsLaunchableActivity() {
        val output = """
            package: name='com.example.demo' versionCode='1' versionName='1.0'
            launchable-activity: name='com.example.demo/.MainActivity'  label='Demo' icon=''
        """.trimIndent()

        val metadata = ApkBadgingParser.parseAaptOutput(output)

        assertNotNull(metadata)
        assertEquals("com.example.demo/.MainActivity", metadata.launchActivity)
    }

    @Test
    fun parseAaptOutput_normalizesFullyQualifiedLaunchActivity() {
        val output = """
            package: name='com.wanpishiky.android' versionCode='141' versionName='1'
            launchable-activity: name='com.jsj.tvgamecollections.GameActivity'  label='Demo' icon=''
        """.trimIndent()

        val metadata = ApkBadgingParser.parseAaptOutput(output)

        assertNotNull(metadata)
        assertEquals(
            "com.wanpishiky.android/com.jsj.tvgamecollections.GameActivity",
            metadata.launchActivity,
        )
    }

    @Test
    fun parseApkanalyzerOutput_extractsApplicationId() {
        val metadata = ApkBadgingParser.parseApkanalyzerOutput("com.example.demo\n")

        assertNotNull(metadata)
        assertEquals("com.example.demo", metadata.packageName)
    }

    @Test
    fun parseAaptOutput_returnsNullWhenPackageMissing() {
        assertNull(ApkBadgingParser.parseAaptOutput("sdkVersion:'33'"))
    }

    @Test
    fun isMockPackage_detectsFallbackMetadata() {
        assertEquals(true, ApkMetadataResolver.isMockPackage("com.mock.demo"))
        assertEquals(false, ApkMetadataResolver.isMockPackage("com.example.demo"))
    }

    @Test
    fun fromFileName_buildsMockPackageName() {
        val metadata = ApkMetadataResolver.fromFileName("My Demo.apk")

        assertEquals("com.mock.my_demo", metadata.packageName)
        assertEquals("My Demo", metadata.appLabel)
    }

    @Test
    fun resolveAapt_findsBuildToolsBinaryFromConfiguredAdbPath() {
        val sdkRoot = File(System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: return)
        val adbPath = File(sdkRoot, "platform-tools/adb${if (File.separatorChar == '\\') ".exe" else ""}")
        if (!adbPath.isFile) return

        val aaptPath = AndroidSdkToolLocator.resolveAapt(adbPath.absolutePath)

        assertNotNull(aaptPath)
        assertEquals(true, File(aaptPath).isFile)
    }
}
