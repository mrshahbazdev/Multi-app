import 'package:hive/hive.dart';

part 'clone_info.g.dart';

@HiveType(typeId: 0)
class CloneInfo extends HiveObject {
  @HiveField(0)
  final String originalPackage;

  @HiveField(1)
  final String clonePackage;

  @HiveField(2)
  final String originalAppName;

  @HiveField(3)
  final String cloneName;

  @HiveField(4)
  final DateTime createdAt;

  @HiveField(5)
  final int cloneIndex;

  @HiveField(6)
  final String? apkPath;

  @HiveField(7)
  final String originalVersionName;

  CloneInfo({
    required this.originalPackage,
    required this.clonePackage,
    required this.originalAppName,
    required this.cloneName,
    required this.createdAt,
    required this.cloneIndex,
    this.apkPath,
    required this.originalVersionName,
  });

  String get displayName => cloneName.isNotEmpty ? cloneName : '$originalAppName Clone $cloneIndex';
}
