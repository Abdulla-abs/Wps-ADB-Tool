package `fun`.abbas.wps_adb.data

actual fun createDeviceShellService(adbPathProvider: () -> String): DeviceShellService =
    NoOpDeviceShellService()
