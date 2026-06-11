package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.AdbQrPayloadBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdbQrPayloadBuilderTest {

    @Test
    fun buildPayload_usesWpa3AdbFormat() {
        var index = 0
        val creds = AdbQrPayloadBuilder.generate { index++ % AdbQrPayloadBuilder.PASSWORD_CHARS.length }
        assertTrue(creds.serviceName.startsWith("studio-"))
        assertEquals(
            "WIFI:T:ADB;S:${creds.serviceName};P:${creds.password};;",
            creds.payload,
        )
    }

    @Test
    fun buildPayload_passwordLengthIsTen() {
        val creds = AdbQrPayloadBuilder.generate()
        assertEquals(10, creds.password.length)
    }

    @Test
    fun buildPayload_serviceSuffixLengthIsTen() {
        val creds = AdbQrPayloadBuilder.generate()
        assertEquals(10, creds.serviceName.removePrefix("studio-").length)
    }
}
