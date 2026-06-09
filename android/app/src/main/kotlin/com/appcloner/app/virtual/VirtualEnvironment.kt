package com.appcloner.app.virtual

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages the isolated file system for cloned apps.
 * Instead of installing the app to /data/data/com.target.app,
 * we reroute its file access to our own private /virtual_data/ directory.
 */
object VirtualEnvironment {
    private const val TAG = "VirtualEnvironment"
    
    init {
        try {
            System.loadLibrary("virtual_engine")
            Log.i(TAG, "Native virtual_engine loaded successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native virtual_engine", e)
        }
    }

    // Initialize native engine (hooks, seccomp)
    external fun initializeNativeEngine()

    // Add a rule to intercept and mutate HTTP/HTTPS traffic
    fun addNetworkMutationRule(targetUrl: String, searchString: String, replaceString: String) {
        NetworkInterceptor.addMutationRule(targetUrl, searchString, replaceString)
    }
    
    // Call this when the app starts
    fun initializeHooks() {
        NetworkInterceptor.install()
    }
    
    /**
     * Initializes the sandbox directory for a cloned app.
     */
    fun createSandbox(context: Context, packageName: String): File {
        val rootDir = context.filesDir
        val virtualRoot = File(rootDir, "virtual_data")
        if (!virtualRoot.exists()) {
            virtualRoot.mkdirs()
        }
        
        val appSandbox = File(virtualRoot, packageName)
        if (!appSandbox.exists()) {
            appSandbox.mkdirs()
            
            // Create standard Android data directories
            File(appSandbox, "shared_prefs").mkdirs()
            File(appSandbox, "databases").mkdirs()
            File(appSandbox, "files").mkdirs()
            File(appSandbox, "cache").mkdirs()
            
            Log.d(TAG, "Created virtual sandbox for $packageName at ${appSandbox.absolutePath}")
        }
        
        return appSandbox
    }
    
    /**
     * Spoofs a Context to return our virtual directories instead of the real system ones.
     */
    fun spoofContext(baseContext: Context, packageName: String): Context {
        // In Phase 3, we will use Java Reflection / Proxy to hook ContextImpl
        // and return the VirtualEnvironment paths.
        Log.d(TAG, "Context spoofing prepared for $packageName")
        return baseContext
    }
}
