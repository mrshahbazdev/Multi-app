import 'package:flutter/services.dart';
import 'package:app_cloner/core/constants.dart';

/// Stealth configuration for clone detection evasion.
class StealthConfig {
  final bool spoofSignature;
  final bool randomizePackageName;
  final bool removeDebugFlags;
  final bool patchNativeLibs;
  final bool spoofDeviceProfile;
  final String? customPackagePrefix;

  const StealthConfig({
    this.spoofSignature = true,
    this.randomizePackageName = false,
    this.removeDebugFlags = true,
    this.patchNativeLibs = false,
    this.spoofDeviceProfile = true,
    this.customPackagePrefix,
  });

  Map<String, dynamic> toMap() => {
        'spoofSignature': spoofSignature,
        'randomizePackageName': randomizePackageName,
        'removeDebugFlags': removeDebugFlags,
        'patchNativeLibs': patchNativeLibs,
        'spoofDeviceProfile': spoofDeviceProfile,
        'customPackagePrefix': customPackagePrefix,
      };

  factory StealthConfig.fromMap(Map<String, dynamic> map) => StealthConfig(
        spoofSignature: map['spoofSignature'] as bool? ?? true,
        randomizePackageName: map['randomizePackageName'] as bool? ?? false,
        removeDebugFlags: map['removeDebugFlags'] as bool? ?? true,
        patchNativeLibs: map['patchNativeLibs'] as bool? ?? false,
        spoofDeviceProfile: map['spoofDeviceProfile'] as bool? ?? true,
        customPackagePrefix: map['customPackagePrefix'] as String?,
      );

  StealthConfig copyWith({
    bool? spoofSignature,
    bool? randomizePackageName,
    bool? removeDebugFlags,
    bool? patchNativeLibs,
    bool? spoofDeviceProfile,
    String? customPackagePrefix,
  }) =>
      StealthConfig(
        spoofSignature: spoofSignature ?? this.spoofSignature,
        randomizePackageName: randomizePackageName ?? this.randomizePackageName,
        removeDebugFlags: removeDebugFlags ?? this.removeDebugFlags,
        patchNativeLibs: patchNativeLibs ?? this.patchNativeLibs,
        spoofDeviceProfile: spoofDeviceProfile ?? this.spoofDeviceProfile,
        customPackagePrefix: customPackagePrefix ?? this.customPackagePrefix,
      );
}

/// Device profile generated for a clone.
class DeviceProfile {
  final String androidId;
  final String serialNumber;
  final String buildFingerprint;
  final String wifiMac;
  final String bluetoothMac;
  final String gsfId;
  final String advertisingId;
  final String imei;
  final String buildModel;
  final String buildManufacturer;
  final String buildBrand;
  final String buildDevice;
  final String buildProduct;

  DeviceProfile({
    required this.androidId,
    required this.serialNumber,
    required this.buildFingerprint,
    required this.wifiMac,
    required this.bluetoothMac,
    required this.gsfId,
    required this.advertisingId,
    required this.imei,
    required this.buildModel,
    required this.buildManufacturer,
    required this.buildBrand,
    required this.buildDevice,
    required this.buildProduct,
  });

  factory DeviceProfile.fromMap(Map<dynamic, dynamic> map) => DeviceProfile(
        androidId: map['androidId'] as String? ?? '',
        serialNumber: map['serialNumber'] as String? ?? '',
        buildFingerprint: map['buildFingerprint'] as String? ?? '',
        wifiMac: map['wifiMac'] as String? ?? '',
        bluetoothMac: map['bluetoothMac'] as String? ?? '',
        gsfId: map['gsfId'] as String? ?? '',
        advertisingId: map['advertisingId'] as String? ?? '',
        imei: map['imei'] as String? ?? '',
        buildModel: map['buildModel'] as String? ?? '',
        buildManufacturer: map['buildManufacturer'] as String? ?? '',
        buildBrand: map['buildBrand'] as String? ?? '',
        buildDevice: map['buildDevice'] as String? ?? '',
        buildProduct: map['buildProduct'] as String? ?? '',
      );
}

/// Service for stealth and anti-detection features.
class StealthService {
  static const _channel = MethodChannel(AppConstants.channelName);

  /// Apply stealth patches to a cloned APK.
  static Future<String> applyStealthPatches({
    required String apkPath,
    required String originalPackage,
    required StealthConfig config,
  }) async {
    final result = await _channel.invokeMethod('applyStealthPatches', {
      'apkPath': apkPath,
      'originalPackage': originalPackage,
      'config': config.toMap(),
    });
    return result as String;
  }

  /// Generate a device profile for a clone.
  static Future<DeviceProfile> generateDeviceProfile({
    required String originalPackage,
    required int cloneIndex,
  }) async {
    final result = await _channel.invokeMethod('generateDeviceProfile', {
      'packageName': originalPackage,
      'cloneIndex': cloneIndex,
    });
    return DeviceProfile.fromMap(Map<dynamic, dynamic>.from(result as Map));
  }

  /// Generate a stealth package name.
  static Future<String> generateStealthPackageName({
    required String originalPackage,
    required int cloneIndex,
    StealthConfig config = const StealthConfig(),
  }) async {
    final result = await _channel.invokeMethod('generateStealthPackageName', {
      'packageName': originalPackage,
      'cloneIndex': cloneIndex,
      'config': config.toMap(),
    });
    return result as String;
  }

  /// Get stealth status/info for a clone.
  static Future<Map<String, dynamic>> getStealthInfo(String clonePackage) async {
    final result = await _channel.invokeMethod('getStealthInfo', {
      'packageName': clonePackage,
    });
    return Map<String, dynamic>.from(result as Map);
  }
}
