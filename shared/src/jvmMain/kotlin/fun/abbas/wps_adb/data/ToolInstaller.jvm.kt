package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.ToolKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

actual fun createToolInstaller(): ToolInstaller = JvmToolInstaller()

actual fun defaultToolInstallRootPath(): String =
    JvmToolInstaller.defaultInstallRoot().absolutePath

class JvmToolInstaller : ToolInstaller {
    override suspend fun install(
        kind: ToolKind,
        installDir: String,
        onProgress: (phaseLabel: String, fraction: Float) -> Unit,
    ): Result<ToolInstallResult> = withContext(Dispatchers.IO) {
        try {
            val targetRoot = File(installDir.trim()).canonicalFile
            if (!targetRoot.exists() && !targetRoot.mkdirs()) {
                return@withContext Result.failure(IllegalStateException("Cannot create install directory: ${targetRoot.absolutePath}"))
            }
            if (!targetRoot.isDirectory) {
                return@withContext Result.failure(IllegalStateException("Install path is not a directory: ${targetRoot.absolutePath}"))
            }

            val artifact = resolveArtifact(kind)
            val archiveFile = File(targetRoot, artifact.fileName)
            onProgress("Downloading ${artifact.fileName}…", 0.02f)
            downloadFile(artifact.url, archiveFile) { downloaded, total ->
                val downloadFraction = if (total > 0L) {
                    (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                } else {
                    0.3f
                }
                onProgress("Downloading…", 0.05f + downloadFraction * 0.7f)
            }

            onProgress("Extracting…", 0.78f)
            extractArchive(archiveFile, targetRoot)
            archiveFile.delete()

            onProgress("Configuring…", 0.92f)
            val executable = findExecutable(kind, targetRoot)
                ?: return@withContext Result.failure(
                    IllegalStateException("Installed files but could not find ${kind.name.lowercase()} executable under ${targetRoot.absolutePath}"),
                )
            if (!executable.canExecute()) {
                executable.setExecutable(true)
            }
            onProgress("Done", 1f)
            Result.success(ToolInstallResult(executable.absolutePath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class Artifact(val url: String, val fileName: String)

    private fun resolveArtifact(kind: ToolKind): Artifact = when (kind) {
        ToolKind.ADB -> {
            val osKey = when {
                isWindows() -> "windows"
                isMac() -> "darwin"
                else -> "linux"
            }
            val fileName = "platform-tools-latest-$osKey.zip"
            Artifact(
                url = "https://dl.google.com/android/repository/$fileName",
                fileName = fileName,
            )
        }
        ToolKind.SCRCPY -> {
            val fileName = scrcpyArchiveName()
            Artifact(
                url = "https://github.com/Genymobile/scrcpy/releases/download/v$SCRCPY_VERSION/$fileName",
                fileName = fileName,
            )
        }
    }

    private fun scrcpyArchiveName(): String = when {
        isWindows() -> "scrcpy-win64-v$SCRCPY_VERSION.zip"
        isMac() && isAarch64() -> "scrcpy-macos-aarch64-v$SCRCPY_VERSION.tar.gz"
        isMac() -> "scrcpy-macos-x86_64-v$SCRCPY_VERSION.tar.gz"
        else -> "scrcpy-linux-x86_64-v$SCRCPY_VERSION.tar.gz"
    }

    private fun downloadFile(
        url: String,
        dest: File,
        onBytes: (downloaded: Long, total: Long) -> Unit,
    ) {
        dest.parentFile?.mkdirs()
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WpsAdbTool")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                error("Download failed HTTP $code for $url")
            }
            val total = connection.contentLengthLong.coerceAtLeast(-1L)
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onBytes(downloaded, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractArchive(archive: File, destDir: File) {
        when {
            archive.name.endsWith(".zip", ignoreCase = true) -> unzip(archive, destDir)
            archive.name.endsWith(".tar.gz", ignoreCase = true) ||
                archive.name.endsWith(".tgz", ignoreCase = true) -> extractTarGz(archive, destDir)
            else -> error("Unsupported archive: ${archive.name}")
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(destDir, entry.name).canonicalFile
                if (!outFile.path.startsWith(destDir.canonicalPath + File.separator) &&
                    outFile.path != destDir.canonicalPath
                ) {
                    error("Zip slip blocked: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
    }

    private fun extractTarGz(archive: File, destDir: File) {
        val result = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
            .redirectErrorStream(true)
            .start()
            .waitFor()
        if (result != 0) {
            error("tar extract failed with exit code $result")
        }
    }

    private fun findExecutable(kind: ToolKind, root: File): File? {
        val names = when (kind) {
            ToolKind.ADB -> if (isWindows()) listOf("adb.exe") else listOf("adb")
            ToolKind.SCRCPY -> if (isWindows()) listOf("scrcpy.exe") else listOf("scrcpy")
        }
        return root.walkTopDown()
            .maxDepth(6)
            .firstOrNull { file -> file.isFile && names.any { it.equals(file.name, ignoreCase = true) } }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)

    private fun isMac(): Boolean =
        System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)

    private fun isAarch64(): Boolean {
        val arch = System.getProperty("os.arch").orEmpty().lowercase()
        return arch.contains("aarch64") || arch == "arm64"
    }

    companion object {
        const val SCRCPY_VERSION = "3.3.4"

        fun defaultInstallRoot(): File =
            File(System.getProperty("user.home"), ".wps-adb-tool/tools")
    }
}
