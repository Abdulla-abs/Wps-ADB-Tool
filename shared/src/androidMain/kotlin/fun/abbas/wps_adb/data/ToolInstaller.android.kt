package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ToolKind

actual fun createToolInstaller(): ToolInstaller = object : ToolInstaller {
    override suspend fun install(
        kind: ToolKind,
        installDir: String,
        onProgress: (phaseLabel: String, fraction: Float) -> Unit,
    ): Result<ToolInstallResult> =
        Result.failure(UnsupportedOperationException("In-app tool download is only available on desktop"))
}

actual fun defaultToolInstallRootPath(): String = ""
