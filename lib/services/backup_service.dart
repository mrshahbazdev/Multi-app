import 'dart:convert';
import 'dart:io';
import 'package:hive/hive.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:app_cloner/models/clone_info.dart';

/// Service for backing up and restoring clone configurations.
class BackupService {
  static Box<CloneInfo> get _box => Hive.box<CloneInfo>('clones');

  /// Export all clone configs as a JSON file.
  /// Returns the file path of the exported backup.
  static Future<String> exportBackup() async {
    final clones = _box.values.toList();
    final backupData = {
      'version': 1,
      'exportedAt': DateTime.now().toIso8601String(),
      'cloneCount': clones.length,
      'clones': clones.map((c) => _cloneToMap(c)).toList(),
    };

    final dir = await getApplicationDocumentsDirectory();
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final file = File('${dir.path}/appcloner_backup_$timestamp.json');
    await file.writeAsString(const JsonEncoder.withIndent('  ').convert(backupData));

    return file.path;
  }

  /// Share a backup file using the system share sheet.
  static Future<void> shareBackup() async {
    final filePath = await exportBackup();
    await Share.shareXFiles([XFile(filePath)], text: 'App Cloner Backup');
  }

  /// Import clone configs from a JSON backup file.
  /// Returns the number of clones imported.
  static Future<int> importBackup(String filePath) async {
    final file = File(filePath);
    if (!await file.exists()) {
      throw Exception('Backup file not found');
    }

    final content = await file.readAsString();
    final data = jsonDecode(content) as Map<String, dynamic>;

    final version = data['version'] as int? ?? 1;
    if (version > 1) {
      throw Exception('Unsupported backup version: $version');
    }

    final clonesList = data['clones'] as List<dynamic>;
    int imported = 0;

    for (final cloneData in clonesList) {
      final map = cloneData as Map<String, dynamic>;
      final clone = _mapToClone(map);

      // Check if this clone already exists (by clonePackage)
      final exists = _box.values.any((c) => c.clonePackage == clone.clonePackage);
      if (!exists) {
        await _box.add(clone);
        imported++;
      }
    }

    return imported;
  }

  /// Get backup info without importing.
  static Future<BackupInfo> getBackupInfo(String filePath) async {
    final file = File(filePath);
    final content = await file.readAsString();
    final data = jsonDecode(content) as Map<String, dynamic>;

    return BackupInfo(
      version: data['version'] as int? ?? 1,
      exportedAt: DateTime.parse(data['exportedAt'] as String),
      cloneCount: data['cloneCount'] as int? ?? 0,
    );
  }

  static Map<String, dynamic> _cloneToMap(CloneInfo clone) {
    return {
      'originalPackage': clone.originalPackage,
      'clonePackage': clone.clonePackage,
      'originalAppName': clone.originalAppName,
      'cloneName': clone.cloneName,
      'createdAt': clone.createdAt.toIso8601String(),
      'cloneIndex': clone.cloneIndex,
      'apkPath': clone.apkPath,
      'originalVersionName': clone.originalVersionName,
    };
  }

  static CloneInfo _mapToClone(Map<String, dynamic> map) {
    return CloneInfo(
      originalPackage: map['originalPackage'] as String,
      clonePackage: map['clonePackage'] as String,
      originalAppName: map['originalAppName'] as String,
      cloneName: map['cloneName'] as String,
      createdAt: DateTime.parse(map['createdAt'] as String),
      cloneIndex: map['cloneIndex'] as int,
      apkPath: map['apkPath'] as String?,
      originalVersionName: map['originalVersionName'] as String? ?? '',
    );
  }
}

class BackupInfo {
  final int version;
  final DateTime exportedAt;
  final int cloneCount;

  BackupInfo({
    required this.version,
    required this.exportedAt,
    required this.cloneCount,
  });
}
