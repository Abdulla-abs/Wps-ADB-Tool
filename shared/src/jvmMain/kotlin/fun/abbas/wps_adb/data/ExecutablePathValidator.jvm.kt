package `fun`.abbas.wps_adb.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun validateAdbExecutablePath(configuredPath: String): Boolean = withContext(Dispatchers.IO) {
    if (!ExecutableLocator.isAdbConfigured(configuredPath)) return@withContext false
    val resolved = ExecutableLocator.resolveAdbPath(configuredPath)
    resolved.isNotBlank() && JvmAdbRunner.isAvailable(resolved)
}

actual suspend fun validateScrcpyExecutablePath(configuredPath: String): Boolean = withContext(Dispatchers.IO) {
    if (!ExecutableLocator.isScrcpyConfigured(configuredPath)) return@withContext false
    val resolved = ExecutableLocator.resolveScrcpyPath(configuredPath)
    resolved.isNotBlank() && JvmScrcpyMirrorService.checkVersion(resolved)
}
