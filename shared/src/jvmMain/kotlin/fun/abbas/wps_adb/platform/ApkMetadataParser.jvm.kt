package `fun`.abbas.wps_adb.platform

import `fun`.abbas.wps_adb.data.ApkBadgingParser
import `fun`.abbas.wps_adb.data.ApkBinaryManifestParser
import `fun`.abbas.wps_adb.data.ApkFileNameParser
import `fun`.abbas.wps_adb.data.AndroidSdkToolLocator
import `fun`.abbas.wps_adb.model.ApkMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class ApkMetadataParser {
    actual suspend fun parse(apkPath: String, adbPath: String): ApkMetadata? = withContext(Dispatchers.IO) {
        val apkFile = File(apkPath)
        if (!apkFile.isFile) return@withContext null

        parseWithAapt(apkFile, adbPath)
            ?: parseWithAapt2Badging(apkFile, adbPath)
            ?: parseWithAapt2PackageName(apkFile, adbPath)
            ?: parseWithApkanalyzer(apkFile, adbPath)
            ?: parseWithBinaryManifest(apkFile)
            ?: ApkFileNameParser.metadataFromFileName(apkFile.name)
    }

    private fun parseWithAapt(apkFile: File, adbPath: String): ApkMetadata? {
        val aaptPath = AndroidSdkToolLocator.resolveAapt(adbPath) ?: return null
        val output = runTool(aaptPath, listOf("dump", "badging", apkFile.absolutePath)) ?: return null
        return ApkBadgingParser.parseAaptOutput(output)
    }

    private fun parseWithAapt2Badging(apkFile: File, adbPath: String): ApkMetadata? {
        val aapt2Path = AndroidSdkToolLocator.resolveAapt2(adbPath) ?: return null
        val output = runTool(aapt2Path, listOf("dump", "badging", apkFile.absolutePath)) ?: return null
        return ApkBadgingParser.parseAaptOutput(output)
    }

    private fun parseWithAapt2PackageName(apkFile: File, adbPath: String): ApkMetadata? {
        val aapt2Path = AndroidSdkToolLocator.resolveAapt2(adbPath) ?: return null
        val output = runTool(aapt2Path, listOf("dump", "packagename", apkFile.absolutePath)) ?: return null
        return ApkBadgingParser.parsePackageNameLine(output)
    }

    private fun parseWithApkanalyzer(apkFile: File, adbPath: String): ApkMetadata? {
        val apkanalyzerPath = AndroidSdkToolLocator.resolveApkanalyzer(adbPath) ?: return null
        val output = runTool(
            apkanalyzerPath,
            listOf("manifest", "application-id", apkFile.absolutePath),
        ) ?: return null
        return ApkBadgingParser.parseApkanalyzerOutput(output)
    }

    private fun parseWithBinaryManifest(apkFile: File): ApkMetadata? {
        val packageName = ApkBinaryManifestParser.parsePackageName(apkFile) ?: return null
        return ApkMetadata(
            packageName = packageName,
            appLabel = apkFile.nameWithoutExtension,
        )
    }

    private fun runTool(executable: String, args: List<String>): String? {
        return try {
            val process = ProcessBuilder(listOf(executable) + args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (output.isBlank()) return null
            if (exitCode != 0 &&
                ApkBadgingParser.parseAaptOutput(output) == null &&
                output.lineSequence().none { line ->
                    ApkBadgingParser.parsePackageNameLine(line) != null
                }
            ) {
                return null
            }
            output
        } catch (_: Exception) {
            null
        }
    }
}
