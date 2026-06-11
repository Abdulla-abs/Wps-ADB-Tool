package `fun`.abbas.wps_adb.data

import kotlin.random.Random

data class AdbQrCredentials(
    val serviceName: String,
    val password: String,
    val payload: String,
)

object AdbQrPayloadBuilder {
    private const val SERVICE_PREFIX = "studio-"
    private const val PASSWORD_LENGTH = 10
    const val PASSWORD_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?"

    fun generate(
        randomIndex: () -> Int = { Random.nextInt(PASSWORD_CHARS.length) },
    ): AdbQrCredentials {
        val suffix = randomString(randomIndex)
        val serviceName = SERVICE_PREFIX + suffix
        val password = randomString(randomIndex)
        val payload = "WIFI:T:ADB;S:$serviceName;P:$password;;"
        return AdbQrCredentials(serviceName, password, payload)
    }

    private fun randomString(randomIndex: () -> Int): String = buildString(PASSWORD_LENGTH) {
        repeat(PASSWORD_LENGTH) {
            append(PASSWORD_CHARS[randomIndex()])
        }
    }
}
