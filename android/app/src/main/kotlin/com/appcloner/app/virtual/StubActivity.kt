package com.appcloner.app.virtual

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * The StubActivity is a transparent Host activity declared in the AndroidManifest.
 * Since Android doesn't allow launching uninstalled Activities, we launch this Stub
 * and trick the OS. Inside onCreate, we load the real cloned Activity dynamically.
 */
class StubActivity : Activity() {
    companion object {
        private const val TAG = "StubActivity"
        const val EXTRA_TARGET_ACTIVITY = "virtual_target_activity"
        const val EXTRA_TARGET_PACKAGE = "virtual_target_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetActivity = intent.getStringExtra(EXTRA_TARGET_ACTIVITY)
        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        
        if (targetActivity == null || targetPackage == null) {
            Log.e(TAG, "Failed to launch StubActivity: Target is null.")
            finish()
            return
        }

        Log.d(TAG, "StubActivity hijacked! Loading target: $targetActivity from $targetPackage")
        
        // Setup Virtual Environment
        VirtualEnvironment.createSandbox(this, targetPackage)
        VirtualEnvironment.initializeNativeEngine()
        
        // Setup MicroG
        MicroGManager.initializeMicroG(this, targetPackage)
        
        // TODO: In Phase 3, we will use Java Reflection to instantiate the 
        // targetActivity class from the custom ClassLoader and attach it to this window.
    }
}
