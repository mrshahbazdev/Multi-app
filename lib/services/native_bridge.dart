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

  /// Get detailed split APK info for an app
  static Future<SplitApkDetails> getSplitInfo(String packageName) async {
    final result = await _channel.invokeMethod(
      'getSplitInfo',
      {'packageName': packageName},
    );
    return SplitApkDetails.fromMap(Map<dynamic, dynamic>.from(result));
  }

  /// Clone an app - returns clone package name
  static Future<String> cloneApp({
    required String packageName,
    required String cloneName,
    required int cloneIndex,
    Function(String status, double progress)? progressCallback,
  }) async {
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

  /// Check if we can install packages
  static Future<bool> canInstallPackages() async {
    final result = await _channel.invokeMethod('canInstallPackages');
    return result as bool;
  }

  /// Request permission to install unknown apps
  static Future<void> requestInstallPermission() async {
    await _channel.invokeMethod('requestInstallPermission');
  }

  /// Clean up cache/temporary files
  static Future<void> cleanupCache() async {
    await _channel.invokeMethod('cleanupCache');
  }

  /// Get storage info (cache size, etc.)
  static Future<Map<String, dynamic>> getStorageInfo() async {
    final result = await _channel.invokeMethod('getStorageInfo');
    return Map<String, dynamic>.from(result as Map);
  }
}

/// Detailed info about an app's split APKs
class SplitApkDetails {
  final bool isSplit;
  final int splitCount;
  final int totalSizeBytes;
  final String totalSizeFormatted;
  final List<SplitInfo> splits;

  SplitApkDetails({
    required this.isSplit,
    required this.splitCount,
    required this.totalSizeBytes,
    required this.totalSizeFormatted,
    required this.splits,
  });

  factory SplitApkDetails.fromMap(Map<dynamic, dynamic> map) {
    final splitsList = (map['splits'] as List<dynamic>?)?.map((s) {
      final m = Map<dynamic, dynamic>.from(s);
      return SplitInfo(
        fileName: m['fileName'] as String,
        type: m['type'] as String,
        sizeBytes: m['sizeBytes'] as int,
        sizeFormatted: m['sizeFormatted'] as String,
      );
    }).toList() ?? [];

    return SplitApkDetails(
      isSplit: map['isSplit'] as bool? ?? false,
      splitCount: map['splitCount'] as int? ?? 1,
      totalSizeBytes: map['totalSizeBytes'] as int? ?? 0,
      totalSizeFormatted: map['totalSizeFormatted'] as String? ?? '0 B',
      splits: splitsList,
    );
  }
}

class SplitInfo {
  final String fileName;
  final String type;
  final int sizeBytes;
  final String sizeFormatted;

  SplitInfo({
    required this.fileName,
    required this.type,
    required this.sizeBytes,
    required this.sizeFormatted,
  });
}
