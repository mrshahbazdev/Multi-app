package com.appcloner.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

/**
 * Handles installation and uninstallation of cloned APKs.
 *
 * Two methods:
 * 1. Intent-based (ACTION_VIEW) — simple but requires user confirmation
 * 2. PackageInstaller API — needed for split APKs, also requires user confirmation
 */
class CloneInstaller(private val context: Context) {

    /**
     * Install a single APK file.
     * Uses FileProvider for Android 7+ and PackageInstaller for split APKs.
     */
    fun installApk(apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) throw IllegalArgumentException("APK file not found: $apkPath")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            installWithPackageInstaller(listOf(file))
        } else {
            installWithIntent(file)
        }
    }

    /**
     * Install split APKs using PackageInstaller.
     */
    fun installSplitApks(apkPaths: List<String>) {
        val files = apkPaths.map { File(it) }
        for (f in files) {
            if (!f.exists()) throw IllegalArgumentException("APK file not found: ${f.absolutePath}")
        }
        installWithPackageInstaller(files)
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
     * Install using ACTION_VIEW intent (simple method).
     */
    private fun installWithIntent(apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Install using PackageInstaller API (supports split APKs).
     */
    private fun installWithPackageInstaller(apkFiles: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        // Calculate total size
        val totalSize = apkFiles.sumOf { it.length() }
        params.setSize(totalSize)

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        try {
            // Write each APK to the session
            for ((index, file) in apkFiles.withIndex()) {
                session.openWrite("split_$index.apk", 0, file.length()).use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    session.fsync(outputStream)
                }
            }

            // Create a pending intent for the install result
            val intent = Intent(context, InstallReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // Commit the session
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }
}
