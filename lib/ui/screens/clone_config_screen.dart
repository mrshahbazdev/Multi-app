import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/models/app_info.dart';
import 'package:app_cloner/providers/clone_provider.dart';
import 'package:app_cloner/services/clone_service.dart';
import 'package:app_cloner/services/native_bridge.dart';
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
  SplitApkDetails? _splitDetails;
  bool _loadingSplitInfo = false;

  @override
  void initState() {
    super.initState();
    _cloneIndex = CloneService.getNextCloneIndex(widget.appInfo.packageName);
    _nameController = TextEditingController(
      text: '${widget.appInfo.appName} Clone $_cloneIndex',
    );
    if (widget.appInfo.hasSplitApks) {
      _loadSplitInfo();
    }
  }

  Future<void> _loadSplitInfo() async {
    setState(() => _loadingSplitInfo = true);
    try {
      final details = await NativeBridge.getSplitInfo(widget.appInfo.packageName);
      if (mounted) {
        setState(() {
          _splitDetails = details;
          _loadingSplitInfo = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _loadingSplitInfo = false);
      }
    }
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
                            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            widget.appInfo.packageName,
                            style: const TextStyle(fontSize: 12, color: Colors.white38),
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
            const SizedBox(height: 16),

            // Split APK Details Card
            if (widget.appInfo.hasSplitApks) ...[
              _buildSplitDetailsCard(),
              const SizedBox(height: 16),
            ],

            // Existing clones
            if (existingClones.isNotEmpty) ...[
              Text(
                'Existing Clones (${existingClones.length})',
                style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.white54),
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
              const SizedBox(height: 16),
            ],

            // Clone name
            const Text(
              'Clone Name',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.white54),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _nameController,
              decoration: const InputDecoration(hintText: 'Enter clone name'),
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
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.copy_all),
                    const SizedBox(width: 8),
                    Text(
                      widget.appInfo.hasSplitApks ? 'Clone (Split APK)' : 'Start Cloning',
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                    ),
                  ],
                ),
              ),
            ),

            if (widget.appInfo.hasSplitApks) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  color: Colors.orange.withOpacity(0.1),
                  border: Border.all(color: Colors.orange.withOpacity(0.3)),
                ),
                child: const Row(
                  children: [
                    Icon(Icons.info_outline, color: Colors.orange, size: 20),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'This app uses Split APKs (App Bundle). All splits will be merged before cloning. This may take longer for large apps.',
                        style: TextStyle(fontSize: 12, color: Colors.orange),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSplitDetailsCard() {
    if (_loadingSplitInfo) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Center(
            child: SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          ),
        ),
      );
    }

    if (_splitDetails == null) return const SizedBox.shrink();

    final details = _splitDetails!;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.extension, color: Colors.orange, size: 20),
                const SizedBox(width: 8),
                Text(
                  'Split APK Details (${details.splitCount} splits)',
                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
                const Spacer(),
                Text(
                  details.totalSizeFormatted,
                  style: const TextStyle(fontSize: 12, color: Colors.white54),
                ),
              ],
            ),
            const SizedBox(height: 12),
            const Divider(height: 1, color: Colors.white12),
            const SizedBox(height: 8),
            ...details.splits.map((split) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    children: [
                      Icon(
                        _splitTypeIcon(split.type),
                        size: 16,
                        color: _splitTypeColor(split.type),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          split.fileName,
                          style: const TextStyle(fontSize: 12),
                        ),
                      ),
                      _infoChip(split.type, color: _splitTypeColor(split.type)),
                      const SizedBox(width: 8),
                      Text(
                        split.sizeFormatted,
                        style: const TextStyle(fontSize: 11, color: Colors.white38),
                      ),
                    ],
                  ),
                )),
          ],
        ),
      ),
    );
  }

  IconData _splitTypeIcon(String type) {
    switch (type) {
      case 'BASE': return Icons.apps;
      case 'ABI': return Icons.memory;
      case 'DENSITY': return Icons.photo_size_select_large;
      case 'LOCALE': return Icons.language;
      case 'FEATURE': return Icons.extension;
      default: return Icons.help_outline;
    }
  }

  Color _splitTypeColor(String type) {
    switch (type) {
      case 'BASE': return Colors.blue;
      case 'ABI': return Colors.green;
      case 'DENSITY': return Colors.purple;
      case 'LOCALE': return Colors.teal;
      case 'FEATURE': return Colors.amber;
      default: return Colors.grey;
    }
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
