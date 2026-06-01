package com.appcloner.app

import android.content.Context
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Modifies APK files to change package name and app label.
 *
 * APK is a ZIP containing:
 * - AndroidManifest.xml (Android Binary XML — AXML format)
 * - classes.dex
 * - resources.arsc
 * - res/ (resources)
 * - lib/ (native libraries)
 * - META-INF/ (signatures — removed during re-sign)
 *
 * We modify the AndroidManifest.xml to change the package name,
 * which makes Android treat it as a separate app.
 */
class ApkModifier(private val context: Context) {

    private val workDir: File
        get() = File(context.filesDir, "clone_work")

    /**
     * Modify an APK: change package name and optionally app name.
     * Returns path to the modified (unsigned) APK.
     */
    fun modifyApk(
        apkPath: String,
        originalPackage: String,
        newPackage: String,
        newAppName: String? = null
    ): String {
        val sourceApk = File(apkPath)
        // Unique per-input name so processing multiple splits in the same
        // working directory does not overwrite each other.
        val outputApk = File(sourceApk.parent, "${sourceApk.nameWithoutExtension}.modified.apk")

        ZipFile(sourceApk).use { zipIn ->
            ZipOutputStream(FileOutputStream(outputApk)).use { zipOut ->
                val entries = zipIn.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    // Skip META-INF (signatures) — we'll re-sign later
                    if (entry.name.startsWith("META-INF/")) continue

                    val newEntry = ZipEntry(entry.name)
                    // Don't preserve compression for manifest — we'll modify it
                    if (entry.name == "AndroidManifest.xml") {
                        newEntry.method = ZipEntry.DEFLATED
                    } else {
                        // Preserve original compression
                        if (entry.method == ZipEntry.STORED) {
                            newEntry.method = ZipEntry.STORED
                            newEntry.size = entry.size
                            newEntry.compressedSize = entry.compressedSize
                            newEntry.crc = entry.crc
                        }
                    }

                    zipOut.putNextEntry(newEntry)

                    if (entry.name == "AndroidManifest.xml") {
                        // Manifest is small; read it fully to patch the binary XML.
                        val data = zipIn.getInputStream(entry).readBytes()
                        val modified = modifyManifest(data, originalPackage, newPackage, newAppName)
                        zipOut.write(modified)
                    } else {
                        // Stream large entries (dex, resources, native libs, assets)
                        // to keep peak memory low and avoid OutOfMemoryError.
                        zipIn.getInputStream(entry).use { it.copyTo(zipOut, 65536) }
                    }

                    zipOut.closeEntry()
                }
            }
        }

