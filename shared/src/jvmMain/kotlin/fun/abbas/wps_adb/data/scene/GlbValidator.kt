package `fun`.abbas.wps_adb.data.scene

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GlbValidator {
    private const val GLTF_MAGIC = 0x46546C67 // "glTF" in little-endian
    private const val SUPPORTED_GLTF_VERSION = 2

    fun validate(file: File) {
        if (!file.exists() || !file.isFile) {
            throw InvalidGlbException("GLB file does not exist or is not a regular file: ${file.absolutePath}")
        }
        if (!file.name.endsWith(".glb", ignoreCase = true)) {
            throw InvalidGlbException("File is not a .glb file: ${file.name}")
        }
        val fileLength = file.length()
        if (fileLength < 12L) {
            throw InvalidGlbException("GLB file too small to contain a valid header: $fileLength bytes")
        }

        val header = ByteArray(12)
        try {
            FileInputStream(file).use { stream ->
                var totalRead = 0
                while (totalRead < 12) {
                    val read = stream.read(header, totalRead, 12 - totalRead)
                    if (read < 0) break
                    totalRead += read
                }
                if (totalRead < 12) {
                    throw InvalidGlbException("Failed to read GLB header: incomplete stream")
                }
            }
        } catch (e: Exception) {
            if (e is InvalidGlbException) throw e
            throw InvalidGlbException("Failed to read GLB file: ${e.message}")
        }

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        if (magic != GLTF_MAGIC) {
            throw InvalidGlbException("Invalid GLB magic bytes: expected 'glTF' (0x46546C67), found 0x${Integer.toHexString(magic)}")
        }

        val version = buffer.int
        if (version != SUPPORTED_GLTF_VERSION) {
            throw InvalidGlbException("Unsupported glTF version: $version (expected $SUPPORTED_GLTF_VERSION)")
        }

        val declaredLength = buffer.int.toLong() and 0xFFFFFFFFL
        if (declaredLength > fileLength) {
            throw InvalidGlbException("Corrupt GLB file: declared length $declaredLength bytes exceeds actual file size $fileLength bytes")
        }
    }
}
