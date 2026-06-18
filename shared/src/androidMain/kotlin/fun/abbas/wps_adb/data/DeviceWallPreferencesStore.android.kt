package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DeviceWallPreferences

actual object DeviceWallPreferencesStore {
    actual fun load(): DeviceWallPreferences = DeviceWallPreferences()

    actual fun save(preferences: DeviceWallPreferences) = Unit
}
