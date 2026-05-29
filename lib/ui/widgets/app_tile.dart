import 'package:flutter/material.dart';
import 'package:app_cloner/models/app_info.dart';

class AppTile extends StatelessWidget {
  final AppInfo appInfo;
  final VoidCallback onTap;

  const AppTile({
    super.key,
    required this.appInfo,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              // App icon
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  color: const Color(0xFF2A2A3E),
                ),
                child: appInfo.iconBytes != null
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: Image.memory(appInfo.iconBytes!, fit: BoxFit.cover),
                      )
                    : const Icon(Icons.android, color: Colors.white38),
              ),
              const SizedBox(width: 12),

              // App info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      appInfo.appName,
                      style: const TextStyle(
                        fontWeight: FontWeight.w500,
                        fontSize: 15,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      appInfo.packageName,
                      style: const TextStyle(
                        fontSize: 11,
                        color: Colors.white38,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        _chip(appInfo.apkSizeFormatted),
                        const SizedBox(width: 6),
                        _chip('v${appInfo.versionName}'),
                        if (appInfo.hasSplitApks) ...[
                          const SizedBox(width: 6),
                          _chip('Split', color: Colors.orange),
                        ],
                      ],
                    ),
                  ],
                ),
              ),

              // Clone button
              const Icon(Icons.arrow_forward_ios, size: 16, color: Colors.white38),
            ],
          ),
        ),
      ),
    );
  }

  Widget _chip(String label, {Color? color}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(6),
        color: (color ?? Colors.white).withOpacity(0.08),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 10, color: color ?? Colors.white38),
      ),
    );
  }
}
