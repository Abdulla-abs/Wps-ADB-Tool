package `fun`.abbas.wps_adb.data

object ApkInstallConflictDetector {
    fun isExistingPackageConflict(message: String): Boolean {
        val normalized = message.uppercase()
        return CONFLICT_MARKERS.any { normalized.contains(it) }
    }

    private val CONFLICT_MARKERS = listOf(
        "INSTALL_FAILED_ALREADY_EXISTS",
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
        "INSTALL_FAILED_VERSION_DOWNGRADE",
        "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE",
        "SIGNATURES DO NOT MATCH",
        "SIGNATURE MISMATCH",
        "WITHOUT FIRST UNINSTALLING",
    )
}
