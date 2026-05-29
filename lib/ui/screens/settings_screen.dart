import 'package:flutter/material.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildSection('General', [
            _buildToggleTile(
              'Dark Mode',
              'Use dark theme',
              Icons.dark_mode,
              true,
              (val) {},
            ),
            _buildTile('Clear Cache', 'Remove temporary clone files', Icons.cleaning_services, () {}),
          ]),
          const SizedBox(height: 16),
          _buildSection('About', [
            _buildTile('Version', '1.0.0', Icons.info_outline, null),
            _buildTile('Developer', 'App Cloner Team', Icons.code, null),
            _buildTile('Rate Us', 'Rate on Play Store', Icons.star_outline, () {}),
            _buildTile('Privacy Policy', 'Read our privacy policy', Icons.privacy_tip_outlined, () {}),
          ]),
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

  Widget _buildTile(String title, String subtitle, IconData icon, VoidCallback? onTap) {
    return ListTile(
      leading: Icon(icon, color: Colors.white54),
      title: Text(title),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 12, color: Colors.white38)),
      trailing: onTap != null ? const Icon(Icons.chevron_right, color: Colors.white38) : null,
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
}
