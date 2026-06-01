package com.appcloner.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast

/**
 * Receives results from PackageInstaller sessions.
 */
class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val sessionId = intent.getIntExtra("session_id", -1)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // User needs to confirm install (not a terminal state).
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.d(TAG, "Clone installed successfully (session $sessionId)")
                if (sessionId != -1) CloneInstaller.deliver(sessionId, status, message)
            }
            else -> {
                // Any other status is a terminal failure.
                Log.e(TAG, "Install failed: status=$status, message=$message (session $sessionId)")
                Toast.makeText(context, "Install failed: $message", Toast.LENGTH_LONG).show()
                if (sessionId != -1) CloneInstaller.deliver(sessionId, status, message)
            }
        }
    }

    companion object {
        private const val TAG = "InstallReceiver"
    }
}
