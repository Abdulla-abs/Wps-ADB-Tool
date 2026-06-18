package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.DeviceCustomOrder
import `fun`.abbas.wps_adb.model.ConnectionType
import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceStatus
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.SortParam
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceCustomOrderTest {
    private fun device(serial: String, name: String = serial) = Device(
        id = serial,
        serial = serial,
        name = name,
        status = DeviceStatus.ONLINE,
        type = DeviceType.PHYSICAL,
        connectionType = ConnectionType.USB,
        androidVersion = "14",
        batteryLevel = 80,
        isCharging = false,
        storageUsed = "1GB",
        storageTotal = "64GB",
        storagePercent = 10,
        screenshotUrl = "",
        screenDescription = "",
    )

    @Test
    fun mergeNewDevices_keepsKnownOrderAndAppendsNewSerials() {
        val merged = DeviceCustomOrder.mergeNewDevices(
            customOrder = listOf("b", "a"),
            currentSerials = listOf("a", "b", "c"),
        )
        assertEquals(listOf("b", "a", "c"), merged)
    }

    @Test
    fun mergeNewDevices_keepsSavedSerialsWhenTemporarilyAbsent() {
        val merged = DeviceCustomOrder.mergeNewDevices(
            customOrder = listOf("a", "b", "c"),
            currentSerials = listOf("a", "c"),
        )
        assertEquals(listOf("a", "b", "c"), merged)
    }

    @Test
    fun mergeNewDevices_preservesOrderWhenCurrentSerialsEmpty() {
        val merged = DeviceCustomOrder.mergeNewDevices(
            customOrder = listOf("a", "b", "c"),
            currentSerials = emptyList(),
        )
        assertEquals(listOf("a", "b", "c"), merged)
    }

    @Test
    fun removeSerial_removesOnlyMatchingEntry() {
        val updated = DeviceCustomOrder.removeSerial(
            customOrder = listOf("a", "b", "c"),
            serial = "b",
        )
        assertEquals(listOf("a", "c"), updated)
    }

    @Test
    fun reorderInFiltered_updatesOnlyVisibleSubset() {
        val reordered = DeviceCustomOrder.reorderInFiltered(
            fullOrder = listOf("a", "b", "c", "d", "e"),
            filteredSerialsInDisplayOrder = listOf("a", "c", "e"),
            fromIndex = 2,
            toIndex = 0,
        )
        assertEquals(listOf("e", "b", "a", "d", "c"), reordered)
    }

    @Test
    fun reorderAfterDrag_fromNameSort_establishesCustomOrder() {
        val devices = listOf(
            device("phone-b", "Beta"),
            device("phone-a", "Alpha"),
            device("phone-c", "Charlie"),
        )
        val reordered = DeviceCustomOrder.reorderAfterDrag(
            allDevices = devices,
            filteredSerialsInDisplayOrder = listOf("phone-a", "phone-b", "phone-c"),
            customOrder = emptyList(),
            sortParam = SortParam.NAME,
            fromIndex = 2,
            toIndex = 0,
        )
        assertEquals(listOf("phone-c", "phone-a", "phone-b"), reordered)
    }

    @Test
    fun sortDevices_customOrder_sortsBySavedSerials() {
        val devices = listOf(
            device("c"),
            device("a"),
            device("b"),
        )
        val sorted = DeviceCustomOrder.sortDevices(
            devices = devices,
            sortParam = SortParam.CUSTOM,
            customOrder = listOf("b", "a", "c"),
        )
        assertEquals(listOf("b", "a", "c"), sorted.map { it.serial })
    }
}
