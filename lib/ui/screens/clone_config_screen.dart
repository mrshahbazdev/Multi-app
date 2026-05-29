import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:app_cloner/models/app_info.dart';
import 'package:app_cloner/services/clone_service.dart';
import 'package:app_cloner/services/icon_service.dart';
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
  Color _selectedColor = IconService.iconColors[0];
  Uint8List? _customIconBytes;
  bool _useCustomIcon = false;
  SplitApkDetails? _splitDetails;
  bool _loadingSplitInfo = false;

  @override
  void initState() {
    super.initState();
    _cloneIndex = CloneService.getNextCloneIndex(widget.appInfo.packageName);
    _nameController = TextEditingController(
      text: '${widget.appInfo.appName} Clone $_cloneIndex',
    );
    _selectedColor = IconService.getDefaultColor(_cloneIndex);

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
      if (mounted) setState(() => _loadingSplitInfo = false);
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
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w600,
                            ),
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

            // Split APK details
            if (widget.appInfo.hasSplitApks) ...[
              _buildSplitDetailsCard(),
              const SizedBox(height: 16),
            ],

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
            const SizedBox(height: 24),

            // Icon customization
            _buildIconSection(),
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
          child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
        ),
      );
    }

    final details = _splitDetails;
    if (details == null || !details.isSplit) {
      return const SizedBox.shrink();
    }

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
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                color: Colors.orange.withOpacity(0.08),
              ),
              child: const Row(
                children: [
                  Icon(Icons.info_outline, size: 16, color: Colors.orange),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'All splits will be merged into a single APK for cloning',
                      style: TextStyle(fontSize: 11, color: Colors.orange),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            ...details.splits.map((split) => Padding(
                  padding: const EdgeInsets.only(bottom: 6),
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
                          overflow: TextOverflow.ellipsis,
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
    switch (type.toUpperCase()) {
      case 'BASE': return Icons.apps;
      case 'ABI': return Icons.memory;
      case 'DENSITY': return Icons.photo_size_select_large;
      case 'LOCALE': return Icons.language;
      case 'FEATURE': return Icons.extension;
      default: return Icons.insert_drive_file;
    }
  }

  Color _splitTypeColor(String type) {
    switch (type.toUpperCase()) {
      case 'BASE': return Colors.blue;
      case 'ABI': return Colors.green;
      case 'DENSITY': return Colors.purple;
      case 'LOCALE': return Colors.teal;
      case 'FEATURE': return Colors.amber;
      default: return Colors.grey;
    }
  }

  Widget _buildIconSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Clone Icon',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: Colors.white54,
          ),
        ),
        const SizedBox(height: 12),

        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                // Icon preview
                Row(
                  children: [
                    // Original icon
                    Column(
                      children: [
                        const Text(
                          'Original',
                          style: TextStyle(fontSize: 11, color: Colors.white38),
                        ),
                        const SizedBox(height: 4),
                        Container(
                          width: 56,
                          height: 56,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(14),
                            color: const Color(0xFF2A2A3E),
                          ),
                          child: widget.appInfo.iconBytes != null
                              ? ClipRRect(
                                  borderRadius: BorderRadius.circular(14),
                                  child: Image.memory(widget.appInfo.iconBytes!, fit: BoxFit.cover),
                                )
                              : const Icon(Icons.android, color: Colors.white38),
                        ),
                      ],
                    ),
                    const SizedBox(width: 16),
                    const Icon(Icons.arrow_forward, color: Colors.white38),
                    const SizedBox(width: 16),

                    // Clone icon preview
                    Column(
                      children: [
                        const Text(
                          'Clone',
                          style: TextStyle(fontSize: 11, color: Colors.white38),
                        ),
                        const SizedBox(height: 4),
                        _buildCloneIconPreview(),
                      ],
                    ),

                    const Spacer(),

                    // Pick custom icon button
                    Column(
                      children: [
                        OutlinedButton.icon(
                          onPressed: _pickCustomIcon,
                          icon: const Icon(Icons.image, size: 18),
                          label: const Text('Custom', style: TextStyle(fontSize: 12)),
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                          ),
                        ),
                        if (_useCustomIcon)
                          TextButton(
                            onPressed: () {
                              setState(() {
                                _useCustomIcon = false;
                                _customIconBytes = null;
                              });
                            },
                            child: const Text('Reset', style: TextStyle(fontSize: 11)),
                          ),
                      ],
                    ),
                  ],
                ),

                const SizedBox(height: 16),
                const Divider(height: 1),
                const SizedBox(height: 16),

                // Color picker
                const Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    'Badge Color',
                    style: TextStyle(fontSize: 12, color: Colors.white38),
                  ),
                ),
                const SizedBox(height: 8),
                SizedBox(
                  height: 36,
                  child: ListView.builder(
                    scrollDirection: Axis.horizontal,
                    itemCount: IconService.iconColors.length,
                    itemBuilder: (context, index) {
                      final color = IconService.iconColors[index];
                      final isSelected = _selectedColor == color;
                      return GestureDetector(
                        onTap: () => setState(() => _selectedColor = color),
                        child: Container(
                          width: 36,
                          height: 36,
                          margin: const EdgeInsets.only(right: 8),
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: color,
                            border: isSelected
                                ? Border.all(color: Colors.white, width: 2.5)
                                : null,
                          ),
                          child: isSelected
                              ? const Icon(Icons.check, size: 18, color: Colors.white)
                              : null,
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCloneIconPreview() {
    return Stack(
      children: [
        Container(
          width: 56,
          height: 56,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            color: const Color(0xFF2A2A3E),
          ),
          child: _useCustomIcon && _customIconBytes != null
              ? ClipRRect(
                  borderRadius: BorderRadius.circular(14),
                  child: Image.memory(_customIconBytes!, fit: BoxFit.cover),
                )
              : widget.appInfo.iconBytes != null
                  ? ClipRRect(
                      borderRadius: BorderRadius.circular(14),
                      child: ColorFiltered(
                        colorFilter: ColorFilter.mode(
                          _selectedColor.withOpacity(0.2),
                          BlendMode.srcATop,
                        ),
                        child: Image.memory(widget.appInfo.iconBytes!, fit: BoxFit.cover),
                      ),
                    )
                  : const Icon(Icons.android, color: Colors.white38),
        ),
        Positioned(
          right: -2,
          bottom: -2,
          child: Container(
            width: 22,
            height: 22,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: _selectedColor,
              border: Border.all(color: const Color(0xFF1A1A2E), width: 2),
            ),
            child: Center(
              child: Text(
                '$_cloneIndex',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _pickCustomIcon() async {
    final picker = ImagePicker();
    final image = await picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 512,
      maxHeight: 512,
    );

    if (image == null) return;

    final bytes = await image.readAsBytes();
    setState(() {
      _customIconBytes = bytes;
      _useCustomIcon = true;
    });
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
