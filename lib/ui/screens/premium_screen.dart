import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/core/constants.dart';
import 'package:app_cloner/providers/premium_provider.dart';

class _Feature {
  final IconData icon;
  final String title;
  final bool free;
  final bool premium;
  const _Feature(this.icon, this.title, {required this.free, required this.premium});
}

class PremiumScreen extends ConsumerWidget {
  const PremiumScreen({super.key});

  static const _features = [
    _Feature(Icons.copy_all, 'Clones', free: false, premium: true),
    _Feature(Icons.block, 'Ad-free experience', free: false, premium: true),
    _Feature(Icons.library_add, 'Batch cloning', free: false, premium: true),
    _Feature(Icons.palette, 'Custom clone icons', free: false, premium: true),
    _Feature(Icons.shield, 'Stealth & anti-detection', free: true, premium: true),
    _Feature(Icons.backup, 'Backup & restore', free: true, premium: true),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final premium = ref.watch(premiumProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Go Premium')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _header(context, premium.isPremium),
          const SizedBox(height: 24),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  _planHeaderRow(),
                  const Divider(height: 24),
                  ..._features.map(_featureRow),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          if (!premium.isPremium) ...[
            _priceCard(context),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 54,
              child: ElevatedButton.icon(
                onPressed: () => _upgrade(context, ref),
                icon: const Icon(Icons.workspace_premium),
                label: const Text(
                  'Upgrade to Premium',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFFFB300),
                  foregroundColor: Colors.black,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
              ),
            ),
            TextButton(
              onPressed: () => ref.read(premiumProvider.notifier).setPremium(true),
              child: const Text('Restore purchases'),
            ),
          ] else
            Center(
              child: Column(
                children: [
                  const Icon(Icons.verified, color: Color(0xFFFFB300), size: 48),
                  const SizedBox(height: 8),
                  Text(
                    'You are a Premium member',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ],
              ),
            ),
          const SizedBox(height: 16),
          const Text(
            'Note: Purchases are processed through Google Play Billing. '
            'Prices may vary by region.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 11, color: Colors.white38),
          ),
        ],
      ),
    );
  }

  Widget _header(BuildContext context, bool isPremium) {
    return Column(
      children: [
        Container(
          width: 72,
          height: 72,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: const Color(0xFFFFB300).withOpacity(0.15),
          ),
          child: const Icon(Icons.workspace_premium,
              color: Color(0xFFFFB300), size: 40),
        ),
        const SizedBox(height: 16),
        Text(
          isPremium ? 'Premium Active' : 'Unlock Everything',
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 4),
        const Text(
          'Unlimited clones, no ads, and all advanced features',
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.white54),
        ),
      ],
    );
  }

  Widget _planHeaderRow() {
    return const Row(
      children: [
        Expanded(flex: 4, child: SizedBox()),
        Expanded(
          flex: 2,
          child: Text('Free',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white54, fontWeight: FontWeight.w600)),
        ),
        Expanded(
          flex: 2,
          child: Text('Premium',
              textAlign: TextAlign.center,
              style: TextStyle(
                  color: Color(0xFFFFB300), fontWeight: FontWeight.w600)),
        ),
      ],
    );
  }

  Widget _featureRow(_Feature f) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Expanded(
            flex: 4,
            child: Row(
              children: [
                Icon(f.icon, size: 18, color: Colors.white54),
                const SizedBox(width: 8),
                Expanded(child: Text(f.title, style: const TextStyle(fontSize: 13))),
              ],
            ),
          ),
          Expanded(
            flex: 2,
            child: Center(child: _planCell(f.title == 'Clones' ? 'free' : (f.free ? 'yes' : 'no'))),
          ),
          Expanded(
            flex: 2,
            child: Center(child: _planCell(f.title == 'Clones' ? 'unlimited' : (f.premium ? 'yes' : 'no'))),
          ),
        ],
      ),
    );
  }

  Widget _planCell(String value) {
    switch (value) {
      case 'yes':
        return const Icon(Icons.check_circle, color: Colors.green, size: 20);
      case 'no':
        return const Icon(Icons.remove, color: Colors.white24, size: 20);
      case 'free':
        return const Text('${AppConstants.maxFreeClones}',
            style: TextStyle(fontWeight: FontWeight.w600));
      case 'unlimited':
        return const Icon(Icons.all_inclusive, color: Color(0xFFFFB300), size: 20);
      default:
        return const SizedBox();
    }
  }

  Widget _priceCard(BuildContext context) {
    return Card(
      color: const Color(0xFFFFB300).withOpacity(0.08),
      child: const Padding(
        padding: EdgeInsets.all(16),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            Text('\$4.99',
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
            SizedBox(width: 6),
            Text('one-time', style: TextStyle(color: Colors.white54)),
          ],
        ),
      ),
    );
  }

  Future<void> _upgrade(BuildContext context, WidgetRef ref) async {
    await ref.read(premiumProvider.notifier).upgradeToPremium();
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Premium unlocked. Thank you!')),
      );
    }
  }
}
