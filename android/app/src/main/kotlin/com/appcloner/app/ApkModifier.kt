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
        val outputApk = File(sourceApk.parent, "modified.apk")

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
     * Modify AndroidManifest.xml in AXML (Android Binary XML) format.
     *
     * AXML structure:
     * - Header (magic + file size)
     * - String Pool (all strings used in the XML)
     * - Resource IDs
     * - XML content (start/end tags, attributes)
     *
     * We modify strings in the String Pool to replace package names.
     */
    private fun modifyManifest(
        data: ByteArray,
        originalPackage: String,
        newPackage: String,
        newAppName: String?
    ): ByteArray {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Read AXML header
        val magic = buffer.getInt()      // 0x00080003
        val fileSize = buffer.getInt()

        // String Pool header
        val stringPoolType = buffer.getShort()   // 0x0001
        val stringPoolHeaderSize = buffer.getShort()
        val stringPoolSize = buffer.getInt()
        val stringCount = buffer.getInt()
        val styleCount = buffer.getInt()
        val flags = buffer.getInt()
        val stringsStart = buffer.getInt()
        val stylesStart = buffer.getInt()

        val isUtf8 = (flags and (1 shl 8)) != 0

        // Read string offsets
        val stringOffsets = IntArray(stringCount) { buffer.getInt() }

        // Read the raw string data
        val stringDataStart = 8 + stringPoolHeaderSize + (stringCount * 4) + (styleCount * 4)
        val stringDataSize = stringPoolSize - (stringDataStart - 8)

        // Parse all strings
        val strings = mutableListOf<String>()
        val savedPosition = buffer.position()

        for (i in 0 until stringCount) {
            buffer.position(8 + stringPoolHeaderSize.toInt() + (stringCount * 4) + (styleCount * 4) + stringsStart + stringOffsets[i])
            val str = if (isUtf8) readUtf8String(buffer) else readUtf16String(buffer)
            strings.add(str)
        }

        // Replace package name in strings
        val modifiedStrings = strings.map { str ->
            when {
                str == originalPackage -> newPackage
                str.startsWith("$originalPackage.") ->
                    str.replace(originalPackage, newPackage)
                newAppName != null && isAppLabel(str, strings) -> newAppName
                else -> str
            }
        }

        // Rebuild the AXML with modified strings
        return rebuildAxml(data, strings, modifiedStrings, isUtf8)
    }

    /**
     * Heuristic to detect if a string might be the app label.
     * The app label is typically a short human-readable string.
     */
    private fun isAppLabel(str: String, allStrings: List<String>): Boolean {
        // App labels are typically short and don't contain dots or slashes
        return false // Disabled for now — too risky to change wrong strings
    }

    /**
     * Read a UTF-8 string from AXML string pool.
     */
    private fun readUtf8String(buffer: ByteBuffer): String {
        val charLen = readUnsignedLeb128(buffer)
        val byteLen = readUnsignedLeb128(buffer)
        val bytes = ByteArray(byteLen)
        buffer.get(bytes)
        buffer.get() // null terminator
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Read a UTF-16 string from AXML string pool.
     */
    private fun readUtf16String(buffer: ByteBuffer): String {
        var charLen = buffer.getShort().toInt() and 0xFFFF
        if (charLen and 0x8000 != 0) {
            charLen = ((charLen and 0x7FFF) shl 16) or (buffer.getShort().toInt() and 0xFFFF)
        }
        val chars = CharArray(charLen)
        for (i in 0 until charLen) {
            chars[i] = buffer.getShort().toChar()
        }
        buffer.getShort() // null terminator
        return String(chars)
    }

    private fun readUnsignedLeb128(buffer: ByteBuffer): Int {
        var value = buffer.get().toInt() and 0xFF
        if (value > 0x7F) {
            val second = buffer.get().toInt() and 0xFF
            value = (value and 0x7F) or (second shl 7)
        }
        return value
    }

    /**
     * Rebuild the entire AXML binary with modified string pool.
     * This is the most complex part — we need to:
     * 1. Rebuild string pool with new strings
     * 2. Update all offsets
     * 3. Keep the rest of the XML structure intact
     */
    private fun rebuildAxml(
        originalData: ByteArray,
        originalStrings: List<String>,
        newStrings: List<String>,
        isUtf8: Boolean
    ): ByteArray {
        val original = ByteBuffer.wrap(originalData).order(ByteOrder.LITTLE_ENDIAN)

        // Read original header info
        original.position(0)
        val magic = original.getInt()
        val fileSize = original.getInt()

        // String pool header
        val spType = original.getShort()
        val spHeaderSize = original.getShort()
        val spSize = original.getInt()
        val stringCount = original.getInt()
        val styleCount = original.getInt()
        val flags = original.getInt()
        val stringsStart = original.getInt()
        val stylesStart = original.getInt()

        // Build new string data
        val newStringBytes = mutableListOf<ByteArray>()
        for (str in newStrings) {
            newStringBytes.add(if (isUtf8) encodeUtf8String(str) else encodeUtf16String(str))
        }

        // Calculate new string pool offsets
        val newOffsets = IntArray(stringCount)
        var offset = 0
        for (i in 0 until stringCount) {
            newOffsets[i] = offset
            offset += newStringBytes[i].size
        }

        // Align to 4 bytes
        val totalStringData = offset
        val padding = (4 - (totalStringData % 4)) % 4

        // New string pool size
        val newStringsStart = stringsStart // Keep same relative offset
        val newSpSize = spHeaderSize + (stringCount * 4) + (styleCount * 4) +
                totalStringData + padding

        // Copy everything after string pool from original
        val afterStringPool = 8 + spSize
        val restData = originalData.copyOfRange(afterStringPool, originalData.size)

        // Build new file
        val newFileSize = 8 + newSpSize + restData.size
        val output = ByteBuffer.allocate(newFileSize).order(ByteOrder.LITTLE_ENDIAN)

        // Write header
        output.putInt(magic)
        output.putInt(newFileSize)

        // Write string pool header
        output.putShort(spType)
        output.putShort(spHeaderSize)
        output.putInt(newSpSize)
        output.putInt(stringCount)
        output.putInt(styleCount)
        output.putInt(flags)
        output.putInt(newStringsStart)
        output.putInt(stylesStart)

        // Write string offsets
        for (off in newOffsets) {
            output.putInt(off)
        }

        // Write style offsets (if any — usually 0)
        // Skip styles — copy from original if present

        // Write string data
        for (bytes in newStringBytes) {
            output.put(bytes)
        }

        // Write padding
        for (i in 0 until padding) {
            output.put(0)
        }

        // Write rest of the XML
        output.put(restData)

        return output.array()
    }

    private fun encodeUtf8String(str: String): ByteArray {
        val utf8Bytes = str.toByteArray(Charsets.UTF_8)
        val stream = ByteArrayOutputStream()
        // char length
        writeUnsignedLeb128(stream, str.length)
        // byte length
        writeUnsignedLeb128(stream, utf8Bytes.size)
        // string data
        stream.write(utf8Bytes)
        // null terminator
        stream.write(0)
        return stream.toByteArray()
    }

    private fun encodeUtf16String(str: String): ByteArray {
        val stream = ByteArrayOutputStream()
        val charLen = str.length
        if (charLen > 0x7FFF) {
            stream.write((charLen shr 16) or 0x8000)
            stream.write(charLen and 0xFFFF)
        } else {
            stream.write(charLen and 0xFF)
            stream.write((charLen shr 8) and 0xFF)
        }
        for (c in str) {
            stream.write(c.code and 0xFF)
            stream.write((c.code shr 8) and 0xFF)
        }
        // null terminator (2 bytes for UTF-16)
        stream.write(0)
        stream.write(0)
        return stream.toByteArray()
    }

    private fun writeUnsignedLeb128(stream: ByteArrayOutputStream, value: Int) {
        if (value <= 0x7F) {
            stream.write(value)
        } else {
            stream.write((value and 0x7F) or 0x80)
            stream.write(value shr 7)
        }
    }
}
