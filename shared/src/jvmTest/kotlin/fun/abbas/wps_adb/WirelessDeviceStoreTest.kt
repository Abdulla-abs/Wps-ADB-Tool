package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.SavedWirelessDevice
import `fun`.abbas.wps_adb.data.WirelessDeviceStore
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.ScreenFormFactor
import `fun`.abbas.wps_adb.model.displayAspectRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class WirelessDeviceStoreTest {
    @Test
    fun addOrUpdate_persistsFormFactorAndScreenSize() {
        val file = File.createTempFile("wps-adb-wireless", ".txt")
        file.deleteOnExit()
        val store = WirelessDeviceStore(file)

        store.addOrUpdate(
            SavedWirelessDevice(
                host = "192.168.0.88",
                port = 5555,
                name = "Living Room TV",
                formFactor = ScreenFormFactor.TV,
                screenWidthPx = 1920,
                screenHeightPx = 1080,
            ),
        )

        val loaded = store.load().single()
        assertEquals(ScreenFormFactor.TV, loaded.formFactor)
        assertEquals(1920, loaded.screenWidthPx)
        assertEquals(1080, loaded.screenHeightPx)
        assertEquals(1920f / 1080f, loaded.toOfflineDevice().displayAspectRatio())
    }

    @Test
    fun addOrUpdate_persistsAcrossLoads() {
        val file = File.createTempFile("wps-adb-wireless", ".txt")
        file.deleteOnExit()
        val store = WirelessDeviceStore(file)

        store.addOrUpdate(SavedWirelessDevice("192.168.0.2", 5555, "Test Phone"))
        val loaded = WirelessDeviceStore(file).load()

        assertEquals(1, loaded.size)
        assertEquals("192.168.0.2:5555", loaded.first().endpoint)
        assertEquals("Test Phone", loaded.first().name)
    }

    @Test
    fun addOrUpdate_replacesExistingEndpoint() {
        val file = File.createTempFile("wps-adb-wireless", ".txt")
        file.deleteOnExit()
        val store = WirelessDeviceStore(file)

        store.addOrUpdate(SavedWirelessDevice("192.168.0.2", 5555, "Old Name"))
        store.addOrUpdate(SavedWirelessDevice("192.168.0.2", 5555, "New Name"))

        assertEquals("New Name", store.load().single().name)
    }

    @Test
    fun toOfflineDevice_usesSavedDisplayName() {
        val device = SavedWirelessDevice("192.168.0.2", 5555, "Bedroom Pixel").toOfflineDevice()

        assertEquals("192.168.0.2:5555", device.serial)
        assertEquals("Bedroom Pixel", device.name)
        assertEquals(DeviceStatus.OFFLINE, device.status)
        assertEquals(ConnectionType.WIFI, device.connectionType)
    }

    @Test
    fun load_returnsEmptyForMissingFile() {
        val file = File.createTempFile("wps-adb-wireless-missing", ".txt")
        file.delete()

        assertTrue(WirelessDeviceStore(file).load().isEmpty())
    }

    @Test
    fun remove_deletesPersistedEndpoint() {
        val file = File.createTempFile("wps-adb-wireless", ".txt")
        file.deleteOnExit()
        val store = WirelessDeviceStore(file)

        store.addOrUpdate(SavedWirelessDevice("192.168.0.2", 5555, "Test Phone"))
        store.addOrUpdate(SavedWirelessDevice("192.168.0.3", 5555, "Other Phone"))
        store.remove("192.168.0.2:5555")

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("192.168.0.3:5555", loaded.single().endpoint)
    }
}
