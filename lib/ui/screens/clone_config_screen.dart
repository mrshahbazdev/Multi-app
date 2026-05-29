import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/models/app_info.dart';
import 'package:app_cloner/providers/clone_provider.dart';
import 'package:app_cloner/services/clone_service.dart';
import 'package:app_cloner/ui/screens/cloning_progress_screen.dart';

class CloneConfigScreen extends ConsumerStatefulWidget {
  final AppInfo appInfo;

  const CloneConfigScreen({super.key, required this.appInfo});

  @override
  ConsumerState<CloneConfigScreen> createState() => _CloneConfigScreenState();
}

class _CloneConfigScreenState extends ConsumerState<CloneConfigScreen> {
  late TextEditingController _nameController;
  late int _cloneIndex;

  @override
  void initState() {
    super.initState();
    _cloneIndex = CloneService.getNextCloneIndex(widget.appInfo.packageName);
    _nameController = TextEditingController(
      text: '${widget.appInfo.appName} Clone $_cloneIndex',
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final existingClones = CloneService.getClonesForApp(widget.appInfo.packageName);

    return Scaffold(
      appBar: AppBar(title: const Text('Clone Settings')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // App Info Card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    // App icon
                    Container(
                      width: 64,
                      height: 64,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(16),
                        color: const Color(0xFF2A2A3E),
                      ),
                      child: widget.appInfo.iconBytes != null
                          ? ClipRRect(
                              borderRadius: BorderRadius.circular(16),
                              child: Image.memory(widget.appInfo.iconBytes!, fit: BoxFit.cover),
                            )
                          : const Icon(Icons.android, size: 32, color: Colors.white38),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            widget.appInfo.appName,
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            widget.appInfo.packageName,
                            style: const TextStyle(
                              fontSize: 12,
                              color: Colors.white38,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Row(
                            children: [
                              _infoChip('v${widget.appInfo.versionName}'),
                              const SizedBox(width: 8),
                              _infoChip(widget.appInfo.apkSizeFormatted),
                              if (widget.appInfo.hasSplitApks) ...[
                                const SizedBox(width: 8),
                                _infoChip('Split APK', color: Colors.orange),
                              ],
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Existing clones
            if (existingClones.isNotEmpty) ...[
              Text(
                'Existing Clones (${existingClones.length})',
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: Colors.white54,
                ),
              ),
              const SizedBox(height: 8),
              ...existingClones.map((c) => Card(
                    child: ListTile(
                      leading: const CircleAvatar(
                        backgroundColor: Color(0xFF2A2A3E),
                        child: Icon(Icons.copy, color: Colors.white54),
                      ),
                      title: Text(c.displayName),
                      subtitle: Text(
                        c.clonePackage,
                        style: const TextStyle(fontSize: 11, color: Colors.white38),
                      ),
                    ),
                  )),
              const SizedBox(height: 24),
            ],

            // Clone name
            const Text(
              'Clone Name',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: Colors.white54,
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _nameController,
              decoration: const InputDecoration(
                hintText: 'Enter clone name',
              ),
            ),
            const SizedBox(height: 32),

            // Clone button
            SizedBox(
              width: double.infinity,
              height: 56,
              child: ElevatedButton(
                onPressed: _startCloning,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.primary,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
                child: const Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.copy_all),
                    SizedBox(width: 8),
                    Text(
                      'Start Cloning',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _infoChip(String label, {Color? color}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: (color ?? Colors.white).withOpacity(0.1),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 11, color: color ?? Colors.white54),
      ),
    );
  }

  void _startCloning() {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) => CloningProgressScreen(
          appInfo: widget.appInfo,
          cloneName: _nameController.text,
        ),
      ),
    );
  }
}
