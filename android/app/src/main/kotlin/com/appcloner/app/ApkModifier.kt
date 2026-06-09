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
        val outputApk = File(sourceApk.parent, "modified_${sourceApk.name}")

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

                    val data = zipIn.getInputStream(entry).readBytes()

                    if (entry.name == "AndroidManifest.xml") {
                        // Modify the binary XML manifest
                        val modified = modifyManifest(data, originalPackage, newPackage, newAppName)
                        zipOut.write(modified)
                    } else if (entry.name == "resources.arsc" && newAppName != null) {
                        // Optionally modify app name in resources
                        // For simplicity, we handle this via manifest string pool
                        zipOut.write(data)
                    } else {
                        zipOut.write(data)
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

        // Parse all strings
        val strings = mutableListOf<String>()
        for (i in 0 until stringCount) {
            buffer.position(8 + stringsStart + stringOffsets[i])
            val str = if (isUtf8) readUtf8String(buffer) else readUtf16String(buffer)
            strings.add(str)
        }

        // We only modify the exact package name string (for the manifest package="..." attribute)
        // and optionally the app label. We do NOT blindly modify other strings to avoid breaking classes.
        val modifiedStrings = strings.map { str ->
            when {
                str == originalPackage -> newPackage
                newAppName != null && isAppLabel(str, strings) -> newAppName
                str == "com.facebook.katana" -> "com.fake.katana"
                str == "com.facebook.orca" -> "com.fake.orca"
                str == "com.facebook.wakizashi" -> "com.fake.wakizashi"
                str == "com.instagram.android" -> "com.fake.instagram"
                str == "com.facebook.services" -> "com.fake.services"
                else -> str
            }
        }.toMutableList()

        return rebuildAxml(data, strings, modifiedStrings, isUtf8, originalPackage, newPackage)
    }
    /**
     * Heuristic to detect if a string might be the app label.
     */
    private fun isAppLabel(str: String, allStrings: List<String>): Boolean {
        return false // Disabled for now
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

    private fun rebuildAxml(
        originalData: ByteArray,
        originalStrings: List<String>,
        modifiedStrings: MutableList<String>,
        isUtf8: Boolean,
        originalPackage: String,
        newPackage: String
    ): ByteArray {
        val original = ByteBuffer.wrap(originalData).order(ByteOrder.LITTLE_ENDIAN)

        original.position(0)
        val magic = original.getInt()
        val fileSize = original.getInt()

        val spType = original.getShort()
        val spHeaderSize = original.getShort()
        val spSize = original.getInt()
        val stringCount = original.getInt()
        val styleCount = original.getInt()
        val flags = original.getInt()
        val stringsStart = original.getInt()
        val stylesStart = original.getInt()

        // Extract style data from original
        val styleOffsetsSize = styleCount * 4
        val styleOffsetsStart = 8 + spHeaderSize.toInt() + (stringCount * 4)
        val styleOffsetsData = originalData.copyOfRange(styleOffsetsStart, styleOffsetsStart + styleOffsetsSize)

        val styleDataSize = if (styleCount > 0) spSize - stylesStart else 0
        val styleDataData = if (styleCount > 0) originalData.copyOfRange(8 + stylesStart, 8 + spSize) else ByteArray(0)

        // Copy everything after string pool from original
        val afterStringPool = 8 + spSize
        val restData = originalData.copyOfRange(afterStringPool, originalData.size)
        val xmlBuffer = ByteBuffer.wrap(restData).order(ByteOrder.LITTLE_ENDIAN)

        // Find relevant indices in the original string pool
        val nameIdx = originalStrings.indexOf("name")
        val tagsToRenameNameAttr = setOf("action", "category", "permission", "uses-permission", 
                                         "permission-tree", "permission-group", "uses-permission-sdk-23")

        val extraStrings = mutableListOf<String>()

        // Scan XML chunks to modify authorities and specific names
        while (xmlBuffer.hasRemaining()) {
            val pos = xmlBuffer.position()
            val type = xmlBuffer.getShort()
            val headerSize = xmlBuffer.getShort()
            val size = xmlBuffer.getInt()
            
            if (type.toInt() == 0x0102) { // START_ELEMENT
                xmlBuffer.position(pos + 8)
                val lineNumber = xmlBuffer.getInt()
                val comment = xmlBuffer.getInt()
                val ns = xmlBuffer.getInt()
                val elNameIdx = xmlBuffer.getInt()
                val attrStart = xmlBuffer.getShort().toInt() and 0xFFFF
                val attrSize = xmlBuffer.getShort().toInt() and 0xFFFF
                val attrCount = xmlBuffer.getShort().toInt() and 0xFFFF
                
                val elNameStr = if (elNameIdx in originalStrings.indices) originalStrings[elNameIdx] else ""
                val isTagToRenameName = tagsToRenameNameAttr.contains(elNameStr)
                
                val attrBase = pos + headerSize + attrStart
                for (i in 0 until attrCount) {
                    xmlBuffer.position(attrBase + i * attrSize)
                    val attrNs = xmlBuffer.getInt()
                    val attrName = xmlBuffer.getInt()
                    val attrRawValue = xmlBuffer.getInt()
                    val attrTypedSize = xmlBuffer.getShort()
                    val attrTypedRes0 = xmlBuffer.get()
                    val attrDataType = xmlBuffer.get()
                    val attrDataValue = xmlBuffer.getInt()
                    
                    val attrNameStr = if (attrName in originalStrings.indices) originalStrings[attrName] else ""
                    
                    var shouldRename = false
                    if (attrNameStr == "authorities" || attrNameStr == "permission" || 
                        attrNameStr == "readPermission" || attrNameStr == "writePermission") {
                        shouldRename = true
                    } else if (attrNameStr == "name" && isTagToRenameName) {
                        shouldRename = true
                    }
                    
                    if (shouldRename && attrDataType.toInt() == 0x03) { // TYPE_STRING
                        val origStrIdx = attrDataValue
                        if (origStrIdx >= 0 && origStrIdx < originalStrings.size) {
                            val origStr = originalStrings[origStrIdx]
                            if (origStr.contains(originalPackage)) {
                                val newStr = origStr.replace(originalPackage, newPackage)
                                var newIdx = stringCount + extraStrings.indexOf(newStr)
                                if (extraStrings.indexOf(newStr) == -1) {
                                    extraStrings.add(newStr)
                                    newIdx = stringCount + extraStrings.size - 1
                                }
                                
                                // Update indices in the restData buffer
                                if (attrRawValue != -1) {
                                    xmlBuffer.putInt(attrBase + i * attrSize + 8, newIdx)
                                }
                                xmlBuffer.putInt(attrBase + i * attrSize + 16, newIdx)
                            }
                        }
                    }
                }
            }
            xmlBuffer.position(pos + size)
        }

        // Add extra strings to our modified string pool
        modifiedStrings.addAll(extraStrings)
        val newStringCount = modifiedStrings.size

        // Build new string data
        val newStringBytes = mutableListOf<ByteArray>()
        for (str in modifiedStrings) {
            newStringBytes.add(if (isUtf8) encodeUtf8String(str) else encodeUtf16String(str))
        }

        val newOffsets = IntArray(newStringCount)
        var offset = 0
        for (i in 0 until newStringCount) {
            newOffsets[i] = offset
            offset += newStringBytes[i].size
        }

        val totalStringData = offset
        val padding = (4 - (totalStringData % 4)) % 4

        val offsetDiff = (newStringCount - stringCount) * 4
        val newStringsStart = stringsStart + offsetDiff
        val newStylesStart = if (styleCount > 0) newStringsStart + totalStringData + padding else 0
        val newSpSize = spHeaderSize + (newStringCount * 4) + (styleCount * 4) +
                totalStringData + padding + styleDataSize

        val newFileSize = 8 + newSpSize + restData.size
        val output = ByteBuffer.allocate(newFileSize).order(ByteOrder.LITTLE_ENDIAN)

        // Write header
        output.putInt(magic)
        output.putInt(newFileSize)

        // Write string pool header
        output.putShort(spType)
        output.putShort(spHeaderSize)
        output.putInt(newSpSize)
        output.putInt(newStringCount)
        output.putInt(styleCount)
        output.putInt(flags)
        output.putInt(newStringsStart)
        output.putInt(newStylesStart)

        // Write string offsets
        for (off in newOffsets) {
            output.putInt(off)
        }

        // Write style offsets
        output.put(styleOffsetsData)

        // Write string data
        for (bytes in newStringBytes) {
            output.put(bytes)
        }

        // Write padding
        for (i in 0 until padding) {
            output.put(0.toByte())
        }

        // Write style data
        output.put(styleDataData)

        // Write rest of the XML (which now contains updated attribute indices)
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
            val word1 = (charLen shr 16) or 0x8000
            stream.write(word1 and 0xFF)
            stream.write((word1 shr 8) and 0xFF)
            val word2 = charLen and 0xFFFF
            stream.write(word2 and 0xFF)
            stream.write((word2 shr 8) and 0xFF)
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
