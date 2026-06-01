import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/models/clone_info.dart';
import 'package:app_cloner/providers/clone_provider.dart';
import 'package:app_cloner/providers/premium_provider.dart';
import 'package:app_cloner/ui/screens/app_picker_screen.dart';
import 'package:app_cloner/ui/screens/batch_clone_screen.dart';
import 'package:app_cloner/ui/screens/premium_screen.dart';
import 'package:app_cloner/ui/screens/settings_screen.dart';
import 'package:app_cloner/ui/widgets/banner_ad_widget.dart';
import 'package:app_cloner/ui/widgets/clone_tile.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final clones = ref.watch(clonesProvider);
    final isPremium = ref.watch(premiumProvider).isPremium;

    return Scaffold(
      appBar: AppBar(
        title: const Text('App Cloner'),
        actions: [
          // Premium upgrade (hidden once unlocked)
          if (!isPremium)
            IconButton(
              icon: const Icon(Icons.workspace_premium, color: Color(0xFFFFB300)),
              tooltip: 'Go Premium',
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const PremiumScreen()),
              ),
            ),
          // Batch clone (Premium feature)
          IconButton(
            icon: const Icon(Icons.library_add_outlined),
            tooltip: 'Batch Clone',
            onPressed: () async {
              if (!isPremium) {
                _showBatchPremiumGate(context);
                return;
              }
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const BatchCloneScreen()),
              );
              ref.read(clonesProvider.notifier).refresh();
            },
          ),
          // Settings
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SettingsScreen()),
            ),
          ),
        ],
      ),
      body: clones.isEmpty ? _buildEmptyState(context) : _buildClonesList(context, clones),
      bottomNavigationBar: const SafeArea(child: BannerAdWidget()),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          await Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const AppPickerScreen()),
          );
          ref.read(clonesProvider.notifier).refresh();
        },
        icon: const Icon(Icons.add),
        label: const Text('Clone App'),
      ),
    );
  }

  void _showBatchPremiumGate(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Premium feature'),
        content: const Text(
          'Batch cloning lets you clone multiple apps at once. '
          'Upgrade to Premium to unlock it.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Not now'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const PremiumScreen()),
              );
            },
            child: const Text('Go Premium'),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.copy_all_rounded,
            size: 80,
            color: Theme.of(context).colorScheme.primary.withOpacity(0.5),
          ),
          const SizedBox(height: 16),
          Text(
            'No Clones Yet',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  color: Colors.white70,
                ),
          ),
          const SizedBox(height: 8),
          Text(
            'Tap "Clone App" to get started\nor use Batch Clone for multiple apps',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Colors.white38,
                ),
          ),
          const SizedBox(height: 48),
        ],
      ),
    );
  }

  Widget _buildClonesList(BuildContext context, List<CloneInfo> clones) {
    return Column(
      children: [
        // Stats bar
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          child: Row(
            children: [
              Text(
                '${clones.length} Clone${clones.length != 1 ? 's' : ''}',
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: Colors.white54,
                ),
              ),
              const Spacer(),
              Icon(Icons.sort, size: 18, color: Colors.white38),
              const SizedBox(width: 4),
              const Text(
                'Recent first',
                style: TextStyle(fontSize: 12, color: Colors.white38),
              ),
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: clones.length,
            itemBuilder: (context, index) {
              return Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: CloneTile(clone: clones[index]),
              );
            },
          ),
        ),
      ],
    );
  }
}
