import 'package:hive/hive.dart';
import 'package:app_cloner/models/app_info.dart';
import 'package:app_cloner/models/clone_info.dart';
import 'package:app_cloner/services/native_bridge.dart';

class CloneService {
  static Box<CloneInfo> get _box => Hive.box<CloneInfo>('clones');

  /// Get all clones
  static List<CloneInfo> getAllClones() {
    return _box.values.toList()
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
  }

  /// Get clones for a specific app
  static List<CloneInfo> getClonesForApp(String packageName) {
    return _box.values
        .where((c) => c.originalPackage == packageName)
        .toList();
  }

  /// Get next clone index for an app
  static int getNextCloneIndex(String packageName) {
    final clones = getClonesForApp(packageName);
    if (clones.isEmpty) return 1;
    return clones.map((c) => c.cloneIndex).reduce((a, b) => a > b ? a : b) + 1;
  }

  /// Clone an app
  static Future<CloneInfo> cloneApp({
    required AppInfo appInfo,
    String? customName,
    Function(String status, double progress)? onProgress,
  }) async {
    final cloneIndex = getNextCloneIndex(appInfo.packageName);
    final cloneName = customName ?? '${appInfo.appName} Clone $cloneIndex';

    onProgress?.call('Preparing...', 0.0);

    // Call native to clone the app
    final clonePackage = await NativeBridge.cloneApp(
      packageName: appInfo.packageName,
      cloneName: cloneName,
      cloneIndex: cloneIndex,
      progressCallback: onProgress,
    );

    // Save clone info
    final clone = CloneInfo(
      originalPackage: appInfo.packageName,
      clonePackage: clonePackage,
      originalAppName: appInfo.appName,
      cloneName: cloneName,
      createdAt: DateTime.now(),
      cloneIndex: cloneIndex,
      originalVersionName: appInfo.versionName,
      isSplitApk: appInfo.hasSplitApks,
      splitCount: appInfo.splitApkPaths?.length ?? 0,
    );

    await _box.add(clone);
    return clone;
  }

  /// Delete a clone
  static Future<void> deleteClone(CloneInfo clone) async {
    await NativeBridge.uninstallApp(clone.clonePackage);
    await clone.delete();
  }

  /// Launch a clone
  static Future<bool> launchClone(CloneInfo clone) async {
    return await NativeBridge.launchApp(clone.clonePackage);
  }

  /// Check if clone is still installed
  static Future<bool> isCloneInstalled(CloneInfo clone) async {
    return await NativeBridge.isAppInstalled(clone.clonePackage);
  }

  /// Clean up cache
  static Future<void> cleanupCache() async {
    await NativeBridge.cleanupCache();
  }
}
