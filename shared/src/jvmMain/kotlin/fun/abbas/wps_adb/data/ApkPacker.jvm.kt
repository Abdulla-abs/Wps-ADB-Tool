package `fun`.abbas.wps_adb.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry

internal object ApkPacker {
    private const val LOCAL_HEADER_SIZE = 30
    private const val RESOURCES_ARSC = "resources.arsc"

    fun repack(entries: List<ApkEntry>, output: File) {
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { fos ->
            writeAlignedZip(fos, entries)
        }
    }

    fun compressionMethod(entryName: String): Int {
        if (entryName.equals("AndroidManifest.xml", ignoreCase = true)) return ZipEntry.STORED
        if (entryName.equals(RESOURCES_ARSC, ignoreCase = true)) return ZipEntry.STORED
        if (entryName.startsWith("lib/", ignoreCase = true) && entryName.endsWith(".so", ignoreCase = true)) {
            return ZipEntry.STORED
        }
        return ZipEntry.DEFLATED
    }

    fun alignmentFor(entryName: String): Int {
        if (entryName.equals(RESOURCES_ARSC, ignoreCase = true)) return 4
        if (entryName.startsWith("lib/", ignoreCase = true) && entryName.endsWith(".so", ignoreCase = true)) {
            return 4096
        }
        return 0
    }

    private fun writeAlignedZip(out: FileOutputStream, entries: List<ApkEntry>) {
        val centralDirectory = ByteArrayOutputStream()
        var offset = 0L

        for (entry in entries) {
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            val method = entry.method
            val alignment = entry.alignment
            val data = if (method == ZipEntry.STORED) entry.data else deflate(entry.data)

            var extra = ByteArray(0)
            if (alignment > 0) {
                val dataOffset = offset + LOCAL_HEADER_SIZE + nameBytes.size
                val padding = ((alignment - (dataOffset % alignment)) % alignment).toInt()
                if (padding > 0) {
                    extra = ByteArray(padding)
                }
            }

            val crc = CRC32().apply { update(entry.data) }.value
            val compressedSize = data.size.toLong()
            val uncompressedSize = entry.data.size.toLong()

            writeLocalHeader(out, nameBytes, method, crc, compressedSize, uncompressedSize, extra)
            if (extra.isNotEmpty()) out.write(extra)
            out.write(data)

            writeCentralDirectoryEntry(
                centralDirectory,
                nameBytes,
                method,
                crc,
                compressedSize,
                uncompressedSize,
                extra,
                offset,
            )

            offset += LOCAL_HEADER_SIZE + nameBytes.size + extra.size + data.size
        }

        val centralDirectoryBytes = centralDirectory.toByteArray()
        out.write(centralDirectoryBytes)
        writeEndOfCentralDirectory(out, entries.size, centralDirectoryBytes.size, offset)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArrayOutputStream(data.size)
        val chunk = ByteArray(4096)
        while (!deflater.finished()) {
            val count = deflater.deflate(chunk)
            buffer.write(chunk, 0, count)
        }
        deflater.end()
        return buffer.toByteArray()
    }

    private fun writeLocalHeader(
        out: FileOutputStream,
        nameBytes: ByteArray,
        method: Int,
        crc: Long,
        compressedSize: Long,
        uncompressedSize: Long,
        extra: ByteArray,
    ) {
        writeInt(out, 0x04034b50)
        writeShort(out, 20)
        writeShort(out, 0)
        writeShort(out, method)
        writeShort(out, 0)
        writeShort(out, 0)
        writeInt(out, crc.toInt())
        writeInt(out, compressedSize.toInt())
        writeInt(out, uncompressedSize.toInt())
        writeShort(out, nameBytes.size)
        writeShort(out, extra.size)
        out.write(nameBytes)
    }

    private fun writeCentralDirectoryEntry(
        out: ByteArrayOutputStream,
        nameBytes: ByteArray,
        method: Int,
        crc: Long,
        compressedSize: Long,
        uncompressedSize: Long,
        extra: ByteArray,
        offset: Long,
    ) {
        writeInt(out, 0x02014b50)
        writeShort(out, 20)
        writeShort(out, 20)
        writeShort(out, 0)
        writeShort(out, method)
        writeShort(out, 0)
        writeShort(out, 0)
        writeInt(out, crc.toInt())
        writeInt(out, compressedSize.toInt())
        writeInt(out, uncompressedSize.toInt())
        writeShort(out, nameBytes.size)
        writeShort(out, extra.size)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, 0)
        writeInt(out, 0)
        writeInt(out, offset.toInt())
        out.write(nameBytes)
        if (extra.isNotEmpty()) out.write(extra)
    }

    private fun writeEndOfCentralDirectory(
        out: FileOutputStream,
        entryCount: Int,
        centralDirectorySize: Int,
        centralDirectoryOffset: Long,
    ) {
        writeInt(out, 0x06054b50)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, entryCount)
        writeShort(out, entryCount)
        writeInt(out, centralDirectorySize)
        writeInt(out, centralDirectoryOffset.toInt())
        writeShort(out, 0)
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
        out.write(value shr 16 and 0xFF)
        out.write(value shr 24 and 0xFF)
    }

    private fun writeShort(out: FileOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    private fun writeInt(out: FileOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
        out.write(value shr 16 and 0xFF)
        out.write(value shr 24 and 0xFF)
    }
}

internal data class ApkEntry(
    val name: String,
    val data: ByteArray,
    val method: Int = ApkPacker.compressionMethod(name),
    val alignment: Int = ApkPacker.alignmentFor(name),
)
