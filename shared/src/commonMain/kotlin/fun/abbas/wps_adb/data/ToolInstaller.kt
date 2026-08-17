package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ToolKind

data class ToolInstallResult(
    val executablePath: String,
)

interface ToolInstaller {
    suspend fun install(
        kind: ToolKind,
        installDir: String,
        onProgress: (phaseLabel: String, fraction: Float) -> Unit,
    ): Result<ToolInstallResult>
}

expect fun createToolInstaller(): ToolInstaller

expect fun defaultToolInstallRootPath(): String
