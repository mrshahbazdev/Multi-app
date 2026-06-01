import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:app_cloner/core/constants.dart';
import 'package:app_cloner/models/clone_info.dart';
import 'package:app_cloner/services/ad_service.dart';
import 'package:app_cloner/app.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Hive
  await Hive.initFlutter();
  Hive.registerAdapter(CloneInfoAdapter());
  await Hive.openBox<CloneInfo>('clones');
  await Hive.openBox(AppConstants.settingsBox);

  // Initialize AdMob (no-op on unsupported platforms / premium users skip ads).
  await AdService.instance.initialize();

  runApp(
    const ProviderScope(
      child: AppClonerApp(),
    ),
  );
}
