package `fun`.abbas.wps_adb.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

object ApkBinaryManifestParser {
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val UTF8_FLAG = 1 shl 8

    fun parsePackageName(apkFile: File): String? {
        if (!apkFile.isFile) return null
        return try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                parsePackageFromBinaryManifest(bytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun parsePackageFromBinaryManifest(bytes: ByteArray): String? {
        readStringPool(bytes)?.let { strings ->
            ApkPackageNames.pickBestPackage(strings)?.let { return it }
        }
        return ApkPackageNames.pickBestPackage(extractUtf16Candidates(bytes))
    }

    private fun readStringPool(bytes: ByteArray): List<String>? {
        if (bytes.size < 8) return null
        var offset = 8
        while (offset + 8 <= bytes.size) {
            val chunkType = readU16(bytes, offset)
            val chunkSize = readU32(bytes, offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) break
            if (chunkType == RES_STRING_POOL_TYPE) {
                return parseStringPoolChunk(bytes, offset, chunkSize)
            }
            offset += chunkSize
        }
        return null
    }

    private fun parseStringPoolChunk(bytes: ByteArray, offset: Int, chunkSize: Int): List<String>? {
        if (chunkSize < 0x001C) return null
        val stringCount = readU32(bytes, offset + 8)
        val flags = readU32(bytes, offset + 16)
        val stringsStart = readU32(bytes, offset + 20)
        if (stringCount <= 0 || stringsStart <= 0 || offset + stringsStart >= bytes.size) return null

        val offsetsStart = offset + 0x001C
        val stringsBase = offset + stringsStart
        val isUtf8 = flags and UTF8_FLAG != 0
        val strings = ArrayList<String>(stringCount)

        for (index in 0 until stringCount) {
            val stringOffset = readU32(bytes, offsetsStart + index * 4)
            val absolute = stringsBase + stringOffset
            if (absolute >= bytes.size) continue
            val decoded = if (isUtf8) {
                readUtf8String(bytes, absolute)
            } else {
                readUtf16String(bytes, absolute)
            }
            if (!decoded.isNullOrBlank()) {
                strings += decoded
            }
        }
        return strings.takeIf { it.isNotEmpty() }
    }

    private fun readUtf8String(bytes: ByteArray, offset: Int): String? {
        if (offset >= bytes.size) return null
        var index = offset
        val ignoredLength = readUtf8Length(bytes, index)
        index += utf8LengthSize(bytes, index) + ignoredLength.first
        val length = readUtf8Length(bytes, index)
        index += utf8LengthSize(bytes, index)
        val byteCount = length.first
        if (byteCount <= 0 || index + byteCount > bytes.size) return null
        return bytes.copyOfRange(index, index + byteCount).decodeToString()
    }

    private fun readUtf16String(bytes: ByteArray, offset: Int): String? {
        if (offset + 2 > bytes.size) return null
        val charCount = readU16(bytes, offset)
        val start = offset + 2
        val byteCount = charCount * 2
        if (charCount <= 0 || start + byteCount > bytes.size) return null
        val buffer = ByteBuffer.wrap(bytes, start, byteCount).order(ByteOrder.LITTLE_ENDIAN)
        val chars = CharArray(charCount)
        for (i in 0 until charCount) {
            chars[i] = buffer.short.toInt().toChar()
        }
        return chars.concatToString()
    }

    private fun readUtf8Length(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        if (offset >= bytes.size) return 0 to 0
        val first = bytes[offset].toInt() and 0xFF
        return if (first and 0x80 != 0) {
            val second = bytes.getOrNull(offset + 1)?.toInt()?.and(0xFF) ?: 0
            ((first and 0x7F) shl 8) or second to 2
        } else {
            first to 1
        }
    }

    private fun utf8LengthSize(bytes: ByteArray, offset: Int): Int =
        if ((bytes[offset].toInt() and 0x80) != 0) 2 else 1

    internal fun extractUtf16Candidates(bytes: ByteArray): List<String> {
        val results = linkedSetOf<String>()
        var index = 0
        while (index < bytes.size - 1) {
            val char = bytes[index].toInt() and 0xFF
            val hi = bytes[index + 1].toInt() and 0xFF
            if (hi == 0 && char in 'a'.code..'z'.code) {
                val builder = StringBuilder()
                var cursor = index
                while (cursor < bytes.size - 1) {
                    val lo = bytes[cursor].toInt() and 0xFF
                    val high = bytes[cursor + 1].toInt() and 0xFF
                    if (high != 0) break
                    val value = lo.toChar()
                    if (value.isLetterOrDigit() || value == '.' || value == '_') {
                        builder.append(value)
                        cursor += 2
                    } else {
                        break
                    }
                }
                if (builder.length >= 5 && '.' in builder) {
                    results += builder.toString().lowercase()
                }
                index = cursor
            } else {
                index++
            }
        }
        return results.toList()
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
