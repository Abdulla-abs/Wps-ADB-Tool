package `fun`.abbas.wps_adb.viewmodel

import `fun`.abbas.wps_adb.model.Device
import `fun`.abbas.wps_adb.model.FilterTab
import `fun`.abbas.wps_adb.model.NavTab
import `fun`.abbas.wps_adb.model.SortParam

data class AppUiState(
    val activeTab: NavTab = NavTab.WALL,
    val filterTab: FilterTab = FilterTab.ALL,
    val searchQuery: String = "",
    val sortParam: SortParam = SortParam.NAME,
    val isLogTrayOpen: Boolean = true,
    val isPairingDialogOpen: Boolean = false,
    val mirroredDevice: Device? = null,
    val isAdbActive: Boolean = true,
    val isRestartingAdb: Boolean = false,
)
