package `fun`.abbas.wps_adb.model

data class DeviceWallPreferences(
    val customOrder: List<String> = emptyList(),
    val sortParam: SortParam = SortParam.NAME,
)
