import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/models/app_info.dart';
import 'package:app_cloner/providers/app_list_provider.dart';
import 'package:app_cloner/ui/screens/clone_config_screen.dart';
import 'package:app_cloner/ui/widgets/app_tile.dart';

class AppPickerScreen extends ConsumerWidget {
  const AppPickerScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final filteredApps = ref.watch(filteredAppsProvider);
    final showSystemApps = ref.watch(showSystemAppsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Select App'),
        actions: [
          FilterChip(
            label: Text(
              'System',
              style: TextStyle(
                color: showSystemApps ? Colors.white : Colors.white54,
                fontSize: 12,
              ),
            ),
            selected: showSystemApps,
            onSelected: (val) => ref.read(showSystemAppsProvider.notifier).state = val,
            selectedColor: Theme.of(context).colorScheme.primary,
            backgroundColor: const Color(0xFF2A2A3E),
            checkmarkColor: Colors.white,
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: Column(
        children: [
          // Search bar
          Padding(
            padding: const EdgeInsets.all(16),
            child: TextField(
              onChanged: (val) => ref.read(searchQueryProvider.notifier).state = val,
              decoration: InputDecoration(
                hintText: 'Search apps...',
                prefixIcon: const Icon(Icons.search, color: Colors.white38),
                suffixIcon: ref.watch(searchQueryProvider).isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, color: Colors.white38),
                        onPressed: () => ref.read(searchQueryProvider.notifier).state = '',
                      )
                    : null,
              ),
            ),
          ),
          // Apps list
          Expanded(
            child: filteredApps.when(
              data: (apps) => _buildAppsList(context, apps),
              loading: () => const Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    CircularProgressIndicator(),
                    SizedBox(height: 16),
                    Text('Scanning installed apps...', style: TextStyle(color: Colors.white54)),
                  ],
                ),
              ),
              error: (err, _) => Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.error_outline, color: Colors.red, size: 48),
                    const SizedBox(height: 8),
                    Text('Error: $err', style: const TextStyle(color: Colors.red)),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppsList(BuildContext context, List<AppInfo> apps) {
    if (apps.isEmpty) {
      return const Center(
        child: Text('No apps found', style: TextStyle(color: Colors.white54)),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      itemCount: apps.length,
      itemBuilder: (context, index) {
        final app = apps[index];
        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: AppTile(
            appInfo: app,
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => CloneConfigScreen(appInfo: app),
                ),
              );
            },
          ),
        );
      },
    );
  }
}
