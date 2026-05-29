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
              // Clone icon with gradient
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
                child: Stack(
                  children: [
                    const Center(
                      child: Icon(Icons.copy_all, color: Colors.white70),
                    ),
                    // Clone index badge
                    Positioned(
                      right: 0,
                      bottom: 0,
                      child: Container(
                        width: 18,
                        height: 18,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: Theme.of(context).colorScheme.primary,
                          border: Border.all(color: const Color(0xFF1A1A2E), width: 1.5),
                        ),
                        child: Center(
                          child: Text(
                            '${clone.cloneIndex}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 9,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
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
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            clone.clonePackage,
                            style: const TextStyle(fontSize: 10, color: Colors.white38),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    // Time ago
                    Text(
                      _timeAgo(clone.createdAt),
                      style: const TextStyle(fontSize: 10, color: Colors.white24),
                    ),
                  ],
                ),
              ),

              // Actions
              PopupMenuButton<String>(
                icon: const Icon(Icons.more_vert, color: Colors.white38),
                onSelected: (val) => _handleAction(context, val),
                itemBuilder: (_) => [
                  const PopupMenuItem(
                    value: 'launch',
                    child: ListTile(
                      leading: Icon(Icons.launch, size: 20),
                      title: Text('Launch'),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'info',
                    child: ListTile(
                      leading: Icon(Icons.info_outline, size: 20),
                      title: Text('App Info'),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'delete',
                    child: ListTile(
                      leading: Icon(Icons.delete, size: 20, color: Colors.red),
                      title: Text('Delete', style: TextStyle(color: Colors.red)),
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _timeAgo(DateTime date) {
    final diff = DateTime.now().difference(date);
    if (diff.inMinutes < 1) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return '${date.day}/${date.month}/${date.year}';
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
      case 'info':
        _showCloneInfo(context);
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

  void _showCloneInfo(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(clone.displayName),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _infoRow('Original App', clone.originalAppName),
            _infoRow('Original Package', clone.originalPackage),
            _infoRow('Clone Package', clone.clonePackage),
            _infoRow('Clone Index', '#${clone.cloneIndex}'),
            _infoRow('Version', clone.originalVersionName),
            _infoRow('Created', clone.createdAt.toString().substring(0, 16)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 100,
            child: Text(
              label,
              style: const TextStyle(fontSize: 12, color: Colors.white54),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}
