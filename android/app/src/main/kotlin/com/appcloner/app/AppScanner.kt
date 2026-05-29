package com.appcloner.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Scans installed apps on the device using PackageManager API.
 */
class AppScanner(private val context: Context) {

    /**
     * Get list of all installed apps with their info.
     * Returns a list of Maps suitable for MethodChannel.
     */
    fun getInstalledApps(includeSystemApps: Boolean): List<Map<String, Any?>> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        val result = mutableListOf<Map<String, Any?>>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            // Skip system apps unless requested
            if (isSystemApp && !includeSystemApps) continue

            // Skip our own app
            if (pkg.packageName == context.packageName) continue

            val apkFile = File(appInfo.sourceDir)
            val splitPaths = appInfo.splitSourceDirs?.toList()

            // Get app icon as bytes
            val iconBytes = try {
                val drawable = pm.getApplicationIcon(appInfo)
                drawableToBytes(drawable)
            } catch (e: Exception) {
                null
            }

            result.add(mapOf(
                "appName" to (pm.getApplicationLabel(appInfo)?.toString() ?: pkg.packageName),
                "packageName" to pkg.packageName,
                "versionName" to (pkg.versionName ?: ""),
                "versionCode" to pkg.longVersionCode.toInt(),
                "apkPath" to appInfo.sourceDir,
                "isSystemApp" to isSystemApp,
                "apkSizeBytes" to apkFile.length().toInt(),
                "iconBytes" to iconBytes,
                "splitApkPaths" to splitPaths
            ))
        }

        return result.sortedBy { (it["appName"] as String).lowercase() }
    }

    /**
     * Get app icon as byte array for a specific package.
     */
    fun getAppIcon(packageName: String): ByteArray? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBytes(drawable)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert a Drawable to PNG byte array.
     */
    private fun drawableToBytes(drawable: Drawable): ByteArray {
        val bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