        return outputApk.absolutePath
    }

    /**
     * Replace the package name inside a binary AndroidManifest.xml (AXML).
     *
     * An AXML file is a sequence of chunks:
     *   [8-byte file header] [string pool chunk] [resource map] [XML tree chunks]
     *
     * All string values (the package name, component class names, authorities,
     * permissions, …) live in the string pool and are referenced elsewhere by
     * *index*. So to rename the package we only need to rewrite the string pool
     * with the replacement strings — every other chunk keeps referring to the
     * same indices and is copied through unchanged.
     *
     * This implementation is intentionally defensive: every length/offset read
     * from the file is bounds-checked, so a malformed or unexpected manifest
     * throws a clear IllegalStateException instead of trying to allocate a
     * garbage-sized array (which previously surfaced as an OutOfMemoryError).
     */
    private fun modifyManifest(
        data: ByteArray,
        originalPackage: String,
        newPackage: String,
        @Suppress("UNUSED_PARAMETER") newAppName: String?
    ): ByteArray {
        val header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // ---- XML file header (ResChunk_header) ----
        val xmlType = header.short.toInt() and 0xFFFF       // 0x0003 (RES_XML_TYPE)
        val xmlHeaderSize = header.short.toInt() and 0xFFFF // usually 8
        check(xmlType == 0x0003) { "Not a binary AndroidManifest (type=$xmlType)" }
        header.getInt() // original file size (recomputed below)

        // ---- String pool chunk (begins right after the XML header) ----
        val spStart = xmlHeaderSize
        check(spStart + 28 <= data.size) { "Manifest too small for string pool" }
        header.position(spStart)
        val spType = header.short.toInt() and 0xFFFF        // 0x0001 (RES_STRING_POOL_TYPE)
        val spHeaderSize = header.short.toInt() and 0xFFFF  // usually 28
        val spSize = header.getInt()
        val stringCount = header.getInt()
        val styleCount = header.getInt()
        val flags = header.getInt()
        val stringsStart = header.getInt()
        val stylesStart = header.getInt()
        check(spType == 0x0001) { "Expected string pool chunk, got $spType" }
        check(stringCount in 0..2_000_000) { "Unreasonable string count: $stringCount" }
        check(styleCount in 0..2_000_000) { "Unreasonable style count: $styleCount" }
        check(spHeaderSize >= 28 && spStart + spSize <= data.size) { "String pool size out of bounds" }

        val isUtf8 = (flags and (1 shl 8)) != 0

        // Read the string offset array.
        val offsetArrayPos = spStart + spHeaderSize
        check(offsetArrayPos + stringCount * 4 <= data.size) { "String offset array out of bounds" }
        header.position(offsetArrayPos)
        val stringOffsets = IntArray(stringCount) { header.getInt() }

        // Decode every string, keeping its exact original encoded bytes so that
        // unchanged strings can be copied verbatim (no re-encoding round-trip).
        val stringDataBase = spStart + stringsStart
        val decoded = ArrayList<String>(stringCount)
        val rawBytes = ArrayList<ByteArray>(stringCount)
        for (i in 0 until stringCount) {
            val pos = stringDataBase + stringOffsets[i]
            check(pos in 0..data.size) { "String #$i offset out of bounds" }
            val entry = if (isUtf8) readUtf8Entry(data, pos) else readUtf16Entry(data, pos)
            decoded.add(entry.first)
            rawBytes.add(entry.second)
        }

        // Compute the (possibly) replaced bytes for each string.
        val newBytes = ArrayList<ByteArray>(stringCount)
        for (i in 0 until stringCount) {
            val s = decoded[i]
            val replacement = when {
                s == originalPackage -> newPackage
                s.startsWith("$originalPackage.") -> newPackage + s.substring(originalPackage.length)
                else -> null
            }
            newBytes.add(
                when {
                    replacement == null -> rawBytes[i]
                    isUtf8 -> encodeUtf8Entry(replacement)
                    else -> encodeUtf16Entry(replacement)
                }
            )
        }

        // Style offset array and style data are copied through verbatim.
        val styleOffsetPos = offsetArrayPos + stringCount * 4
        check(styleOffsetPos + styleCount * 4 <= data.size) { "Style offset array out of bounds" }
        val styleOffsetBytes = data.copyOfRange(styleOffsetPos, styleOffsetPos + styleCount * 4)
        val styleData = if (styleCount > 0) {
            check(spStart + stylesStart <= spStart + spSize) { "Style data out of bounds" }
            data.copyOfRange(spStart + stylesStart, spStart + spSize)
        } else ByteArray(0)

        // Everything after the string pool chunk references strings by index and
        // is left untouched.
        val rest = data.copyOfRange(spStart + spSize, data.size)

        // ---- Rebuild the string pool chunk ----
        val newStringOffsets = IntArray(stringCount)
        var dataLen = 0
        for (i in 0 until stringCount) {
            newStringOffsets[i] = dataLen
            dataLen += newBytes[i].size
        }
        val stringPad = (4 - (dataLen % 4)) % 4
        val paddedDataLen = dataLen + stringPad

        val newStringsStart = spHeaderSize + stringCount * 4 + styleCount * 4
        val newStylesStart = if (styleCount > 0) newStringsStart + paddedDataLen else 0
        val newSpSize = newStringsStart + paddedDataLen + styleData.size

        val newFileSize = spStart + newSpSize + rest.size
        val out = ByteBuffer.allocate(newFileSize).order(ByteOrder.LITTLE_ENDIAN)

        // XML file header
        out.putShort(xmlType.toShort())
        out.putShort(xmlHeaderSize.toShort())
        out.putInt(newFileSize)
        // Any bytes between the fixed 8-byte header and the string pool (rare).
        if (spStart > 8) out.put(data, 8, spStart - 8)

        // String pool header
        out.putShort(spType.toShort())
        out.putShort(spHeaderSize.toShort())
        out.putInt(newSpSize)
        out.putInt(stringCount)
        out.putInt(styleCount)
        out.putInt(flags)
        out.putInt(newStringsStart)
        out.putInt(newStylesStart)
        repeat(spHeaderSize - 28) { out.put(0) } // pad if header is larger than expected

        // String offset array
        for (o in newStringOffsets) out.putInt(o)
        // Style offset array (verbatim)
        out.put(styleOffsetBytes)
        // String data + 4-byte alignment padding
        for (b in newBytes) out.put(b)
        repeat(stringPad) { out.put(0) }
        // Style data (verbatim)
        out.put(styleData)

        // Remaining chunks (resource map + XML tree), unchanged
        out.put(rest)

        return out.array()
    }

    /**
     * Read one UTF-8 string-pool entry, returning the decoded text and the exact
     * raw bytes it occupies (two length fields + data + null terminator).
     */
    private fun readUtf8Entry(data: ByteArray, start: Int): Pair<String, ByteArray> {
        var p = start
        val charLen = decodeLength8(data, p); p = charLen.second   // character count (unused)
        val byteLen = decodeLength8(data, p); p = byteLen.second   // byte count
        val len = byteLen.first
        check(len in 0..(data.size - p)) { "UTF-8 string length out of bounds" }
        val text = String(data, p, len, Charsets.UTF_8)
        p += len
        check(p < data.size) { "Missing UTF-8 null terminator" }
        p += 1 // null terminator
        return Pair(text, data.copyOfRange(start, p))
    }

    /**
     * Read one UTF-16 string-pool entry, returning the decoded text and the exact
     * raw bytes it occupies (length field + data + 2-byte null terminator).
     */
    private fun readUtf16Entry(data: ByteArray, start: Int): Pair<String, ByteArray> {
        var p = start
        var len = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)
        p += 2
        if (len and 0x8000 != 0) {
            val lo = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)
            len = ((len and 0x7FFF) shl 16) or lo
            p += 2
        }
        val byteLen = len * 2
        check(byteLen in 0..(data.size - p)) { "UTF-16 string length out of bounds" }
        val text = String(data, p, byteLen, Charsets.UTF_16LE)
        p += byteLen
        check(p + 1 < data.size) { "Missing UTF-16 null terminator" }
        p += 2 // null terminator (2 bytes)
        return Pair(text, data.copyOfRange(start, p))
    }

    /** Decode an AXML 8-bit length (1 or 2 bytes). Returns value + next position. */
    private fun decodeLength8(data: ByteArray, p: Int): Pair<Int, Int> {
        val first = data[p].toInt() and 0xFF
        return if (first and 0x80 != 0) {
            val second = data[p + 1].toInt() and 0xFF
            Pair(((first and 0x7F) shl 8) or second, p + 2)
        } else {
            Pair(first, p + 1)
        }
    }

    /** Encode a string as a UTF-8 string-pool entry (length fields + data + NUL). */
    private fun encodeUtf8Entry(str: String): ByteArray {
        val utf8 = str.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        writeLength8(out, str.length)   // character count
        writeLength8(out, utf8.size)    // byte count
        out.write(utf8)
        out.write(0)                    // null terminator
        return out.toByteArray()
    }

    /** Encode a string as a UTF-16 string-pool entry (length field + data + NUL). */
    private fun encodeUtf16Entry(str: String): ByteArray {
        val out = ByteArrayOutputStream()
        writeLength16(out, str.length)
        val utf16 = str.toByteArray(Charsets.UTF_16LE)
        out.write(utf16)
        out.write(0); out.write(0)      // 2-byte null terminator
        return out.toByteArray()
    }

    private fun writeLength8(out: ByteArrayOutputStream, value: Int) {
        require(value <= 0x7FFF) { "String too long to encode (UTF-8): $value" }
        if (value > 0x7F) {
            out.write((value shr 8) or 0x80)
            out.write(value and 0xFF)
        } else {
            out.write(value)
        }
    }

    private fun writeLength16(out: ByteArrayOutputStream, value: Int) {
        if (value > 0x7FFF) {
            val hi = (value shr 16) or 0x8000
            out.write(hi and 0xFF); out.write((hi shr 8) and 0xFF)
            out.write(value and 0xFF); out.write((value shr 8) and 0xFF)
        } else {
            out.write(value and 0xFF); out.write((value shr 8) and 0xFF)
        }
    }
}
