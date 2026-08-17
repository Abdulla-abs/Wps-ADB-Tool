package `fun`.abbas.wps_adb.data

/**
 * Always use the real desktop repository. [MockAdbRepository] is for tests / Android preview only.
 * Falling back to mock when `adb` is missing used to show fake ONLINE demo devices and fake pairing,
 * which looked like real connections on a machine with no ADB installed.
 *
 * [JvmAdbRepository] already degrades cleanly: empty wall + `isAdbActive=false` + settings hint.
 */
actual fun createAdbRepository(): AdbRepository = JvmAdbRepository()
