package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.DeviceWallPreferencesStore
import `fun`.abbas.wps_adb.model.DeviceWallPreferences
import `fun`.abbas.wps_adb.model.SortParam
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceWallPreferencesStoreTest {
    @Test
    fun saveAndLoad_roundTripsCustomOrderAndSortParam() {
        val dir = Files.createTempDirectory("device-wall-prefs").toFile()
        val file = File(dir, "settings.properties")
        val originalHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", dir.absolutePath)
            val prefs = DeviceWallPreferences(
                customOrder = listOf("192.168.1.2:5555", "adb-serial-1"),
                sortParam = SortParam.CUSTOM,
            )
            DeviceWallPreferencesStore.save(prefs)
            assertEquals(prefs, DeviceWallPreferencesStore.load())
        } finally {
            System.setProperty("user.home", originalHome)
            file.delete()
            dir.delete()
        }
    }
}
