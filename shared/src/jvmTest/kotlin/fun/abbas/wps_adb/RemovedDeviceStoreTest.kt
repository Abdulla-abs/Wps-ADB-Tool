package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.RemovedDeviceStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class RemovedDeviceStoreTest {
    @Test
    fun add_persistsRemovedSerial() {
        val file = File.createTempFile("wps-adb-removed", ".txt")
        file.deleteOnExit()
        val store = RemovedDeviceStore(file)

        store.add("192.168.0.2:5555")

        assertTrue(RemovedDeviceStore(file).contains("192.168.0.2:5555"))
    }

    @Test
    fun remove_clearsRemovedSerial() {
        val file = File.createTempFile("wps-adb-removed", ".txt")
        file.deleteOnExit()
        val store = RemovedDeviceStore(file)

        store.add("192.168.0.2:5555")
        store.remove("192.168.0.2:5555")

        assertFalse(store.contains("192.168.0.2:5555"))
    }

    @Test
    fun load_returnsEmptyForMissingFile() {
        val file = File.createTempFile("wps-adb-removed-missing", ".txt")
        file.delete()

        assertTrue(RemovedDeviceStore(file).load().isEmpty())
    }
}
