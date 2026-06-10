package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.model.DeviceScreenMetrics
import `fun`.abbas.wps_adb.model.ScreenFormFactor
import `fun`.abbas.wps_adb.model.displayAspectRatio
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceScreenMetricsTest {
    @Test
    fun parseFormFactor_detectsTvTabletAndPhone() {
        assertEquals(ScreenFormFactor.TV, DeviceScreenMetrics.parseFormFactor("tv"))
        assertEquals(ScreenFormFactor.TABLET, DeviceScreenMetrics.parseFormFactor("default,tablet"))
        assertEquals(ScreenFormFactor.PHONE, DeviceScreenMetrics.parseFormFactor("default,phone"))
    }

    @Test
    fun parseWindowSize_readsPhysicalSize() {
        val size = DeviceScreenMetrics.parseWindowSize(
            """
            Physical size: 1920x1080
            Override size: 960x540
            """.trimIndent(),
        )

        assertEquals(1920 to 1080, size)
    }

    @Test
    fun inferFormFactor_usesScreenSizeWhenCharacteristicsUnknown() {
        assertEquals(
            ScreenFormFactor.TV,
            DeviceScreenMetrics.inferFormFactor("", 1920, 1080),
        )
        assertEquals(
            ScreenFormFactor.TABLET,
            DeviceScreenMetrics.inferFormFactor("", 1600, 2560),
        )
        assertEquals(
            ScreenFormFactor.PHONE,
            DeviceScreenMetrics.inferFormFactor("", 1080, 2400),
        )
    }

    @Test
    fun displayAspectRatio_usesActualResolutionWhenAvailable() {
        val phone = sampleDevice(formFactor = ScreenFormFactor.PHONE, width = 1080, height = 2400)
        val tv = sampleDevice(formFactor = ScreenFormFactor.TV, width = 1920, height = 1080)

        assertEquals(1080f / 2400f, phone.displayAspectRatio())
        assertEquals(1920f / 1080f, tv.displayAspectRatio())
        assertTrue(tv.displayAspectRatio() > 1f)
    }

    private fun sampleDevice(
        formFactor: ScreenFormFactor,
        width: Int,
        height: Int,
    ) = Device(
        id = "sample",
        name = "Sample",
        serial = "sample",
        type = DeviceType.PHYSICAL,
        connectionType = ConnectionType.USB,
        status = DeviceStatus.ONLINE,
        androidVersion = "Android 14",
        batteryLevel = 100,
        isCharging = false,
        storageUsed = "--",
        storageTotal = "--",
        storagePercent = 0,
        screenshotUrl = "",
        screenDescription = "Sample",
        formFactor = formFactor,
        screenWidthPx = width,
        screenHeightPx = height,
    )
}
