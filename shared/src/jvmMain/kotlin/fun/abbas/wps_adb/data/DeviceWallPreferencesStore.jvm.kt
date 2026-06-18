package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DeviceWallPreferences
import `fun`.abbas.wps_adb.model.SortParam
import java.io.File
import java.util.Properties

actual object DeviceWallPreferencesStore {
    private const val KEY_CUSTOM_ORDER = "deviceCustomOrder"
    private const val KEY_SORT = "deviceWallSortParam"

    actual fun load(): DeviceWallPreferences {
        val file = storeFile()
        if (!file.exists()) return DeviceWallPreferences()
        val props = Properties().apply {
            file.inputStream().buffered().use(::load)
        }
        val customOrder = props.getProperty(KEY_CUSTOM_ORDER, "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val sortParam = runCatching {
            SortParam.valueOf(props.getProperty(KEY_SORT, SortParam.NAME.name))
        }.getOrDefault(SortParam.NAME)
        return DeviceWallPreferences(customOrder = customOrder, sortParam = sortParam)
    }

    actual fun save(preferences: DeviceWallPreferences) {
        val file = storeFile()
        val props = if (file.exists()) {
            Properties().apply { file.inputStream().buffered().use(::load) }
        } else {
            Properties()
        }
        props.setProperty(KEY_CUSTOM_ORDER, preferences.customOrder.joinToString(","))
        props.setProperty(KEY_SORT, preferences.sortParam.name)
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { props.store(it, "WpsAdbTool settings") }
    }

    private fun storeFile(): File = AppSettingsStore.defaultStoreFile()
}
