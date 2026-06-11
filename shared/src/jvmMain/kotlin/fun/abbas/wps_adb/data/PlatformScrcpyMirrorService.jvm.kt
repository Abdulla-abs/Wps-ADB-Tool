package `fun`.abbas.wps_adb.data

actual fun createScrcpyMirrorService(
    scrcpyPathProvider: () -> String,
    adbPathProvider: () -> String,
): ScrcpyMirrorService {
    return if (JvmAdbRunner.isAvailable()) {
        JvmScrcpyMirrorService(scrcpyPathProvider, adbPathProvider)
    } else {
        NoOpScrcpyMirrorService()
    }
}
