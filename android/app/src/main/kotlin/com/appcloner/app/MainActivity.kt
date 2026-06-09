package com.appcloner.app

import android.os.Bundle
import android.util.Log
import com.appcloner.app.virtual.VirtualEnvironment
import com.appcloner.app.virtual.NetworkInterceptor
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    private lateinit var nativeBridge: NativeBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize native C++ engine
        try {
            VirtualEnvironment.initializeNativeEngine()
            Log.i("MultiApp", "C++ Virtual Engine initialized.")
        } catch (e: Exception) {
            Log.e("MultiApp", "Failed to init native engine", e)
        }
        
        // Install the pure-Kotlin network interceptor (undetectable by banking apps)
        VirtualEnvironment.initializeHooks()
        Log.i("MultiApp", "Network Interceptor started (Undetectable Mode).")
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // NativeBridge handles ALL existing MethodChannel methods
        // (getInstalledApps, cloneApp, uninstallApp, launchApp, etc.)
        nativeBridge = NativeBridge(this, flutterEngine)
    }
}
