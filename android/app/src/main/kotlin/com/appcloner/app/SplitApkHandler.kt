package com.appcloner.app

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Handles split APK (App Bundle) operations.
 *
 * Modern Android apps (WhatsApp, Instagram, etc.) use App Bundles which install
 * as multiple split APKs:
 *   base.apk — main code + manifest
 *   config.arm64_v8a.apk — native libraries for arm64
 *   config.xxhdpi.apk — resources for screen density
 *   config.en.apk — language resources
 *
 * For cloning, we need to:
 * 1. Detect if an app uses split APKs
 * 2. Extract all splits
 * 3. Classify each split (base, ABI, density, locale)
 * 4. Modify only the base APK (package name change)
 * 5. Install all splits together via PackageInstaller session
 */
class SplitApkHandler(private val context: Context) {

    companion object {
        private const val TAG = "SplitApkHandler"
    }

    data class SplitApkInfo(
        val path: String,
        val fileName: String,
        val type: SplitType,
        val sizeBytes: Long
    )

    enum class SplitType {
        BASE,
        ABI,         // config.arm64_v8a, config.armeabi_v7a, config.x86, config.x86_64
        DENSITY,     // config.xxhdpi, config.xxxhdpi, config.xhdpi, etc.
        LOCALE,      // config.en, config.ur, config.hi, etc.
        FEATURE,     // dynamic feature modules
        UNKNOWN
    }

    /**
     * Check if an app uses split APKs.
     */
    fun isSplitApk(packageName: String): Boolean {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val splitDirs = appInfo.splitSourceDirs
        return splitDirs != null && splitDirs.isNotEmpty()
    }

    /**
     * Get detailed info about all split APKs for an app.
     */
    fun getSplitInfo(packageName: String): List<SplitApkInfo> {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val result = mutableListOf<SplitApkInfo>()

        // Base APK
        val baseFile = File(appInfo.sourceDir)
        result.add(SplitApkInfo(
            path = appInfo.sourceDir,
            fileName = baseFile.name,
            type = SplitType.BASE,
            sizeBytes = baseFile.length()
        ))

        // Split APKs
        appInfo.splitSourceDirs?.forEach { splitPath ->
            val file = File(splitPath)
            result.add(SplitApkInfo(
                path = splitPath,
                fileName = file.name,
                type = classifySplit(file.name),
                sizeBytes = file.length()
            ))
        }

        return result
    }

