package com.appcloner.app

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Lightweight Google Play Services (GMS) detection helper.
 *
 * Intentionally avoids depending on the heavyweight `play-services-base`
 * library — we only need to know whether GMS is present/usable on the device
 * and whether a target app relies on it (so we can warn the user that a clone
 * will need a separate Google account for push notifications / login).
 */
class GmsHelper(private val context: Context) {

    companion object {
        private const val TAG = "GmsHelper"
        private const val GMS_PACKAGE = "com.google.android.gms"
        private const val VENDING_PACKAGE = "com.android.vending"

        // Markers that indicate an app depends on Google Play Services.
        private val GMS_LIBRARY_MARKERS = listOf(
            "com.google.android.gms",
            "com.google.firebase",
        )
    }

    /** Device-wide GMS availability summary. */
    fun getGmsStatus(): Map<String, Any> {
        val pm = context.packageManager
        var installed = false
        var enabled = false
        var versionName = ""
        var versionCode = 0L

        try {
            val info = pm.getPackageInfo(GMS_PACKAGE, 0)
            installed = true
            versionName = info.versionName ?: ""
            versionCode = packageVersionCode(info)
            enabled = try {
                pm.getApplicationInfo(GMS_PACKAGE, 0).enabled
            } catch (_: Exception) {
                true
            }
        } catch (_: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Google Play Services not installed on this device")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query GMS status", e)
        }

        val playStoreInstalled = try {
            pm.getPackageInfo(VENDING_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

        val available = installed && enabled
        return mapOf(
            "available" to available,
            "installed" to installed,
            "enabled" to enabled,
            "playStoreInstalled" to playStoreInstalled,
            "versionName" to versionName,
            "versionCode" to versionCode,
            "statusText" to statusText(installed, enabled),
        )
    }

    /**
     * Whether [packageName] appears to depend on Google Play Services.
     * Checks shared libraries, services, and GMS metadata declared by the app.
     */
    fun appUsesGms(packageName: String): Map<String, Any> {
        val pm = context.packageManager
        var usesGms = false
        val reasons = mutableListOf<String>()

        try {
            val flags = PackageManager.GET_SERVICES or
                PackageManager.GET_META_DATA or
                PackageManager.GET_SHARED_LIBRARY_FILES
            val pkg = pm.getPackageInfo(packageName, flags)

            // 1. Shared libraries (uses-library) referencing GMS.
            pkg.applicationInfo?.sharedLibraryFiles?.forEach { lib ->
                if (GMS_LIBRARY_MARKERS.any { lib.contains(it) }) {
                    usesGms = true
                    reasons.add("uses GMS shared library")
                }
            }

            // 2. Declared services from the GMS/Firebase namespace.
            pkg.services?.forEach { service ->
                val name = service.name ?: ""
                if (GMS_LIBRARY_MARKERS.any { name.startsWith(it) }) {
                    usesGms = true
                    reasons.add("declares GMS service")
                }
            }

            // 3. GMS version metadata embedded by the Play Services SDK.
            val metaData = pkg.applicationInfo?.metaData
            if (metaData != null) {
                if (metaData.containsKey("com.google.android.gms.version") ||
                    metaData.keySet().any { it.startsWith("com.google.firebase") }
                ) {
                    usesGms = true
                    reasons.add("embeds GMS version metadata")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect $packageName for GMS usage", e)
        }

        return mapOf(
            "packageName" to packageName,
            "usesGms" to usesGms,
            "reasons" to reasons.distinct(),
        )
    }

    private fun statusText(installed: Boolean, enabled: Boolean): String = when {
        !installed -> "Not installed"
        !enabled -> "Disabled"
        else -> "Available"
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: android.content.pm.PackageInfo): Long {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }
}
