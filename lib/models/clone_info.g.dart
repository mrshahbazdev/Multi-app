// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'clone_info.dart';

class CloneInfoAdapter extends TypeAdapter<CloneInfo> {
  @override
  final int typeId = 0;

  @override
  CloneInfo read(BinaryReader reader) {
    final numOfFields = reader.readByte();
    final fields = <int, dynamic>{
      for (int i = 0; i < numOfFields; i++) reader.readByte(): reader.read(),
    };
    return CloneInfo(
      originalPackage: fields[0] as String,
      clonePackage: fields[1] as String,
      originalAppName: fields[2] as String,
      cloneName: fields[3] as String,
      createdAt: fields[4] as DateTime,
      cloneIndex: fields[5] as int,
      apkPath: fields[6] as String?,
      originalVersionName: fields[7] as String,
    );
  }

  @override
  void write(BinaryWriter writer, CloneInfo obj) {
    writer
      ..writeByte(8)
      ..writeByte(0)
      ..write(obj.originalPackage)
      ..writeByte(1)
      ..write(obj.clonePackage)
      ..writeByte(2)
      ..write(obj.originalAppName)
      ..writeByte(3)
      ..write(obj.cloneName)
      ..writeByte(4)
      ..write(obj.createdAt)
      ..writeByte(5)
      ..write(obj.cloneIndex)
      ..writeByte(6)
      ..write(obj.apkPath)
      ..writeByte(7)
      ..write(obj.originalVersionName);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CloneInfoAdapter &&
          runtimeType == other.runtimeType &&
          typeId == other.typeId;
}
