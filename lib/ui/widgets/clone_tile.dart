import 'package:flutter/material.dart';
import 'package:app_cloner/models/clone_info.dart';
import 'package:app_cloner/services/clone_service.dart';

class CloneTile extends StatelessWidget {
  final CloneInfo clone;

  const CloneTile({super.key, required this.clone});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: () => _launchClone(context),
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              // Clone icon
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  gradient: LinearGradient(
                    colors: [
                      Theme.of(context).colorScheme.primary.withOpacity(0.3),
                      Theme.of(context).colorScheme.secondary.withOpacity(0.3),
                    ],
                  ),
                ),
                child: const Icon(Icons.copy_all, color: Colors.white70),
              ),
              const SizedBox(width: 12),

              // Clone info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      clone.displayName,
                      style: const TextStyle(
                        fontWeight: FontWeight.w500,
                        fontSize: 15,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'From: ${clone.originalAppName}',
                      style: const TextStyle(fontSize: 12, color: Colors.white54),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      clone.clonePackage,
                      style: const TextStyle(fontSize: 10, color: Colors.white38),
                    ),
                  ],
                ),
              ),

              // Actions
              PopupMenuButton<String>(
                icon: const Icon(Icons.more_vert, color: Colors.white38),
                onSelected: (val) => _handleAction(context, val),
                itemBuilder: (_) => [
                  const PopupMenuItem(value: 'launch', child: Text('Launch')),
                  const PopupMenuItem(value: 'delete', child: Text('Delete')),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _launchClone(BuildContext context) async {
    try {
      final launched = await CloneService.launchClone(clone);
      if (!launched && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Clone not installed. Please reinstall.')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    }
  }

  void _handleAction(BuildContext context, String action) async {
    switch (action) {
      case 'launch':
        _launchClone(context);
        break;
      case 'delete':
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('Delete Clone?'),
            content: Text('This will uninstall "${clone.displayName}" from your device.'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Cancel'),
              ),
              TextButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('Delete', style: TextStyle(color: Colors.red)),
              ),
            ],
          ),
        );
        if (confirmed == true) {
          await CloneService.deleteClone(clone);
        }
        break;
    }
  }
}
