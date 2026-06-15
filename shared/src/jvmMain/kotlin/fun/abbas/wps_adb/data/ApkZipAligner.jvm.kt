package `fun`.abbas.wps_adb.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object ApkZipAligner {
    fun align(input: File, output: File, adbPath: String) {
        val zipalign = AndroidSdkToolLocator.resolveBuildToolsBinary(adbPath, "zipalign")
        if (zipalign != null) {
            alignWithTool(zipalign, input, output)
            return
        }
        realignInProcess(input, output)
    }

    private fun alignWithTool(zipalign: String, input: File, output: File) {
        val tempOutput = if (output.absolutePath == input.absolutePath) {
            File(output.parentFile, "${output.nameWithoutExtension}.aligned.apk")
        } else {
            output
        }
        val result = ProcessBuilder(
            zipalign,
            "-p",
            "-f",
            "4",
            input.absolutePath,
            tempOutput.absolutePath,
        )
            .redirectErrorStream(true)
            .start()
            .waitFor()
        if (result != 0 || !tempOutput.isFile) {
            error("zipalign failed with exit code $result")
        }
        if (tempOutput != output) {
            tempOutput.copyTo(output, overwrite = true)
            tempOutput.delete()
        }
    }

    private fun realignInProcess(input: File, output: File) {
        val entries = ZipFile(input).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { entry ->
                    val data = zip.getInputStream(entry).use { it.readBytes() }
                    ApkEntry(entry.name, data)
                }
                .toList()
        }
        ApkPacker.repack(entries, output)
    }
}
