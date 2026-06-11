package `fun`.abbas.wps_adb.data

object ApkPackageNames {
    private val packageRegex = Regex("""^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$""")

    fun isValid(name: String): Boolean = packageRegex.matches(name)

    fun isLikelyAppPackage(name: String): Boolean {
        if (!isValid(name)) return false
        if (name.startsWith("android.") || name.startsWith("androidx.")) return false
        if (name.startsWith("com.android.") || name.startsWith("com.google.android.")) return false
        return true
    }

    fun pickBestPackage(candidates: Iterable<String>): String? =
        candidates
            .map { it.lowercase() }
            .filter { isLikelyAppPackage(it) }
            .distinct()
            .maxWithOrNull(
                compareBy<String> { it.count { ch -> ch == '.' } }
                    .thenBy { it.length },
            )
}
