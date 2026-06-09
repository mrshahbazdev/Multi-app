package com.appcloner.app

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Pure Kotlin implementation of Android's Zipalign.
 * Aligns uncompressed entries (like .so files) to 4-byte or 4096-byte boundaries.
 */
object ZipAlign {
    private const val TAG = "ZipAlign"

    fun alignZip(inFile: File, outFile: File) {
        val inRaf = RandomAccessFile(inFile, "r")
        val outRaf = RandomAccessFile(outFile, "rw")
        outRaf.setLength(0) // Clear output if exists

        try {
            alignZipInternal(inRaf, outRaf)
        } finally {
            inRaf.close()
            outRaf.close()
        }
    }

    private fun alignZipInternal(inRaf: RandomAccessFile, outRaf: RandomAccessFile) {
        val fileOffsets = mutableListOf<Int>()
        var header = readIntLE(inRaf)

        while (header == 0x04034b50) {
            val currentOffset = outRaf.filePointer.toInt()
            fileOffsets.add(currentOffset)

            writeIntLE(outRaf, 0x04034b50)

            val version = readShortLE(inRaf)
            writeShortLE(outRaf, version)

            val flags = readShortLE(inRaf)
            writeShortLE(outRaf, flags)

            val hasDataDescriptor = (flags.toInt() and 0x8) == 0x8

            val compression = readShortLE(inRaf)
            writeShortLE(outRaf, compression)

            val shouldAlign = compression.toInt() == 0

            passBytes(inRaf, outRaf, 8) // modTime, modDate, crc

            val compressedSize = readIntLE(inRaf)
            writeIntLE(outRaf, compressedSize)

            passBytes(inRaf, outRaf, 4) // uncompressedSize

            val fileNameLen = readShortLE(inRaf)
            writeShortLE(outRaf, fileNameLen)

            val extraFieldLen = readShortLE(inRaf)

            // Read filename to determine alignment
            val fileNameBytes = ByteArray(fileNameLen.toInt())
            inRaf.readFully(fileNameBytes)
            val fileName = String(fileNameBytes)

            val alignment = if (fileName.endsWith(".so")) 4096 else 4

            // dataStartPoint is current pos + 2 (extraFieldLen offset) + fileNameLen + extraFieldLen
            val dataStartPoint = outRaf.filePointer.toInt() + 2 + fileNameLen.toInt() + extraFieldLen.toInt()

            val wrongOffset = dataStartPoint % alignment
            var paddingSize = if (wrongOffset == 0) 0 else alignment - wrongOffset

            // A ZIP Extra Field block requires 4 bytes for the header (ID + Size).
            if (shouldAlign && paddingSize > 0 && paddingSize < 4) {
                paddingSize += alignment
            }

            if (shouldAlign && paddingSize > 0) {
                writeShortLE(outRaf, (extraFieldLen + paddingSize).toShort())
            } else {
                writeShortLE(outRaf, extraFieldLen)
                paddingSize = 0 // Enforce 0 if we shouldn't align
            }

            outRaf.write(fileNameBytes)

            // Pass original extra field
            passBytes(inRaf, outRaf, extraFieldLen.toLong())

            if (shouldAlign && paddingSize > 0) {
                // Write official Android Zipalign Extra Field (0xD935)
                writeShortLE(outRaf, 0xD935.toShort()) // Header ID
                writeShortLE(outRaf, (paddingSize - 4).toShort()) // Data Size
                if (paddingSize - 4 > 0) {
                    outRaf.write(ByteArray(paddingSize - 4))
                }
            }

            if (!hasDataDescriptor) {
                passBytes(inRaf, outRaf, compressedSize.toLong())
                header = readIntLE(inRaf)
                continue
            }

            // Has Data Descriptor
            val buffer = ByteArray(8192)
            var sig = 0
            var found = false
            while (!found) {
                val readBytes = inRaf.read(buffer)
                if (readBytes < 0) throw java.io.EOFException()

                for (i in 0 until readBytes) {
                    val cur = buffer[i].toInt() and 0xFF
                    sig = (sig ushr 8) or (cur shl 24)
                    if (sig == 0x08074b50) {
                        found = true
                        outRaf.write(buffer, 0, i + 1)
                        val overRead = readBytes - 1 - i
                        inRaf.seek(inRaf.filePointer - overRead)
                        break
                    }
                }
                if (!found) {
                    outRaf.write(buffer, 0, readBytes)
                }
            }

            // Pass data descriptor fields (crc, compSize, uncompSize) = 12 bytes
            passBytes(inRaf, outRaf, 12)

            header = readIntLE(inRaf)
        }

        val centralDirectoryPosition = outRaf.filePointer.toInt()
        var fileOffsetIndex = 0

        while (header == 0x02014b50) {
            writeIntLE(outRaf, 0x02014b50)
            val fileOffset = fileOffsets[fileOffsetIndex]

            passBytes(inRaf, outRaf, 24)

            val fileNameLen = readShortLE(inRaf)
            writeShortLE(outRaf, fileNameLen)

            val extraFieldLen = readShortLE(inRaf)
            writeShortLE(outRaf, extraFieldLen)

            val fileCommentLen = readShortLE(inRaf)
            writeShortLE(outRaf, fileCommentLen)

            passBytes(inRaf, outRaf, 8)

            // Skip old offset, write new offset
            readIntLE(inRaf)
            writeIntLE(outRaf, fileOffset)

            passBytes(inRaf, outRaf, fileNameLen.toLong())
            passBytes(inRaf, outRaf, extraFieldLen.toLong())
            passBytes(inRaf, outRaf, fileCommentLen.toLong())

            fileOffsetIndex++
            header = readIntLE(inRaf)
        }

        if (header != 0x06054b50) {
            throw Exception("No end of central directory record header found!")
        }

        writeIntLE(outRaf, 0x06054b50)
        passBytes(inRaf, outRaf, 12)

        // Offset of central directory
        readIntLE(inRaf)
        writeIntLE(outRaf, centralDirectoryPosition)

        val commentLen = readShortLE(inRaf)
        writeShortLE(outRaf, commentLen)
        passBytes(inRaf, outRaf, commentLen.toLong())
        
        Log.d(TAG, "Zipalign completed successfully!")
    }

    private fun readIntLE(raf: RandomAccessFile): Int {
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        val b4 = raf.read()
        if (b4 < 0) throw java.io.EOFException()
        return (b1) or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    private fun writeIntLE(raf: RandomAccessFile, v: Int) {
        raf.write(v and 0xFF)
        raf.write((v ushr 8) and 0xFF)
        raf.write((v ushr 16) and 0xFF)
        raf.write((v ushr 24) and 0xFF)
    }

    private fun readShortLE(raf: RandomAccessFile): Short {
        val b1 = raf.read()
        val b2 = raf.read()
        if (b2 < 0) throw java.io.EOFException()
        return ((b1) or (b2 shl 8)).toShort()
    }

    private fun writeShortLE(raf: RandomAccessFile, v: Short) {
        raf.write(v.toInt() and 0xFF)
        raf.write((v.toInt() ushr 8) and 0xFF)
    }

    private fun passBytes(inRaf: RandomAccessFile, outRaf: RandomAccessFile, len: Long) {
        if (len <= 0) return
        val buffer = ByteArray(8192)
        var left = len
        while (left > 0) {
            val toRead = if (left > buffer.size) buffer.size else left.toInt()
            val read = inRaf.read(buffer, 0, toRead)
            if (read < 0) throw java.io.EOFException()
            outRaf.write(buffer, 0, read)
            left -= read
        }
    }
}
