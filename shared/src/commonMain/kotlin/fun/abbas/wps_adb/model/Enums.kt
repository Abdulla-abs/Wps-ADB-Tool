package `fun`.abbas.wps_adb.model

enum class ConnectionType { WIFI, USB, EMULATOR }

enum class DeviceType { PHYSICAL, EMULATOR }

enum class ScreenFormFactor { PHONE, TABLET, TV, UNKNOWN }

enum class DeviceStatus { ONLINE, OFFLINE, UNAUTHORIZED }

enum class LogLevel { V, D, I, W, E }

enum class FilterTab { ALL, PHYSICAL, EMULATORS }

enum class SortParam { NAME, SERIAL, BATTERY }

enum class NavTab { WALL, GROUPS, SETTINGS }

enum class DeviceAction { DEBUG, DISCONNECT }
