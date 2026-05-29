package com.appcloner.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

/**
 * Handles installation and uninstallation of cloned APKs.
 * Supports both single APK and split APK installation via PackageInstaller.
 */
class CloneInstaller(private val context: Context) {

    companion object {
        private const val TAG = "CloneInstaller"
    }

    /**
     * Install a single APK file.
     */
    fun installApk(apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) throw IllegalArgumentException("APK file not found: $apkPath")
        Log.d(TAG, "Installing single APK: $apkPath (${file.length() / 1024}KB)")
        installWithPackageInstaller(listOf(file))
    }

    /**
     * Install split APKs using PackageInstaller session.
     * This is the proper way to install App Bundles.
     *
     * @param apkPaths List of all APK paths (base + config splits)
     * @param onProgress Progress callback (fileName, progress 0.0-1.0)
     */
    fun installSplitApks(
        apkPaths: List<String>,
        onProgress: ((String, Double) -> Unit)? = null
    ) {
        val files = apkPaths.map { File(it) }
        for (f in files) {
            if (!f.exists()) throw IllegalArgumentException("APK file not found: ${f.absolutePath}")
        }

        Log.d(TAG, "Installing ${files.size} split APKs:")
        files.forEach { Log.d(TAG, "  ${it.name} (${it.length() / 1024}KB)") }

        installWithPackageInstaller(files, onProgress)
    }

    /**
     * Uninstall an app by package name.
     */
    fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Check if app has permission to install unknown apps.
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS, 0
            ) == 1
        }
    }

    /**
     * Open settings to allow installing unknown apps.
     */
    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Install using PackageInstaller API.
     * This supports both single and split APK installation.
     *
     * For split APKs, all splits must be written into a single session
     * before committing — Android will verify that they all belong together.
     */
    private fun installWithPackageInstaller(
        apkFiles: List<File>,
        onProgress: ((String, Double) -> Unit)? = null
    ) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        // Calculate total size across all APKs
        val totalSize = apkFiles.sumOf { it.length() }
        params.setSize(totalSize)

        // For Android 12+, set install reason
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        // Allow downgrade for testing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            params.setOriginatingUri(Uri.parse("package:${context.packageName}"))
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        Log.d(TAG, "Created install session $sessionId for ${apkFiles.size} APK(s), total ${totalSize / 1024}KB")

        try {
            var bytesWritten = 0L

            for ((index, file) in apkFiles.withIndex()) {
                val splitName = if (apkFiles.size == 1) {
                    "base.apk"
                } else {
                    file.name
                }

                Log.d(TAG, "Writing split $index: $splitName (${file.length() / 1024}KB)")
                onProgress?.invoke("Installing $splitName...", bytesWritten.toDouble() / totalSize)

                session.openWrite(splitName, 0, file.length()).use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        val buffer = ByteArray(16384)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            bytesWritten += read
                        }
                    }
                    session.fsync(outputStream)
                }
            }

            onProgress?.invoke("Committing install...", 0.95)

            // Create a pending intent for the install result
            val intent = Intent(context, InstallReceiver::class.java).apply {
                action = "com.appcloner.INSTALL_RESULT"
                putExtra("session_id", sessionId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            Log.d(TAG, "Committing session $sessionId")
            session.commit(pendingIntent.intentSender)

            onProgress?.invoke("Waiting for user confirmation...", 1.0)

        } catch (e: Exception) {
            Log.e(TAG, "Install failed, abandoning session $sessionId", e)
            session.abandon()
            throw e
        }
    }

    /**
     * Clean up any abandoned install sessions.
     */
    fun cleanupSessions() {
        val installer = context.packageManager.packageInstaller
        for (session in installer.mySessions) {
            try {
                installer.abandonSession(session.sessionId)
                Log.d(TAG, "Cleaned up abandoned session ${session.sessionId}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean session ${session.sessionId}", e)
            }
        }
    }
}
