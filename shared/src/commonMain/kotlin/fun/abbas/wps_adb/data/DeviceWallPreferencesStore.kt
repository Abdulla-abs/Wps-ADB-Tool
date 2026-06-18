package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DeviceWallPreferences

expect object DeviceWallPreferencesStore {
    fun load(): DeviceWallPreferences
    fun save(preferences: DeviceWallPreferences)
}
