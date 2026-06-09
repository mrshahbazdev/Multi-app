package com.appcloner.app

import android.content.Context
import android.util.Log
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Patches cloned APKs for stealth and anti-detection.
 *
 * Features:
 * 1. Signature spoofing — inject original app signature into the clone
 * 2. Package name randomization — use harder-to-detect package names
 * 3. Manifest hardening — remove debuggable flags, add detection evasion
 * 4. Native library patching — modify .so files that check package/signature
 */
class StealthPatcher(private val context: Context) {

    companion object {
        private const val TAG = "StealthPatcher"
    }

    /**
     * Configuration for stealth features.
     */
    data class StealthConfig(
        val spoofSignature: Boolean = true,
        val randomizePackageName: Boolean = false,
        val removeDebugFlags: Boolean = true,
        val patchNativeLibs: Boolean = false,
        val customPackagePrefix: String? = null
    )

    /**
     * Generate a stealth-friendly package name.
     * Instead of "com.clone1.com_whatsapp" which is obviously a clone,
     * generate something like "com.social.messenger.alt" or use user prefix.
     */
    fun generateStealthPackageName(
        originalPackage: String,
        cloneIndex: Int,
        config: StealthConfig
    ): String {
        if (!config.randomizePackageName) {
            return "com.clone$cloneIndex.${originalPackage.replace(".", "_")}"
        }

        val prefix = config.customPackagePrefix ?: generateRandomPrefix()
        val suffix = generateShortHash(originalPackage, cloneIndex)

        return "$prefix.$suffix"
    }

    /**
     * Generate a random but realistic-looking package prefix.
     */
    private fun generateRandomPrefix(): String {
        val prefixes = listOf(
            "com.app", "com.mobile", "org.tools", "io.util",
            "com.social", "net.client", "com.service", "app.helper"
        )
        val suffixes = listOf(
            "core", "plus", "pro", "lite", "fast", "smart",
            "hub", "link", "sync", "next", "go", "one"
        )
        return "${prefixes.random()}.${suffixes.random()}"
    }

    /**
     * Generate a short deterministic hash suffix for the package name.
     */
    private fun generateShortHash(packageName: String, index: Int): String {
        val input = "$packageName:$index"
        val hash = input.hashCode().and(0x7FFFFFFF)
        return "m${hash.toString(36).take(6)}"
    }

    /**
     * Get the original app's signing certificate.
     * Used to spoof signature verification in the clone.
     */
    fun getOriginalSignature(packageName: String): ByteArray? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )
            packageInfo.signatures?.firstOrNull()?.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get signature for $packageName", e)
            null
        }
    }

    /**
     * Apply stealth patches to a modified APK before signing.
     *
     * This modifies the APK to:
     * 1. Store original signature in assets (for runtime spoofing)
     * 2. Remove android:debuggable="true" if present
     * 3. Patch known detection strings in DEX files
     */
    fun applyStealthPatches(
        apkPath: String,
        originalPackage: String,
        config: StealthConfig,
        onProgress: ((String, Double) -> Unit)? = null
    ): String {
        val sourceApk = File(apkPath)
        val outputApk = File(sourceApk.parent, "stealth_patched.apk")

        onProgress?.invoke("Applying stealth patches...", 0.0)

        val originalSig = if (config.spoofSignature) {
            getOriginalSignature(originalPackage)
        } else null

        ZipFile(sourceApk).use { zipIn ->
            ZipOutputStream(FileOutputStream(outputApk)).use { zipOut ->
                val entries = zipIn.entries()
                val totalEntries = sourceApk.length() // approximate
                var processed = 0

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    processed++

                    // Skip META-INF (will be re-signed)
                    if (entry.name.startsWith("META-INF/")) continue

                    val newEntry = ZipEntry(entry.name)
                    if (entry.method == ZipEntry.STORED) {
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = entry.size
                        newEntry.compressedSize = entry.compressedSize
                        newEntry.crc = entry.crc
                    }

                    zipOut.putNextEntry(newEntry)

                    val data = zipIn.getInputStream(entry).readBytes()

                    when {
                        entry.name == "AndroidManifest.xml" -> {
                            // Manifest is already patched safely via string pool in ApkModifier
                            // Just write the data
                            zipOut.write(data)
                        }
                        entry.name.endsWith(".dex") && config.patchNativeLibs -> {
                            // Patch detection strings in DEX
                            val patched = patchDexStrings(data, originalPackage)
                            zipOut.write(patched)
                        }
                        else -> {
                            zipOut.write(data)
                        }
                    }

                    zipOut.closeEntry()
                }

                // Inject original signature as asset (for runtime spoofing)
                if (originalSig != null) {
                    val sigEntry = ZipEntry("assets/clone_meta/original_sig.bin")
                    zipOut.putNextEntry(sigEntry)
                    zipOut.write(originalSig)
                    zipOut.closeEntry()

                    Log.d(TAG, "Injected original signature (${originalSig.size} bytes)")
                }

                // Inject stealth config as asset
                val configEntry = ZipEntry("assets/clone_meta/stealth.conf")
                zipOut.putNextEntry(configEntry)
                val configData = buildStealthConfigData(config, originalPackage)
                zipOut.write(configData)
                zipOut.closeEntry()
            }
        }

        onProgress?.invoke("Stealth patches applied", 1.0)

        // Replace original with patched
        sourceApk.delete()
        outputApk.renameTo(sourceApk)

        return sourceApk.absolutePath
    }



    /**
     * Patch detection strings in DEX files.
     * Some apps check for clone-related package names or paths.
     *
     * This is a best-effort approach — advanced apps use native checks.
     */
    private fun patchDexStrings(dexData: ByteArray, originalPackage: String): ByteArray {
        // For safety, we don't modify DEX structure directly
        // Instead, we look for known detection strings and replace them
        // with benign alternatives of the same length

        val detectionStrings = mapOf(
            "com.clone" to "com.appli",    // Same length replacement
            "dual.space" to "dual.xxxxx",  // Pad to match
            "parallel.space" to "parallel.xxxxx"
        )

        var result = dexData

        for ((target, replacement) in detectionStrings) {
            val targetBytes = target.toByteArray(Charsets.UTF_8)
            val replBytes = replacement.toByteArray(Charsets.UTF_8).copyOf(targetBytes.size)

            result = replaceBytes(result, targetBytes, replBytes)
        }

        return result
    }

    /**
     * Replace byte sequence in data (first occurrence only for safety).
     */
    private fun replaceBytes(data: ByteArray, target: ByteArray, replacement: ByteArray): ByteArray {
        if (target.size != replacement.size) return data

        for (i in 0 until data.size - target.size) {
            var match = true
            for (j in target.indices) {
                if (data[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                val result = data.copyOf()
                System.arraycopy(replacement, 0, result, i, replacement.size)
                return result
            }
        }

        return data
    }

    /**
     * Build stealth config data to embed in the APK.
     */
    private fun buildStealthConfigData(config: StealthConfig, originalPackage: String): ByteArray {
        val sb = StringBuilder()
        sb.appendLine("# Clone Stealth Config")
        sb.appendLine("original_package=$originalPackage")
        sb.appendLine("spoof_signature=${config.spoofSignature}")
        sb.appendLine("randomize_package=${config.randomizePackageName}")
        sb.appendLine("remove_debug=${config.removeDebugFlags}")
        sb.appendLine("patch_native=${config.patchNativeLibs}")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}
