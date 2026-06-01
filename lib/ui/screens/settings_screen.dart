import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import 'package:app_cloner/core/constants.dart';
import 'package:app_cloner/providers/clone_provider.dart';
import 'package:app_cloner/providers/premium_provider.dart';
import 'package:app_cloner/services/backup_service.dart';
import 'package:app_cloner/services/clone_service.dart';
import 'package:app_cloner/services/gms_service.dart';
import 'package:app_cloner/ui/screens/premium_screen.dart';
import 'package:app_cloner/ui/screens/stealth_settings_screen.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  bool _isClearing = false;
  bool _isExporting = false;
  bool _isImporting = false;
  GmsStatus _gmsStatus = GmsStatus.unknown;

  @override
  void initState() {
    super.initState();
    _loadGmsStatus();
  }

  Future<void> _loadGmsStatus() async {
    final status = await GmsService.getGmsStatus();
    if (mounted) setState(() => _gmsStatus = status);
  }

  @override
  Widget build(BuildContext context) {
    final clones = ref.watch(clonesProvider);
    final cloneCount = clones.length;
    final isPremium = ref.watch(premiumProvider).isPremium;

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Stats card
          _buildStatsCard(cloneCount),
          const SizedBox(height: 16),

          _buildSection('Subscription', [
            _buildTile(
              isPremium ? 'Premium' : 'Free Plan',
              isPremium
                  ? 'Unlimited clones, no ads, all features'
                  : '${cloneCount.clamp(0, AppConstants.maxFreeClones)}/${AppConstants.maxFreeClones} free clones used',
              isPremium ? Icons.workspace_premium : Icons.star_border,
              () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const PremiumScreen()),
                );
              },
              trailing: isPremium
                  ? const Icon(Icons.verified, color: Color(0xFFFFB300))
                  : null,
            ),
          ]),
          const SizedBox(height: 16),

          _buildSection('Google Play Services', [
            _buildTile(
              'GMS Status',
              _gmsStatus.installed
                  ? '${_gmsStatus.statusText}${_gmsStatus.versionName.isNotEmpty ? ' • v${_gmsStatus.versionName}' : ''}'
                  : _gmsStatus.statusText,
              Icons.cloud,
              null,
              trailing: Icon(
                _gmsStatus.available ? Icons.check_circle : Icons.cancel,
                color: _gmsStatus.available ? Colors.green : Colors.orange,
              ),
            ),
            _buildTile(
              'Play Store',
              _gmsStatus.playStoreInstalled ? 'Installed' : 'Not installed',
              Icons.shop,
              null,
            ),
          ]),
          const SizedBox(height: 16),

          _buildSection('Clone Management', [
            _buildTile(
              'Clear Cache',
              'Remove temporary clone files',
              Icons.cleaning_services,
              _isClearing ? null : _clearCache,
              trailing: _isClearing
                  ? const SizedBox(
                      width: 20, height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : null,
            ),
            _buildTile(
              'Delete All Clones',
              '$cloneCount clones will be removed',
              Icons.delete_sweep,
              cloneCount > 0 ? _deleteAllClones : null,
            ),
          ]),
          const SizedBox(height: 16),

          _buildSection('Backup & Restore', [
            _buildTile(
              'Export Backup',
              'Save clone configs to file',
              Icons.upload_file,
              _isExporting ? null : _exportBackup,
              trailing: _isExporting
                  ? const SizedBox(
                      width: 20, height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : null,
            ),
            _buildTile(
              'Import Backup',
              'Restore clones from backup file',
              Icons.download,
              _isImporting ? null : _importBackup,
              trailing: _isImporting
                  ? const SizedBox(
                      width: 20, height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : null,
            ),
          ]),
          const SizedBox(height: 16),

          _buildSection('Stealth & Anti-Detection', [
            _buildTile(
              'Stealth Settings',
              'Signature spoof, device fingerprint, package randomization',
              Icons.shield,
              () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const StealthSettingsScreen()),
                );
              },
            ),
          ]),
          const SizedBox(height: 16),

          _buildSection('General', [
            _buildToggleTile(
              'Dark Mode',
              'Use dark theme',
              Icons.dark_mode,
              true,
              (val) {},
            ),
            _buildToggleTile(
              'Auto-cleanup',
              'Remove temp files after cloning',
              Icons.auto_delete,
              true,
              (val) {},
            ),
          ]),
          const SizedBox(height: 16),

          _buildSection('About', [
            _buildTile('Version', '1.3.0 (Phase 5)', Icons.info_outline, null),
            _buildTile('Developer', 'App Cloner Team', Icons.code, null),
            _buildTile('Rate Us', 'Rate on Play Store', Icons.star_outline, () {}),
            _buildTile('Privacy Policy', 'Read our privacy policy', Icons.privacy_tip_outlined, () {}),
          ]),
        ],
      ),
    );
  }

  Widget _buildStatsCard(int cloneCount) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            _statItem(Icons.copy_all, '$cloneCount', 'Clones'),
            const SizedBox(width: 24),
            _statItem(Icons.apps, 'Unlimited', 'App Support'),
            const SizedBox(width: 24),
            _statItem(Icons.extension, 'Split APK', 'Support'),
          ],
        ),
      ),
    );
  }

  Widget _statItem(IconData icon, String value, String label) {
    return Expanded(
      child: Column(
        children: [
          Icon(icon, color: Theme.of(context).colorScheme.primary, size: 28),
          const SizedBox(height: 8),
          Text(
            value,
            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(fontSize: 11, color: Colors.white38),
          ),
        ],
      ),
    );
  }

  Widget _buildSection(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 8),
          child: Text(
            title,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: Colors.white54,
            ),
          ),
        ),
        Card(
          child: Column(children: children),
        ),
      ],
    );
  }

  Widget _buildTile(
    String title,
    String subtitle,
    IconData icon,
    VoidCallback? onTap, {
    Widget? trailing,
  }) {
    return ListTile(
      leading: Icon(icon, color: Colors.white54),
      title: Text(title),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 12, color: Colors.white38)),
      trailing: trailing ?? (onTap != null ? const Icon(Icons.chevron_right, color: Colors.white38) : null),
      onTap: onTap,
    );
  }

  Widget _buildToggleTile(
    String title,
    String subtitle,
    IconData icon,
    bool value,
    ValueChanged<bool> onChanged,
  ) {
    return SwitchListTile(
      secondary: Icon(icon, color: Colors.white54),
      title: Text(title),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 12, color: Colors.white38)),
      value: value,
      onChanged: onChanged,
    );
  }

  Future<void> _clearCache() async {
    setState(() => _isClearing = true);
    try {
      await CloneService.cleanupCache();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Cache cleared successfully')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    }
    if (mounted) setState(() => _isClearing = false);
  }

  Future<void> _deleteAllClones() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete All Clones?'),
        content: const Text(
          'This will uninstall ALL cloned apps and remove their records. This cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete All', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      final clones = CloneService.getAllClones();
      for (final clone in clones) {
        await CloneService.deleteClone(clone);
      }
      ref.read(clonesProvider.notifier).refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('All clones deleted')),
        );
      }
    }
  }

  Future<void> _exportBackup() async {
    setState(() => _isExporting = true);
    try {
      await BackupService.shareBackup();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Backup exported successfully')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Export failed: $e')),
        );
      }
    }
    if (mounted) setState(() => _isExporting = false);
  }

  Future<void> _importBackup() async {
    setState(() => _isImporting = true);
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.any,
        allowMultiple: false,
      );

      if (result == null || result.files.isEmpty) {
        if (mounted) setState(() => _isImporting = false);
        return;
      }

      final filePath = result.files.first.path;
      if (filePath == null) {
        if (mounted) setState(() => _isImporting = false);
        return;
      }

      // Show backup info first
      final info = await BackupService.getBackupInfo(filePath);

      if (!mounted) return;

      final confirmed = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Import Backup'),
          content: Text(
            'This backup contains ${info.cloneCount} clone(s).\n'
            'Exported: ${info.exportedAt.toString().substring(0, 16)}\n\n'
            'Existing clones with the same package name will be skipped.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Import'),
            ),
          ],
        ),
      );

      if (confirmed == true) {
        final imported = await BackupService.importBackup(filePath);
        ref.read(clonesProvider.notifier).refresh();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('$imported clone(s) imported')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Import failed: $e')),
        );
      }
    }
    if (mounted) setState(() => _isImporting = false);
  }
}
