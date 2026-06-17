package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ApkEntry
import `fun`.abbas.wps_adb.data.ApkPacker
import `fun`.abbas.wps_adb.data.DecompileApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DecompileApkSignerTest {

    @Test
    fun signApk_producesVerifiableV2Signature() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "apk-signer-${System.nanoTime()}").apply { mkdirs() }
        val unsignedApk = File(tempDir, "unsigned.apk")
        val signedApk = File(tempDir, "signed.apk")
        ApkPacker.repack(
            entries = listOf(
                ApkEntry("AndroidManifest.xml", minimalAndroidManifestBytes()),
                ApkEntry("classes.dex", byteArrayOf(0x64, 0x65, 0x78, 0x0a, 0x0a, 0x00, 0x00, 0x00)),
                ApkEntry("resources.arsc", ByteArray(16) { 0x02 }),
            ),
            output = unsignedApk,
        )

        val keystore = File(tempDir, "debug.keystore")
        DecompileApkSigner.sign(unsignedApk, signedApk, adbPath = "adb", keystoreFile = keystore)

        val result = ApkVerifier.Builder(signedApk).build().verify()
        assertTrue(result.isVerified, result.errors.joinToString { it.toString() })
        assertTrue(
            result.v2SchemeSigners.isNotEmpty() || result.v3SchemeSigners.isNotEmpty(),
            "Expected APK Signature Scheme v2 or v3",
        )
    }

    private fun minimalAndroidManifestBytes(): ByteArray {
        val resource = DecompileApkSignerTest::class.java.getResourceAsStream("/test-minimal/AndroidManifest.xml")
            ?: error("Missing test resource AndroidManifest.xml")
        return resource.use { it.readBytes() }
    }
}
