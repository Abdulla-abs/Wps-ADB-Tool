package `fun`.abbas.wps_adb.data

import java.io.File

object JvmAdbKeyManager {
    private val androidDir: File
        get() = File(System.getProperty("user.home"), ".android")

    fun hasKeyPair(): Boolean {
        val privateKey = File(androidDir, "adbkey")
        val publicKey = File(androidDir, "adbkey.pub")
        return privateKey.exists() && publicKey.exists()
    }

    fun ensureKeyPair(adbPath: String): Boolean {
        if (hasKeyPair()) return true
        androidDir.mkdirs()
        val privateKey = File(androidDir, "adbkey")
        val result = JvmAdbRunner { adbPath }.run(listOf("keygen", privateKey.absolutePath))
        return result.success && hasKeyPair()
    }
}
