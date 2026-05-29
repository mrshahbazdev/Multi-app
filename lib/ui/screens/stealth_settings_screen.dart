import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/services/stealth_service.dart';

/// Provider for stealth configuration state.
final stealthConfigProvider = StateProvider<StealthConfig>((ref) {
  return const StealthConfig();
});

class StealthSettingsScreen extends ConsumerStatefulWidget {
  const StealthSettingsScreen({super.key});

  @override
  ConsumerState<StealthSettingsScreen> createState() => _StealthSettingsScreenState();
}

class _StealthSettingsScreenState extends ConsumerState<StealthSettingsScreen> {
  final _prefixController = TextEditingController();
  DeviceProfile? _previewProfile;
  bool _loadingPreview = false;

  @override
  void dispose() {
    _prefixController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final config = ref.watch(stealthConfigProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Stealth Settings'),
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline),
            onPressed: _showInfoDialog,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(12),
                        color: Colors.red.withOpacity(0.15),
                      ),
                      child: const Icon(Icons.shield, color: Colors.red, size: 28),
                    ),
                    const SizedBox(width: 16),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Anti-Detection',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          SizedBox(height: 2),
                          Text(
                            'Prevent apps from detecting they are cloned',
                            style: TextStyle(fontSize: 12, color: Colors.white54),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Signature Spoofing section
            _sectionTitle('Signature Verification'),
            _switchTile(
              title: 'Spoof App Signature',
              subtitle: 'Inject original signature to bypass integrity checks',
              icon: Icons.verified_user,
              value: config.spoofSignature,
              onChanged: (v) => ref.read(stealthConfigProvider.notifier).state =
                  config.copyWith(spoofSignature: v),
            ),
            const SizedBox(height: 16),

            // Package Name section
            _sectionTitle('Package Name Strategy'),
            _switchTile(
              title: 'Randomize Package Name',
              subtitle: 'Use random-looking package names instead of "com.clone1..."',
              icon: Icons.shuffle,
              value: config.randomizePackageName,
              onChanged: (v) => ref.read(stealthConfigProvider.notifier).state =
                  config.copyWith(randomizePackageName: v),
            ),
            if (config.randomizePackageName) ...[
              const SizedBox(height: 8),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Custom Package Prefix (optional)',
                        style: TextStyle(fontSize: 12, color: Colors.white54),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _prefixController,
                        decoration: const InputDecoration(
                          hintText: 'e.g. com.social.app',
                          helperText: 'Leave empty for auto-generated prefix',
                          isDense: true,
                        ),
                        onChanged: (v) {
                          ref.read(stealthConfigProvider.notifier).state =
                              config.copyWith(customPackagePrefix: v.isEmpty ? null : v);
                        },
                      ),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(height: 16),

            // Device Fingerprint section
            _sectionTitle('Device Fingerprint'),
            _switchTile(
              title: 'Spoof Device Profile',
              subtitle: 'Give each clone unique device identifiers (ANDROID_ID, IMEI, etc.)',
              icon: Icons.fingerprint,
              value: config.spoofDeviceProfile,
              onChanged: (v) => ref.read(stealthConfigProvider.notifier).state =
                  config.copyWith(spoofDeviceProfile: v),
            ),
            if (config.spoofDeviceProfile) ...[
              const SizedBox(height: 8),
              _buildDeviceProfilePreview(),
            ],
            const SizedBox(height: 16),

            // APK Hardening section
            _sectionTitle('APK Hardening'),
            _switchTile(
              title: 'Remove Debug Flags',
              subtitle: 'Strip android:debuggable from manifest',
              icon: Icons.bug_report,
              value: config.removeDebugFlags,
              onChanged: (v) => ref.read(stealthConfigProvider.notifier).state =
                  config.copyWith(removeDebugFlags: v),
            ),
            const SizedBox(height: 8),
            _switchTile(
              title: 'Patch Detection Strings',
              subtitle: 'Remove known clone-detection strings from DEX files (experimental)',
              icon: Icons.code,
              value: config.patchNativeLibs,
              onChanged: (v) => ref.read(stealthConfigProvider.notifier).state =
                  config.copyWith(patchNativeLibs: v),
              isExperimental: true,
            ),
            const SizedBox(height: 24),

            // Warning card
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                color: Colors.orange.withOpacity(0.1),
                border: Border.all(color: Colors.orange.withOpacity(0.3)),
              ),
              child: const Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.warning_amber, color: Colors.orange, size: 20),
                  SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Important Notes',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 13,
                            color: Colors.orange,
                          ),
                        ),
                        SizedBox(height: 4),
                        Text(
                          '• Signature spoofing helps bypass basic checks but may not work with Play Integrity API\n'
                          '• DEX patching is experimental and may cause crashes in some apps\n'
                          '• Device profile spoofing requires the clone to read the injected config at runtime',
                          style: TextStyle(fontSize: 11, color: Colors.white54),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _sectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        title,
        style: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: Colors.white54,
        ),
      ),
    );
  }

  Widget _switchTile({
    required String title,
    required String subtitle,
    required IconData icon,
    required bool value,
    required ValueChanged<bool> onChanged,
    bool isExperimental = false,
  }) {
    return Card(
      child: SwitchListTile(
        title: Row(
          children: [
            Icon(icon, size: 20, color: value ? Colors.green : Colors.white38),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(title, style: const TextStyle(fontSize: 14)),
                      if (isExperimental) ...[
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(4),
                            color: Colors.orange.withOpacity(0.15),
                          ),
                          child: const Text(
                            'BETA',
                            style: TextStyle(fontSize: 9, color: Colors.orange, fontWeight: FontWeight.bold),
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: const TextStyle(fontSize: 11, color: Colors.white38),
                  ),
                ],
              ),
            ),
          ],
        ),
        value: value,
        onChanged: onChanged,
      ),
    );
  }

  Widget _buildDeviceProfilePreview() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Text(
                  'Preview: Generated Profile',
                  style: TextStyle(fontSize: 12, color: Colors.white54),
                ),
                const Spacer(),
                TextButton.icon(
                  onPressed: _loadPreviewProfile,
                  icon: _loadingPreview
                      ? const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.refresh, size: 16),
                  label: Text(
                    _previewProfile == null ? 'Generate' : 'Refresh',
                    style: const TextStyle(fontSize: 12),
                  ),
                ),
              ],
            ),
            if (_previewProfile != null) ...[
              const Divider(height: 16),
              _profileRow('Android ID', _previewProfile!.androidId),
              _profileRow('IMEI', _previewProfile!.imei),
              _profileRow('Serial', _previewProfile!.serialNumber),
              _profileRow('Wi-Fi MAC', _previewProfile!.wifiMac),
              _profileRow('Model', _previewProfile!.buildModel),
              _profileRow('Brand', _previewProfile!.buildBrand),
              _profileRow('Manufacturer', _previewProfile!.buildManufacturer),
            ],
          ],
        ),
      ),
    );
  }

  Widget _profileRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        children: [
          SizedBox(
            width: 100,
            child: Text(
              label,
              style: const TextStyle(fontSize: 11, color: Colors.white38),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(fontSize: 11, fontFamily: 'monospace'),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _loadPreviewProfile() async {
    setState(() => _loadingPreview = true);
    try {
      final profile = await StealthService.generateDeviceProfile(
        originalPackage: 'com.example.app',
        cloneIndex: 1,
      );
      if (mounted) {
        setState(() {
          _previewProfile = profile;
          _loadingPreview = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _loadingPreview = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    }
  }

  void _showInfoDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Stealth Mode'),
        content: const SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'How Clone Detection Works',
                style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
              ),
              SizedBox(height: 8),
              Text(
                '1. Signature Check — Apps verify their signing certificate\n'
                '2. Package Name — Apps check for known clone prefixes\n'
                '3. Device IDs — Servers detect same device = same person\n'
                '4. Debug Flags — Clone tools may leave debuggable=true\n'
                '5. File Paths — Apps scan for clone-related files',
                style: TextStyle(fontSize: 12, color: Colors.white70),
              ),
              SizedBox(height: 16),
              Text(
                'What We Do',
                style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
              ),
              SizedBox(height: 8),
              Text(
                '• Inject original signature into clone assets\n'
                '• Generate random package names\n'
                '• Create unique device fingerprints per clone\n'
                '• Strip debug metadata\n'
                '• Patch known detection strings in code',
                style: TextStyle(fontSize: 12, color: Colors.white70),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Got it'),
          ),
        ],
      ),
    );
  }
}
