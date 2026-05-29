package com.appcloner.app

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Extracts APK files from installed apps.
 * Copies the APK to our app's private storage for modification.
 */
class ApkExtractor(private val context: Context) {

    private val workDir: File
        get() = File(context.filesDir, "clone_work").also { it.mkdirs() }

    /**
     * Extract APK for a given package name.
     * Returns the path to the extracted APK in our private storage.
     */
    fun extractApk(packageName: String): String {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val sourceApk = File(appInfo.sourceDir)

        // Create output directory for this clone operation
        val outputDir = File(workDir, packageName).also {
            it.deleteRecursively()
            it.mkdirs()
        }

        val outputApk = File(outputDir, "base.apk")

        // Copy APK to our storage
        FileInputStream(sourceApk).use { input ->
            FileOutputStream(outputApk).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }

        // Also copy split APKs if they exist
        appInfo.splitSourceDirs?.forEachIndexed { index, splitPath ->
            val splitFile = File(splitPath)
            val outputSplit = File(outputDir, "split_$index.apk")
            FileInputStream(splitFile).use { input ->
                FileOutputStream(outputSplit).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
        }

        return outputApk.absolutePath
    }

    /**
     * Get all split APK paths for an app if it uses App Bundles.
     */
    fun getSplitApks(packageName: String): List<String> {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        return appInfo.splitSourceDirs?.toList() ?: emptyList()
    }

    /**
     * Clean up work directory for a package.
     */
    fun cleanup(packageName: String) {
        File(workDir, packageName).deleteRecursively()
    }

    /**
     * Clean up all work directories.
     */
    fun cleanupAll() {
        workDir.deleteRecursively()
    }
}
