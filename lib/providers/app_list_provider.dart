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

// Toggle for filtering to only split APK apps
final showOnlySplitAppsProvider = StateProvider<bool>((ref) => false);

// Search query
final searchQueryProvider = StateProvider<String>((ref) => '');

// Filtered apps based on search and filters
final filteredAppsProvider = Provider<AsyncValue<List<AppInfo>>>((ref) {
  final appsAsync = ref.watch(installedAppsProvider);
  final query = ref.watch(searchQueryProvider).toLowerCase();
  final onlySplit = ref.watch(showOnlySplitAppsProvider);

  return appsAsync.whenData((apps) {
    var filtered = apps;

    if (onlySplit) {
      filtered = filtered.where((app) => app.hasSplitApks).toList();
    }

    if (query.isNotEmpty) {
      filtered = filtered.where((app) {
        return app.appName.toLowerCase().contains(query) ||
            app.packageName.toLowerCase().contains(query);
      }).toList();
    }

    return filtered;
  });
});

// Stats providers
final splitAppCountProvider = Provider<AsyncValue<int>>((ref) {
  return ref.watch(installedAppsProvider).whenData(
    (apps) => apps.where((a) => a.hasSplitApks).length,
  );
});
