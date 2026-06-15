package `fun`.abbas.wps_adb.data

import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

internal object DecompileApkSigner {
    private const val KEYSTORE_PASS = "android"
    private const val KEY_ALIAS = "androiddebugkey"

    fun ensureDebugKeystore(keystoreFile: File): File {
        keystoreFile.parentFile?.mkdirs()
        if (!keystoreFile.isFile) {
            generateDebugKeystore(keystoreFile)
        }
        return keystoreFile
    }

    fun sign(unsignedApk: File, signedApk: File, adbPath: String, keystoreFile: File) {
        require(unsignedApk.isFile) { "Unsigned APK not found: ${unsignedApk.absolutePath}" }
        signedApk.parentFile?.mkdirs()

        val alignedApk = File(unsignedApk.parentFile, "${unsignedApk.nameWithoutExtension}.aligned.apk")
        try {
            ApkZipAligner.align(unsignedApk, alignedApk, adbPath)

            val keystore = ensureDebugKeystore(keystoreFile)
            val apksigner = AndroidSdkToolLocator.resolveBuildToolsBinary(adbPath, "apksigner")
            if (apksigner != null) {
                signWithApksignerTool(apksigner, keystore, alignedApk, signedApk)
            } else {
                signWithApksigLibrary(keystore, alignedApk, signedApk)
            }
        } finally {
            alignedApk.delete()
        }
    }

    private fun signWithApksignerTool(
        apksigner: String,
        keystore: File,
        unsignedApk: File,
        signedApk: File,
    ) {
        val result = ProcessBuilder(
            apksigner,
            "sign",
            "--ks", keystore.absolutePath,
            "--ks-key-alias", KEY_ALIAS,
            "--ks-pass", "pass:$KEYSTORE_PASS",
            "--key-pass", "pass:$KEYSTORE_PASS",
            "--v1-signing-enabled", "true",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true",
            "--out", signedApk.absolutePath,
            unsignedApk.absolutePath,
        )
            .redirectErrorStream(true)
            .start()
            .waitFor()
        if (result != 0 || !signedApk.isFile) {
            error("apksigner failed with exit code $result")
        }
    }

    private fun signWithApksigLibrary(keystoreFile: File, unsignedApk: File, signedApk: File) {
        val storePass = KEYSTORE_PASS.toCharArray()
        val keyStore = loadKeyStore(keystoreFile, storePass)
        val privateKey = keyStore.getKey(KEY_ALIAS, storePass) as PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
        val signerConfig = ApkSigner.SignerConfig.Builder(
            KEY_ALIAS,
            privateKey,
            listOf(certificate),
        ).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setMinSdkVersion(24)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    private fun loadKeyStore(keystoreFile: File, password: CharArray): KeyStore {
        for (type in listOf("PKCS12", "JKS")) {
            runCatching {
                return KeyStore.getInstance(type).also { keyStore ->
                    keystoreFile.inputStream().use { keyStore.load(it, password) }
                }
            }
        }
        error("Unsupported keystore format: ${keystoreFile.absolutePath}")
    }

    private fun generateDebugKeystore(keystore: File) {
        val result = ProcessBuilder(
            "keytool",
            "-genkeypair",
            "-v",
            "-keystore", keystore.absolutePath,
            "-alias", KEY_ALIAS,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-storepass", KEYSTORE_PASS,
            "-keypass", KEYSTORE_PASS,
            "-dname", "CN=Android Debug,O=Android,C=US",
        )
            .redirectErrorStream(true)
            .start()
            .waitFor()
        if (result != 0 || !keystore.isFile) {
            error("Failed to generate debug keystore")
        }
    }
}
