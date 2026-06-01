import 'package:flutter/services.dart';
import 'package:app_cloner/core/constants.dart';

/// Device-wide Google Play Services availability.
class GmsStatus {
  final bool available;
  final bool installed;
  final bool enabled;
  final bool playStoreInstalled;
  final String versionName;
  final int versionCode;
  final String statusText;

  const GmsStatus({
    required this.available,
    required this.installed,
    required this.enabled,
    required this.playStoreInstalled,
    required this.versionName,
    required this.versionCode,
    required this.statusText,
  });

  factory GmsStatus.fromMap(Map<dynamic, dynamic> map) => GmsStatus(
        available: map['available'] as bool? ?? false,
        installed: map['installed'] as bool? ?? false,
        enabled: map['enabled'] as bool? ?? false,
        playStoreInstalled: map['playStoreInstalled'] as bool? ?? false,
        versionName: map['versionName'] as String? ?? '',
        versionCode: (map['versionCode'] as num?)?.toInt() ?? 0,
        statusText: map['statusText'] as String? ?? 'Unknown',
      );

  static const GmsStatus unknown = GmsStatus(
    available: false,
    installed: false,
    enabled: false,
    playStoreInstalled: false,
    versionName: '',
    versionCode: 0,
    statusText: 'Unknown',
  );
}

/// Whether a specific app depends on Google Play Services.
class GmsUsage {
  final String packageName;
  final bool usesGms;
  final List<String> reasons;

  const GmsUsage({
    required this.packageName,
    required this.usesGms,
    required this.reasons,
  });

  factory GmsUsage.fromMap(Map<dynamic, dynamic> map) => GmsUsage(
        packageName: map['packageName'] as String? ?? '',
        usesGms: map['usesGms'] as bool? ?? false,
        reasons: (map['reasons'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
      );
}

/// Service for Google Play Services (GMS) detection and clone compatibility.
class GmsService {
  static const _channel = MethodChannel(AppConstants.channelName);

  /// Get device-wide Google Play Services availability.
  static Future<GmsStatus> getGmsStatus() async {
    try {
      final result = await _channel.invokeMethod(AppConstants.methodGetGmsStatus);
      if (result == null) return GmsStatus.unknown;
      return GmsStatus.fromMap(Map<dynamic, dynamic>.from(result as Map));
    } on PlatformException {
      return GmsStatus.unknown;
    }
  }

  /// Check whether [packageName] relies on Google Play Services.
  ///
  /// Cloned GMS-dependent apps generally need a separate Google account for
  /// push notifications and login to work correctly.
  static Future<GmsUsage> appUsesGms(String packageName) async {
    try {
      final result = await _channel.invokeMethod(
        AppConstants.methodAppUsesGms,
        {'packageName': packageName},
      );
      if (result == null) {
        return GmsUsage(packageName: packageName, usesGms: false, reasons: const []);
      }
      return GmsUsage.fromMap(Map<dynamic, dynamic>.from(result as Map));
    } on PlatformException {
      return GmsUsage(packageName: packageName, usesGms: false, reasons: const []);
    }
  }
}
