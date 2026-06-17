package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ApkEntry
import `fun`.abbas.wps_adb.data.ApkPacker
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals

class ApkPackerTest {

    @Test
    fun repack_storesResourcesArscUncompressedAndAligned() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "apk-packer-${System.nanoTime()}").apply { mkdirs() }
        val arscBytes = ByteArray(13) { 0x02 }
        val output = File(tempDir, "packed.apk")
        ApkPacker.repack(
            entries = listOf(
                ApkEntry("classes.dex", byteArrayOf(0x64, 0x65, 0x78, 0x0a)),
                ApkEntry("resources.arsc", arscBytes),
            ),
            output = output,
        )

        ZipFile(output).use { zip ->
            val arsc = zip.getEntry("resources.arsc")
            assertEquals(ZipEntry.STORED, arsc.method)
            assertEquals(arscBytes.size.toLong(), arsc.size)
        }

        val dataOffset = localFileDataOffset(output, "resources.arsc")
        assertEquals(0L, dataOffset % 4)
    }

    private fun localFileDataOffset(apk: File, entryName: String): Long {
        RandomAccessFile(apk, "r").use { raf ->
            val fileLength = raf.length()
            var offset = 0L
            while (offset + 30 <= fileLength) {
                raf.seek(offset)
                if (raf.readInt().reverseBytes() != 0x04034b50) break
                val compressedSize = readUInt32At(raf, offset + 18)
                val nameLength = readUInt16At(raf, offset + 26)
                val extraLength = readUInt16At(raf, offset + 28)
                raf.seek(offset + 30)
                val name = ByteArray(nameLength).also { raf.readFully(it) }.decodeToString()
                val dataOffset = offset + 30 + nameLength + extraLength
                if (name == entryName) return dataOffset
                offset = dataOffset + compressedSize
            }
        }
        error("Entry not found: $entryName")
    }

    private fun readUInt16At(raf: RandomAccessFile, offset: Long): Int {
        raf.seek(offset)
        return readUInt16(raf)
    }

    private fun readUInt16(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        return b0 or (b1 shl 8)
    }

    private fun readUInt32At(raf: RandomAccessFile, offset: Long): Long {
        raf.seek(offset)
        val b0 = raf.read().toLong()
        val b1 = raf.read().toLong()
        val b2 = raf.read().toLong()
        val b3 = raf.read().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun Int.reverseBytes(): Int {
        return (this ushr 24) or ((this shr 8) and 0xFF00) or ((this shl 8) and 0xFF0000) or (this shl 24)
    }
}
