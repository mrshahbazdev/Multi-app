import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/models/app_info.dart';
import 'package:app_cloner/services/native_bridge.dart';

// Provider for installed apps list
final installedAppsProvider = FutureProvider<List<AppInfo>>((ref) async {
  final includeSystem = ref.watch(showSystemAppsProvider);
  final apps = await NativeBridge.getInstalledApps(includeSystemApps: includeSystem);
  apps.sort((a, b) => a.appName.toLowerCase().compareTo(b.appName.toLowerCase()));
  return apps;
});

// Toggle for showing system apps
final showSystemAppsProvider = StateProvider<bool>((ref) => false);

// Search query
final searchQueryProvider = StateProvider<String>((ref) => '');

// Filtered apps based on search
final filteredAppsProvider = Provider<AsyncValue<List<AppInfo>>>((ref) {
  final appsAsync = ref.watch(installedAppsProvider);
  final query = ref.watch(searchQueryProvider).toLowerCase();

  return appsAsync.whenData((apps) {
    if (query.isEmpty) return apps;
    return apps.where((app) {
      return app.appName.toLowerCase().contains(query) ||
          app.packageName.toLowerCase().contains(query);
    }).toList();
  });
});
