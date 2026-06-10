package `fun`.abbas.wps_adb.data

actual fun createAdbRepository(): AdbRepository {
    return if (JvmAdbRunner.isAvailable()) {
        JvmAdbRepository()
    } else {
        MockAdbRepository()
    }
}
