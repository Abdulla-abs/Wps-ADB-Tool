package `fun`.abbas.wps_adb.data

actual fun createDeviceShellService(adbPathProvider: () -> String): DeviceShellService {
    return if (JvmAdbRunner.isAvailable()) {
        JvmDeviceShellService(adbPathProvider)
    } else {
        NoOpDeviceShellService()
    }
}
