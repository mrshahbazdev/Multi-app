package com.appcloner.app

import android.content.Context
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

    private val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
    private val appScanner = AppScanner(context)
    private val apkExtractor = ApkExtractor(context)
    private val apkModifier = ApkModifier(context)
    private val apkSigner = ApkSigner(context)
    private val cloneInstaller = CloneInstaller(context)

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
                        result.success(apps)
                    } catch (e: Exception) {
                        result.error("SCAN_ERROR", e.message, null)
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

            "cloneApp" -> {
                val packageName = call.argument<String>("packageName") ?: ""
                val cloneName = call.argument<String>("cloneName") ?: ""
                val cloneIndex = call.argument<Int>("cloneIndex") ?: 1

                Thread {
                    try {
                        // Step 1: Extract APK
                        sendProgress("Extracting APK...", 0.15)
                        val apkPath = apkExtractor.extractApk(packageName)

                        // Step 2: Modify package name
                        sendProgress("Modifying package...", 0.40)
                        val clonePackage = "com.clone$cloneIndex.${packageName.replace(".", "_")}"
                        val modifiedApk = apkModifier.modifyApk(
                            apkPath = apkPath,
                            originalPackage = packageName,
                            newPackage = clonePackage,
                            newAppName = cloneName
                        )

                        // Step 3: Sign APK
                        sendProgress("Signing APK...", 0.70)
                        val signedApk = apkSigner.signApk(modifiedApk)

                        // Step 4: Install
                        sendProgress("Installing clone...", 0.90)
                        cloneInstaller.installApk(signedApk)

                        sendProgress("Complete!", 1.0)
                        result.success(clonePackage)
                    } catch (e: Exception) {
                        result.error("CLONE_ERROR", e.message, null)
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

            else -> result.notImplemented()
        }
    }

    private fun sendProgress(status: String, progress: Double) {
        channel.invokeMethod("onCloneProgress", mapOf(
            "status" to status,
            "progress" to progress
        ))
    }

    companion object {
        const val CHANNEL_NAME = "com.appcloner/native"
    }
}
