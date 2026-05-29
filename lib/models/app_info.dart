import 'dart:typed_data';

class AppInfo {
  final String appName;
  final String packageName;
  final String versionName;
  final int versionCode;
  final String apkPath;
  final bool isSystemApp;
  final int apkSizeBytes;
  final Uint8List? iconBytes;
  final List<String>? splitApkPaths;

  AppInfo({
    required this.appName,
    required this.packageName,
    required this.versionName,
    required this.versionCode,
    required this.apkPath,
    required this.isSystemApp,
    required this.apkSizeBytes,
    this.iconBytes,
    this.splitApkPaths,
  });

  bool get hasSplitApks => splitApkPaths != null && splitApkPaths!.isNotEmpty;

  String get apkSizeFormatted {
    if (apkSizeBytes < 1024) return '$apkSizeBytes B';
    if (apkSizeBytes < 1024 * 1024) return '${(apkSizeBytes / 1024).toStringAsFixed(1)} KB';
    return '${(apkSizeBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  factory AppInfo.fromMap(Map<dynamic, dynamic> map) {
    return AppInfo(
      appName: map['appName'] ?? '',
      packageName: map['packageName'] ?? '',
      versionName: map['versionName'] ?? '',
      versionCode: map['versionCode'] ?? 0,
      apkPath: map['apkPath'] ?? '',
      isSystemApp: map['isSystemApp'] ?? false,
      apkSizeBytes: map['apkSizeBytes'] ?? 0,
      iconBytes: map['iconBytes'] != null ? Uint8List.fromList(List<int>.from(map['iconBytes'])) : null,
      splitApkPaths: map['splitApkPaths'] != null
          ? List<String>.from(map['splitApkPaths'])
          : null,
    );
  }
}