    /**
     * Extract all split APKs to our work directory.
     * Returns a map of SplitType -> extracted file path.
     */
    fun extractAllSplits(packageName: String, workDir: File): Map<SplitType, List<String>> {
        val splitInfo = getSplitInfo(packageName)
        val outputDir = File(workDir, packageName).also {
            it.deleteRecursively()
            it.mkdirs()
        }

        val result = mutableMapOf<SplitType, MutableList<String>>()

        for (split in splitInfo) {
            val sourceFile = File(split.path)
            val outputFile = File(outputDir, split.fileName)

            Log.d(TAG, "Extracting ${split.type}: ${split.fileName} (${split.sizeBytes / 1024}KB)")

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output, bufferSize = 16384)
                }
            }

            result.getOrPut(split.type) { mutableListOf() }.add(outputFile.absolutePath)
        }

        return result
    }

    /**
     * Get the total size of all APKs for an app (base + splits).
     */
    fun getTotalApkSize(packageName: String): Long {
        return getSplitInfo(packageName).sumOf { it.sizeBytes }
    }

    /**
     * Get a human-readable summary of splits.
     */
    fun getSplitSummary(packageName: String): Map<String, Any> {
        val splits = getSplitInfo(packageName)
        val totalSize = splits.sumOf { it.sizeBytes }

        return mapOf(
            "isSplit" to (splits.size > 1),
            "splitCount" to splits.size,
            "totalSizeBytes" to totalSize,
            "totalSizeFormatted" to formatSize(totalSize),
            "splits" to splits.map { split ->
                mapOf(
                    "fileName" to split.fileName,
                    "type" to split.type.name,
                    "sizeBytes" to split.sizeBytes,
                    "sizeFormatted" to formatSize(split.sizeBytes)
                )
            }
        )
    }

    /**
     * Merge split APKs into a single universal APK.
     * This is useful for apps that don't work well with split install.
     *
     * Process:
     * 1. Start with base.apk as the foundation
     * 2. Copy native libs from ABI split into lib/ directory
     * 3. Copy density resources from density split
     * 4. Copy locale resources from locale split
     */
    fun mergeSplitsToSingle(
        splitPaths: Map<SplitType, List<String>>,
        outputPath: String,
        onProgress: ((String, Double) -> Unit)? = null
    ): String {
        val basePaths = splitPaths[SplitType.BASE]
            ?: throw IllegalStateException("No base APK found")
        val basePath = basePaths.first()

        onProgress?.invoke("Merging base APK...", 0.1)

        val outputFile = File(outputPath)
        val baseZip = ZipFile(File(basePath))

        ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
            val addedEntries = mutableSetOf<String>()

            // First, copy all entries from base APK
            val baseEntries = baseZip.entries()
            while (baseEntries.hasMoreElements()) {
                val entry = baseEntries.nextElement()
                if (entry.name.startsWith("META-INF/")) continue

                val newEntry = ZipEntry(entry.name)
                zipOut.putNextEntry(newEntry)
                zipOut.write(baseZip.getInputStream(entry).readBytes())
                zipOut.closeEntry()
                addedEntries.add(entry.name)
            }
            baseZip.close()

            onProgress?.invoke("Merging native libraries...", 0.3)

            // Merge ABI splits (native libraries)
            splitPaths[SplitType.ABI]?.forEach { abiPath ->
                mergeEntriesFrom(abiPath, zipOut, addedEntries)
            }

            onProgress?.invoke("Merging density resources...", 0.5)

            // Merge density splits
            splitPaths[SplitType.DENSITY]?.forEach { densityPath ->
                mergeEntriesFrom(densityPath, zipOut, addedEntries)
            }

            onProgress?.invoke("Merging locale resources...", 0.7)

            // Merge locale splits
            splitPaths[SplitType.LOCALE]?.forEach { localePath ->
                mergeEntriesFrom(localePath, zipOut, addedEntries)
            }

            // Merge feature splits
            splitPaths[SplitType.FEATURE]?.forEach { featurePath ->
                mergeEntriesFrom(featurePath, zipOut, addedEntries)
            }

            // Merge unknown splits
            splitPaths[SplitType.UNKNOWN]?.forEach { unknownPath ->
                mergeEntriesFrom(unknownPath, zipOut, addedEntries)
            }
        }

        onProgress?.invoke("Merge complete", 1.0)
        Log.d(TAG, "Merged APK size: ${outputFile.length() / 1024}KB")

        return outputFile.absolutePath
    }

    /**
     * Merge entries from a split APK into the output ZIP.
     * Skips AndroidManifest.xml (we use the base one) and META-INF.
     * Skips entries already added from base or previous splits.
     */
    private fun mergeEntriesFrom(
        splitPath: String,
        zipOut: ZipOutputStream,
        addedEntries: MutableSet<String>
    ) {
        val splitZip = ZipFile(File(splitPath))
        val entries = splitZip.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()

            // Skip manifest and signatures — use base APK's
            if (entry.name == "AndroidManifest.xml") continue
            if (entry.name.startsWith("META-INF/")) continue

            // Skip if already added from base or previous split
            if (entry.name in addedEntries) {
                Log.d(TAG, "Skipping duplicate entry: ${entry.name}")
                continue
            }

            val newEntry = ZipEntry(entry.name)
            zipOut.putNextEntry(newEntry)
            zipOut.write(splitZip.getInputStream(entry).readBytes())
            zipOut.closeEntry()
            addedEntries.add(entry.name)
        }

        splitZip.close()
    }

    /**
     * Classify a split APK by its filename.
     */
    private fun classifySplit(fileName: String): SplitType {
        val name = fileName.lowercase().removeSuffix(".apk")

        // ABI splits
        val abiPatterns = listOf(
            "arm64_v8a", "arm64-v8a", "armeabi_v7a", "armeabi-v7a",
            "x86_64", "x86", "mips", "mips64"
        )
        if (abiPatterns.any { name.contains(it) }) return SplitType.ABI

        // Density splits
        val densityPatterns = listOf(
            "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi",
            "tvdpi", "nodpi", "anydpi"
        )
        if (densityPatterns.any { name.contains(it) }) return SplitType.DENSITY

        // Locale splits (typically config.xx or config.xx_XX)
        val localeRegex = Regex("""config\.([a-z]{2}(_[A-Z]{2})?)""")
        if (localeRegex.containsMatchIn(name)) return SplitType.LOCALE

        // Feature splits (typically split_config.feature_name)
        if (name.contains("feature") || name.startsWith("split_")) return SplitType.FEATURE

        return SplitType.UNKNOWN
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        }
    }
}
