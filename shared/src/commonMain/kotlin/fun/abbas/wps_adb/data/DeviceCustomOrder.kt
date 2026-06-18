package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.DeviceType
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.SortParam

object DeviceCustomOrder {
    fun filterDevices(
        devices: List<Device>,
        filterTab: FilterTab,
        searchQuery: String,
    ): List<Device> {
        return devices
            .filter { device ->
                when (filterTab) {
                    FilterTab.PHYSICAL -> device.type == DeviceType.PHYSICAL
                    FilterTab.EMULATORS -> device.type == DeviceType.EMULATOR
                    FilterTab.ALL -> true
                }
            }
            .filter { device ->
                val query = searchQuery.lowercase()
                device.name.lowercase().contains(query) ||
                    device.serial.lowercase().contains(query) ||
                    device.androidVersion.lowercase().contains(query)
            }
    }

    fun sortDevices(
        devices: List<Device>,
        sortParam: SortParam,
        customOrder: List<String>,
    ): List<Device> {
        return when (sortParam) {
            SortParam.SERIAL -> devices.sortedBy { it.serial }
            SortParam.BATTERY -> devices.sortedByDescending { it.batteryLevel }
            SortParam.NAME -> devices.sortedBy { it.name }
            SortParam.CUSTOM -> sortByCustomOrder(devices, customOrder)
        }
    }

    fun sortedSerials(devices: List<Device>, sortParam: SortParam): List<String> =
        sortDevices(devices, sortParam, emptyList()).map { it.serial }

    fun mergeNewDevices(customOrder: List<String>, currentSerials: List<String>): List<String> {
        if (currentSerials.isEmpty()) return customOrder
        val known = customOrder.toSet()
        val appended = currentSerials.filter { it !in known }
        return customOrder + appended
    }

    fun removeSerial(customOrder: List<String>, serial: String): List<String> =
        customOrder.filterNot { it == serial }

    fun reorderInFiltered(
        fullOrder: List<String>,
        filteredSerialsInDisplayOrder: List<String>,
        fromIndex: Int,
        toIndex: Int,
    ): List<String> {
        if (fromIndex == toIndex) return fullOrder
        if (fromIndex !in filteredSerialsInDisplayOrder.indices || toIndex !in filteredSerialsInDisplayOrder.indices) {
            return fullOrder
        }
        val reorderedFiltered = filteredSerialsInDisplayOrder.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        val filteredSet = filteredSerialsInDisplayOrder.toSet()
        val result = fullOrder.toMutableList()
        var filteredIndex = 0
        for (index in result.indices) {
            if (result[index] in filteredSet) {
                result[index] = reorderedFiltered[filteredIndex++]
            }
        }
        return result
    }

    fun reorderAfterDrag(
        allDevices: List<Device>,
        filteredSerialsInDisplayOrder: List<String>,
        customOrder: List<String>,
        sortParam: SortParam,
        fromIndex: Int,
        toIndex: Int,
    ): List<String> {
        val allSerials = allDevices.map { it.serial }
        val baseOrder = when (sortParam) {
            SortParam.CUSTOM -> mergeNewDevices(customOrder, allSerials)
            else -> sortedSerials(allDevices, sortParam)
        }
        return reorderInFiltered(
            fullOrder = baseOrder,
            filteredSerialsInDisplayOrder = filteredSerialsInDisplayOrder,
            fromIndex = fromIndex,
            toIndex = toIndex,
        )
    }

    private fun sortByCustomOrder(devices: List<Device>, customOrder: List<String>): List<Device> {
        val orderIndex = customOrder.withIndex().associate { (index, serial) -> serial to index }
        return devices.sortedWith(
            compareBy<Device>({ orderIndex[it.serial] ?: Int.MAX_VALUE })
                .thenBy { it.serial },
        )
    }
}
