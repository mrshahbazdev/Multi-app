package com.appcloner.app

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Aligns the uncompressed (STORED) entries of an APK on a byte boundary, the
 * same way Android's `zipalign` tool does.
 *
 * Android 11+ (apps targeting SDK 30+) refuses to install an APK whose
 * `resources.arsc` is not stored uncompressed AND aligned on a 4-byte
 * boundary — the install fails with "App not installed", and a launch attempt
 * then reports the package as missing. `ZipOutputStream` (used when we merge
 * splits / rewrite the manifest) preserves the STORED method but never inserts
 * the padding needed for alignment, so the rebuilt clone must be aligned here
 * before it is signed. apksig preserves existing alignment, so aligning first
 * and signing afterwards produces a valid, installable APK.
 *
 * Alignment is achieved by padding each STORED entry's "extra" field so that
 * the entry's data starts on the required boundary. Compressed (DEFLATED)
 * entries do not need alignment and are copied through.
 */
object ZipAligner {

    private const val TAG = "ZipAligner"

    /** Local file header is a fixed 30 bytes before the name + extra field. */
    private const val LOCAL_HEADER_SIZE = 30L

    /** Default alignment for uncompressed entries (e.g. resources.arsc). */
    private const val DEFAULT_ALIGNMENT = 4

    /** Page alignment for uncompressed native libraries. */
    private const val SO_ALIGNMENT = 4096

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    /**
     * Produce an aligned copy of [inputPath] at [outputPath].
     */
    fun align(inputPath: String, outputPath: String): String {
        val output = File(outputPath)
        if (output.exists()) output.delete()

        ZipFile(File(inputPath)).use { zin ->
            val counting = CountingOutputStream(FileOutputStream(output))
            ZipOutputStream(counting).use { zout ->
                val entries = zin.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val newEntry = ZipEntry(entry.name)
                    newEntry.time = entry.time

                    if (entry.method == ZipEntry.STORED) {
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = entry.size
                        newEntry.compressedSize = entry.compressedSize
                        newEntry.crc = entry.crc

                        val alignment =
                            if (entry.name.endsWith(".so")) SO_ALIGNMENT else DEFAULT_ALIGNMENT
                        val nameLen = entry.name.toByteArray(Charsets.UTF_8).size
                        val dataStartNoExtra = counting.count + LOCAL_HEADER_SIZE + nameLen
                        val pad = ((alignment - (dataStartNoExtra % alignment)) % alignment).toInt()
                        if (pad > 0) newEntry.extra = ByteArray(pad)
                    } else {
                        newEntry.method = ZipEntry.DEFLATED
                    }

                    zout.putNextEntry(newEntry)
                    zin.getInputStream(entry).use { it.copyTo(zout, 65536) }
                    zout.closeEntry()
                }
            }
        }

        Log.d(TAG, "Aligned APK -> ${output.absolutePath} (${output.length() / 1024}KB)")
        return output.absolutePath
    }
}
