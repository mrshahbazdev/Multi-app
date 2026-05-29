import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:app_cloner/core/constants.dart';
import 'package:app_cloner/models/app_info.dart';

class NativeBridge {
  static const _channel = MethodChannel(AppConstants.channelName);

  /// Get all installed apps on the device
  static Future<List<AppInfo>> getInstalledApps({bool includeSystemApps = false}) async {
    final List<dynamic> result = await _channel.invokeMethod(
      AppConstants.methodGetApps,
      {'includeSystemApps': includeSystemApps},
    );
    return result.map((e) => AppInfo.fromMap(Map<dynamic, dynamic>.from(e))).toList();
  }

  /// Get app icon as bytes
  static Future<Uint8List?> getAppIcon(String packageName) async {
    final result = await _channel.invokeMethod(
      AppConstants.methodGetAppIcon,
      {'packageName': packageName},
    );
    if (result == null) return null;
    return Uint8List.fromList(List<int>.from(result));
  }

  /// Clone an app - returns clone package name
  /// [progressCallback] receives status updates: extracting, modifying, signing
  static Future<String> cloneApp({
    required String packageName,
    required String cloneName,
    required int cloneIndex,
    Function(String status, double progress)? progressCallback,
  }) async {
    // Set up event channel for progress updates
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onCloneProgress') {
        final args = call.arguments as Map;
        progressCallback?.call(
          args['status'] as String,
          (args['progress'] as num).toDouble(),
        );
      }
    });

    final result = await _channel.invokeMethod(
      AppConstants.methodCloneApp,
      {
        'packageName': packageName,
        'cloneName': cloneName,
        'cloneIndex': cloneIndex,
      },
    );

    _channel.setMethodCallHandler(null);
    return result as String;
  }

  /// Install a cloned APK
  static Future<bool> installApk(String apkPath) async {
    final result = await _channel.invokeMethod(
      AppConstants.methodInstallApk,
      {'apkPath': apkPath},
    );
    return result as bool;
  }

  /// Uninstall a cloned app
  static Future<bool> uninstallApp(String packageName) async {
    final result = await _channel.invokeMethod(
      AppConstants.methodUninstallApp,
      {'packageName': packageName},
    );
    return result as bool;
  }

  /// Launch a cloned app
  static Future<bool> launchApp(String packageName) async {
    final result = await _channel.invokeMethod(
      AppConstants.methodLaunchApp,
      {'packageName': packageName},
    );
    return result as bool;
  }

  /// Check if an app is installed
  static Future<bool> isAppInstalled(String packageName) async {
    final result = await _channel.invokeMethod(
      AppConstants.methodIsAppInstalled,
      {'packageName': packageName},
    );
    return result as bool;
  }
}
