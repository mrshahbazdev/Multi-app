package com.appcloner.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Main bridge between Flutter and native Android code.
 * Handles all MethodChannel communication.
 */
class NativeBridge(
    private val context: Context,
    flutterEngine: FlutterEngine
) : MethodChannel.MethodCallHandler {

    companion object {
        const val CHANNEL_NAME = "com.appcloner/native"
        private const val TAG = "NativeBridge"
    }

    private val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appScanner = AppScanner(context)
    private val apkExtractor = ApkExtractor(context)
    private val apkModifier = ApkModifier(context)
    private val apkSigner = ApkSigner(context)
    private val cloneInstaller = CloneInstaller(context)
    private val splitHandler = SplitApkHandler(context)
    private val stealthPatcher = StealthPatcher(context)
    private val deviceSpoofing = DeviceSpoofing(context)
    private val gmsHelper = GmsHelper(context)

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getInstalledApps" -> {
                val includeSystem = call.argument<Boolean>("includeSystemApps") ?: false
                Thread {
                    try {
                        val apps = appScanner.getInstalledApps(includeSystem)
                        mainHandler.post { result.success(apps) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to scan apps", e)
                        mainHandler.post { result.error("SCAN_ERROR", e.message, null) }
                    }
                }.start()
            }

            "getAppIcon" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                try {
                    val icon = appScanner.getAppIcon(packageName)
                    result.success(icon)
                } catch (e: Exception) {
                    result.error("ICON_ERROR", e.message, null)
                }
            }

            "getSplitInfo" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                Thread {
                    try {
                        val info = splitHandler.getSplitSummary(packageName)
                        mainHandler.post { result.success(info) }
                    } catch (e: Exception) {
                        mainHandler.post { result.error("SPLIT_INFO_ERROR", e.message, null) }
                    }
                }.start()
            }

            "cloneApp" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                val cloneName = call.argument<String>("cloneName") ?: ""
                val cloneIndex = call.argument<Int>("cloneIndex") ?: 1

                Thread {
                    try {
                        val clonePackage = performClone(packageName, cloneName, cloneIndex)
                        mainHandler.post { result.success(clonePackage) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Clone failed for $packageName", e)
                        mainHandler.post { result.error("CLONE_ERROR", e.message, e.stackTraceToString()) }
                    }
                }.start()
            }

            "installApk" -> {
                val apkPath = call.argument<String>("apkPath") ?: ""
                try {
                    cloneInstaller.installApk(apkPath)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("INSTALL_ERROR", e.message, null)
                }
            }

            "uninstallApp" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                try {
                    cloneInstaller.uninstallApp(packageName)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("UNINSTALL_ERROR", e.message, null)
                }
            }

            "launchApp" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        context.startActivity(intent)
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                } catch (e: Exception) {
                    result.error("LAUNCH_ERROR", e.message, null)
                }
            }

            "isAppInstalled" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                try {
                    context.packageManager.getPackageInfo(packageName, 0)
                    result.success(true)
                } catch (e: Exception) {
                    result.success(false)
                }
            }

            "canInstallPackages" -> {
                result.success(cloneInstaller.canInstallPackages())
            }

            "requestInstallPermission" -> {
                cloneInstaller.requestInstallPermission()
                result.success(true)
            }

            "cleanupCache" -> {
                Thread {
                    try {
                        apkExtractor.cleanupAll()
                        cloneInstaller.cleanupSessions()
                        mainHandler.post { result.success(true) }
                    } catch (e: Exception) {
                        mainHandler.post { result.error("CLEANUP_ERROR", e.message, null) }
                    }
                }.start()
            }

            "getStorageInfo" -> {
                try {
                    val cacheDir = java.io.File(context.cacheDir, "clone_work")
                    val cacheSize = if (cacheDir.exists()) getDirSize(cacheDir) else 0L
                    val info = mapOf(
                        "cacheSizeBytes" to cacheSize,
                        "cacheSizeFormatted" to formatSize(cacheSize)
                    )
                    result.success(info)
                } catch (e: Exception) {
                    result.error("STORAGE_ERROR", e.message, null)
                }
            }

            "applyStealthPatches" -> {
                val apkPath = call.argument<String>("apkPath") ?: ""
                val originalPackage = call.argument<String>("originalPackage") ?: ""
                val configMap = call.argument<Map<String, Any>>("config") ?: emptyMap()

                Thread {
                    try {
                        val config = StealthPatcher.StealthConfig(
                            spoofSignature = configMap["spoofSignature"] as? Boolean ?: true,
                            randomizePackageName = configMap["randomizePackageName"] as? Boolean ?: false,
                            removeDebugFlags = configMap["removeDebugFlags"] as? Boolean ?: true,
                            patchNativeLibs = configMap["patchNativeLibs"] as? Boolean ?: false,
                            customPackagePrefix = configMap["customPackagePrefix"] as? String
                        )
                        val patchedPath = stealthPatcher.applyStealthPatches(
                            apkPath, originalPackage, config
                        ) { status, progress ->
                            sendProgress(status, progress)
                        }
                        mainHandler.post { result.success(patchedPath) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Stealth patch failed", e)
                        mainHandler.post { result.error("STEALTH_ERROR", e.message, null) }
                    }
                }.start()
            }

            "generateDeviceProfile" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                val cloneIndex = call.argument<Int>("cloneIndex") ?: 1

                try {
                    val profile = deviceSpoofing.generateProfile(packageName, cloneIndex)
                    result.success(profile.toMap())
                } catch (e: Exception) {
                    result.error("PROFILE_ERROR", e.message, null)
                }
            }

            "generateStealthPackageName" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                val cloneIndex = call.argument<Int>("cloneIndex") ?: 1
                val configMap = call.argument<Map<String, Any>>("config") ?: emptyMap()

                try {
                    val config = StealthPatcher.StealthConfig(
                        randomizePackageName = configMap["randomizePackageName"] as? Boolean ?: false,
                        customPackagePrefix = configMap["customPackagePrefix"] as? String
                    )
                    val stealthName = stealthPatcher.generateStealthPackageName(
                        packageName, cloneIndex, config
                    )
                    result.success(stealthName)
                } catch (e: Exception) {
                    result.error("STEALTH_NAME_ERROR", e.message, null)
                }
            }

            "getStealthInfo" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                try {
                    val isInstalled = try {
                        context.packageManager.getPackageInfo(packageName, 0)
                        true
                    } catch (_: Exception) { false }

                    val info = mapOf(
                        "isInstalled" to isInstalled,
                        "hasStealthConfig" to true,
                        "packageName" to packageName
                    )
                    result.success(info)
                } catch (e: Exception) {
                    result.error("STEALTH_INFO_ERROR", e.message, null)
                }
            }

            "getGmsStatus" -> {
                try {
                    result.success(gmsHelper.getGmsStatus())
                } catch (e: Exception) {
                    result.error("GMS_STATUS_ERROR", e.message, null)
                }
            }

            "appUsesGms" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                Thread {
                    try {
                        val info = gmsHelper.appUsesGms(packageName)
                        mainHandler.post { result.success(info) }
                    } catch (e: Exception) {
                        mainHandler.post { result.error("GMS_USAGE_ERROR", e.message, null) }
                    }
                }.start()
            }

            else -> result.notImplemented()
        }
    }

    /**
     * Perform the full clone operation.
     * Handles both single and split APK apps.
     */
    private fun performClone(
        packageName: String,
        cloneName: String,
        cloneIndex: Int
    ): String {
        val clonePackage = "com.clone$cloneIndex.${packageName.replace(".", "_")}"
        val isSplit = splitHandler.isSplitApk(packageName)

        Log.d(TAG, "Cloning $packageName -> $clonePackage (split=$isSplit)")

        // Step 1: Extract
        sendProgress("Extracting APK${if (isSplit) "s" else ""}...", 0.05)
        val extraction = apkExtractor.extract(packageName) { status, progress ->
            sendProgress(status, 0.05 + progress * 0.20)
        }

        val apkToModify: String

        if (isSplit) {
            // Step 1.5: Merge split APKs into a single APK
            val splitCount = extraction.splitPaths.values.sumOf { it.size }
            sendProgress("Merging $splitCount split APKs...", 0.25)

            val mergedPath = "${apkExtractor.getWorkDir()}/$packageName/merged.apk"
            apkToModify = splitHandler.mergeSplitsToSingle(
                extraction.splitPaths,
                mergedPath
            ) { status, progress ->
                sendProgress(status, 0.25 + progress * 0.15)
            }

            Log.d(TAG, "Merged ${splitCount} splits into single APK: $apkToModify")
        } else {
            apkToModify = extraction.basePath
        }

        // Step 2: Modify package name
        sendProgress("Modifying package name...", 0.45)
        val modifiedApk = apkModifier.modifyApk(
            apkPath = apkToModify,
            originalPackage = packageName,
            newPackage = clonePackage,
            newAppName = cloneName
        )
        Log.d(TAG, "Modified APK: $modifiedApk")

        // Step 2.5: Apply stealth patches
        sendProgress("Applying stealth patches...", 0.50)
        val stealthConfig = StealthPatcher.StealthConfig(
            spoofSignature = true,
            removeDebugFlags = true
        )
        stealthPatcher.applyStealthPatches(modifiedApk, packageName, stealthConfig)

        // Step 2.6: Inject device profile
        sendProgress("Generating device profile...", 0.55)
        val profile = deviceSpoofing.generateProfile(packageName, cloneIndex)
        deviceSpoofing.injectProfile(modifiedApk, profile)
        Log.d(TAG, "Injected device profile for clone $cloneIndex")

        // Step 3: Sign
        sendProgress("Signing APK...", 0.65)
        val signedApk = apkSigner.signApk(modifiedApk)
        Log.d(TAG, "Signed APK: $signedApk")

        // Step 4: Install
        sendProgress("Installing clone...", 0.85)
        cloneInstaller.installApk(signedApk)

        sendProgress("Waiting for install confirmation...", 0.95)

        // Cleanup temp files
        Thread {
            Thread.sleep(5000)
            apkExtractor.cleanup(packageName)
        }.start()

        return clonePackage
    }

    private fun sendProgress(status: String, progress: Double) {
        Log.d(TAG, "Progress: $status (${"%.0f".format(progress * 100)}%)")
        mainHandler.post {
            channel.invokeMethod("onCloneProgress", mapOf(
                "status" to status,
                "progress" to progress
            ))
        }
    }

    private fun getDirSize(dir: java.io.File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    }
}
