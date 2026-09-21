package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ParsedAdbDevice
import `fun`.abbas.wps_adb.data.WirelessDeviceStatusReconciler
import `fun`.abbas.wps_adb.model.DeviceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WirelessDeviceStatusReconcilerTest {
    @Test
    fun reconcileParsedStatus_promotesReachableWirelessOfflineDevice() {
        val parsed = ParsedAdbDevice(
            serial = "192.168.5.134:5555",
            status = DeviceStatus.OFFLINE,
            product = "oriole",
            model = "Pixel_6",
        )

        val reconciled = WirelessDeviceStatusReconciler.reconcileParsedStatus(
            parsed = parsed,
            isUserDisconnected = false,
            isReachable = { true },
        )

        assertEquals(DeviceStatus.ONLINE, reconciled.status)
    }

    @Test
    fun reconcileParsedStatus_keepsUserDisconnectedOfflineDevice() {
        val parsed = ParsedAdbDevice(
            serial = "192.168.5.134:5555",
            status = DeviceStatus.OFFLINE,
            product = null,
            model = null,
        )

        val reconciled = WirelessDeviceStatusReconciler.reconcileParsedStatus(
            parsed = parsed,
            isUserDisconnected = true,
            isReachable = { true },
        )

        assertEquals(DeviceStatus.OFFLINE, reconciled.status)
    }

    @Test
    fun reconcileParsedStatus_keepsUnreachableWirelessOfflineDevice() {
        val parsed = ParsedAdbDevice(
            serial = "192.168.5.134:5555",
            status = DeviceStatus.OFFLINE,
            product = null,
            model = null,
        )

        val reconciled = WirelessDeviceStatusReconciler.reconcileParsedStatus(
            parsed = parsed,
            isUserDisconnected = false,
            isReachable = { false },
        )

        assertEquals(DeviceStatus.OFFLINE, reconciled.status)
    }

    @Test
    fun resolveSavedPlaceholderStatus_usesReachabilityProbe() {
        assertEquals(
            DeviceStatus.ONLINE,
            WirelessDeviceStatusReconciler.resolveSavedPlaceholderStatus(
                endpoint = "192.168.5.134:5555",
                isUserDisconnected = false,
                isReachable = { true },
            ),
        )
        assertEquals(
            DeviceStatus.OFFLINE,
            WirelessDeviceStatusReconciler.resolveSavedPlaceholderStatus(
                endpoint = "192.168.5.134:5555",
                isUserDisconnected = false,
                isReachable = { false },
            ),
        )
    }

    @Test
    fun isWirelessTransport_detectsIpPortAndTlsSerials() {
        assertTrue(WirelessDeviceStatusReconciler.isWirelessTransport("192.168.5.134:5555"))
        assertTrue(
            WirelessDeviceStatusReconciler.isWirelessTransport("adb-b949c7c6-zEwUSM._adb-tls-connect._tcp"),
        )
        assertFalse(WirelessDeviceStatusReconciler.isWirelessTransport("emulator-5554"))
        assertFalse(WirelessDeviceStatusReconciler.isWirelessTransport("ABC123"))
    }
}
