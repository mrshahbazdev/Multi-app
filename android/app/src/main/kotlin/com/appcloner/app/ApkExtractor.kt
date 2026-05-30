package com.appcloner.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Extracts APK files from installed apps.
 * Handles both single APKs and split APKs (App Bundles).
 */
class ApkExtractor(private val context: Context) {

    companion object {
        private const val TAG = "ApkExtractor"
    }

    val workDir: File
        get() = File(context.filesDir, "clone_work").also { it.mkdirs() }

    private val splitHandler = SplitApkHandler(context)

    data class ExtractionResult(
        val basePath: String,
        val isSplit: Boolean,
        val splitPaths: Map<SplitApkHandler.SplitType, List<String>>,
        val totalSizeBytes: Long
    )

    /**
     * Extract APK(s) for a given package.
     * Automatically detects split APKs and extracts all splits.
     */
    fun extract(
        packageName: String,
        onProgress: ((String, Double) -> Unit)? = null
    ): ExtractionResult {
        val isSplit = splitHandler.isSplitApk(packageName)

        return if (isSplit) {
            extractSplitApk(packageName, onProgress)
        } else {
            extractSingleApk(packageName, onProgress)
        }
    }

    /**
     * Extract a single (non-split) APK.
     */
    private fun extractSingleApk(
        packageName: String,
        onProgress: ((String, Double) -> Unit)? = null
    ): ExtractionResult {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val sourceApk = File(appInfo.sourceDir)

        val outputDir = File(workDir, packageName).also {
            it.deleteRecursively()
            it.mkdirs()
        }

        val outputApk = File(outputDir, "base.apk")

        onProgress?.invoke("Extracting APK...", 0.2)
        Log.d(TAG, "Extracting single APK: ${sourceApk.absolutePath} (${sourceApk.length() / 1024}KB)")

        copyFile(sourceApk, outputApk, onProgress, 0.2, 0.8)

        onProgress?.invoke("Extraction complete", 1.0)

        return ExtractionResult(
            basePath = outputApk.absolutePath,
            isSplit = false,
            splitPaths = mapOf(SplitApkHandler.SplitType.BASE to listOf(outputApk.absolutePath)),
            totalSizeBytes = outputApk.length()
        )
    }

    /**
     * Extract all split APKs for an app.
     */
    private fun extractSplitApk(
        packageName: String,
        onProgress: ((String, Double) -> Unit)? = null
    ): ExtractionResult {
        val splitInfo = splitHandler.getSplitInfo(packageName)
        val totalSize = splitInfo.sumOf { it.sizeBytes }

        Log.d(TAG, "Extracting split APK: $packageName (${splitInfo.size} splits, ${totalSize / 1024}KB total)")

        onProgress?.invoke("Extracting ${splitInfo.size} split APKs...", 0.1)

        val splitPaths = splitHandler.extractAllSplits(packageName, workDir)

        // Find the base APK path
        val basePath = splitPaths[SplitApkHandler.SplitType.BASE]?.firstOrNull()
            ?: throw IllegalStateException("No base APK found for $packageName")

        // Report what was extracted
        for ((type, paths) in splitPaths) {
            Log.d(TAG, "  $type: ${paths.size} file(s)")
        }

        onProgress?.invoke("All splits extracted", 1.0)

        return ExtractionResult(
            basePath = basePath,
            isSplit = true,
            splitPaths = splitPaths,
            totalSizeBytes = totalSize
        )
    }

    /**
     * Copy a file with progress reporting.
     */
    private fun copyFile(
        source: File,
        dest: File,
        onProgress: ((String, Double) -> Unit)?,
        startProgress: Double,
        endProgress: Double
    ) {
        val totalBytes = source.length()
        var copiedBytes = 0L
        val buffer = ByteArray(16384)

        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    copiedBytes += bytesRead
                    val fraction = copiedBytes.toDouble() / totalBytes
                    val progress = startProgress + (endProgress - startProgress) * fraction
                    onProgress?.invoke("Extracting... ${(fraction * 100).toInt()}%", progress)
                }
            }
        }
    }

    fun cleanup(packageName: String) {
        File(workDir, packageName).deleteRecursively()
    }

    fun cleanupAll() {
        workDir.deleteRecursively()
    }

}
