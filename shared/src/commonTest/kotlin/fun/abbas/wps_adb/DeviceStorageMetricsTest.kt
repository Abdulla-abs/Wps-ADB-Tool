package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.model.DeviceStorageMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DeviceStorageMetricsTest {
    @Test
    fun parseDfOutput_standardDataMount() {
        val output = """
            Filesystem      1K-blocks    Used Available Use% Mounted on
            /dev/block/data  55128444 7661500  47417792  14% /data
        """.trimIndent()

        val snapshot = DeviceStorageMetrics.parseDfOutput(output)
        assertNotNull(snapshot)
        assertEquals(14, snapshot.percent)
        assertEquals("52.6GB", snapshot.total)
        assertEquals("7.3GB", snapshot.used)
    }

    @Test
    fun parseDfOutput_obfuscatedMountFromDfDataQuery() {
        val output = """
            Filesystem            1K-blocks    Used Available Use% Mounted on
            /dev/block/mmcblk0p37  18299068 9232528   9066540  51% /storage/emulated/0/Android/obb
        """.trimIndent()

        val snapshot = DeviceStorageMetrics.parseDfOutput(output)
        assertNotNull(snapshot)
        assertEquals(51, snapshot.percent)
        assertEquals("17.5GB", snapshot.total)
        assertEquals("8.8GB", snapshot.used)
    }

    @Test
    fun parseDfOutput_humanReadableFormat() {
        val output = """
            Filesystem            Size  Used Avail Use% Mounted on
            /dev/block/mmcblk0p37  17G  8.8G  8.6G  51% /storage/emulated/0/Android/obb
        """.trimIndent()

        val snapshot = DeviceStorageMetrics.parseDfOutput(output)
        assertNotNull(snapshot)
        assertEquals(51, snapshot.percent)
        assertEquals("17GB", snapshot.total)
        assertEquals("8.8GB", snapshot.used)
    }

    @Test
    fun parseDfOutputPreferringDataMount_picksRootDataPartition() {
        val output = """
            Filesystem            1K-blocks    Used Available Use% Mounted on
            /dev/block/mmcblk0p37  18299068 9232360   9066708  51% /data
            /dev/fuse              18299068 9232360   9066708  51% /storage/emulated
        """.trimIndent()

        val snapshot = DeviceStorageMetrics.parseDfOutputPreferringDataMount(output)
        assertNotNull(snapshot)
        assertEquals(51, snapshot.percent)
        assertEquals("17.5GB", snapshot.total)
    }
}
