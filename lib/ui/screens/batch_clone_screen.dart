import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/models/app_info.dart';
import 'package:app_cloner/providers/app_list_provider.dart';
import 'package:app_cloner/services/clone_service.dart';
import 'package:app_cloner/providers/clone_provider.dart';

/// Screen for selecting and cloning multiple apps at once.
class BatchCloneScreen extends ConsumerStatefulWidget {
  const BatchCloneScreen({super.key});

  @override
  ConsumerState<BatchCloneScreen> createState() => _BatchCloneScreenState();
}

class _BatchCloneScreenState extends ConsumerState<BatchCloneScreen> {
  final Set<String> _selectedPackages = {};
  bool _isCloning = false;
  int _currentIndex = 0;
  int _totalCount = 0;
  String _currentApp = '';
  String _currentStatus = '';
  double _currentProgress = 0.0;
  final List<_BatchResult> _results = [];

  @override
  Widget build(BuildContext context) {
    final appsAsync = ref.watch(installedAppsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(_isCloning
            ? 'Cloning ${_currentIndex + 1}/$_totalCount'
            : 'Batch Clone (${_selectedPackages.length} selected)'),
        actions: [
          if (!_isCloning && _selectedPackages.isNotEmpty)
            TextButton(
              onPressed: _clearSelection,
              child: const Text('Clear'),
            ),
        ],
      ),
      body: _isCloning ? _buildProgress() : _buildAppSelection(appsAsync),
      floatingActionButton: !_isCloning && _selectedPackages.isNotEmpty
          ? FloatingActionButton.extended(
              onPressed: () => _startBatchClone(appsAsync),
              icon: const Icon(Icons.copy_all),
              label: Text('Clone ${_selectedPackages.length} Apps'),
            )
          : null,
    );
  }

  Widget _buildAppSelection(AsyncValue<List<AppInfo>> appsAsync) {
    return appsAsync.when(
      data: (apps) {
        // Filter out system apps
        final userApps = apps.where((a) => !a.isSystemApp).toList();
        return Column(
          children: [
            // Select all / info bar
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  Text(
                    '${userApps.length} apps available',
                    style: const TextStyle(fontSize: 12, color: Colors.white38),
                  ),
                  const Spacer(),
                  TextButton(
                    onPressed: () {
                      setState(() {
                        if (_selectedPackages.length == userApps.length) {
                          _selectedPackages.clear();
                        } else {
                          _selectedPackages.addAll(userApps.map((a) => a.packageName));
                        }
                      });
                    },
                    child: Text(
                      _selectedPackages.length == userApps.length ? 'Deselect All' : 'Select All',
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                itemCount: userApps.length,
                itemBuilder: (context, index) {
                  final app = userApps[index];
                  final isSelected = _selectedPackages.contains(app.packageName);

                  return Card(
                    color: isSelected
                        ? Theme.of(context).colorScheme.primary.withOpacity(0.1)
                        : null,
                    child: CheckboxListTile(
                      value: isSelected,
                      onChanged: (_) => _toggleApp(app.packageName),
                      secondary: Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(10),
                          color: const Color(0xFF2A2A3E),
                        ),
                        child: app.iconBytes != null
                            ? ClipRRect(
                                borderRadius: BorderRadius.circular(10),
                                child: Image.memory(app.iconBytes!, fit: BoxFit.cover),
                              )
                            : const Icon(Icons.android, size: 24, color: Colors.white38),
                      ),
                      title: Text(
                        app.appName,
                        style: const TextStyle(fontSize: 14),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        '${app.apkSizeFormatted}${app.hasSplitApks ? ' • Split APK' : ''}',
                        style: const TextStyle(fontSize: 11, color: Colors.white38),
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        );
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, _) => Center(child: Text('Error: $err')),
    );
  }

  Widget _buildProgress() {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        children: [
          // Overall progress
          const SizedBox(height: 32),
          Text(
            'Batch Cloning',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          Text(
            '${_currentIndex + 1} of $_totalCount',
            style: const TextStyle(color: Colors.white54),
          ),
          const SizedBox(height: 24),

          // Overall progress bar
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: LinearProgressIndicator(
              value: _totalCount > 0 ? (_currentIndex + _currentProgress) / _totalCount : 0,
              minHeight: 8,
              backgroundColor: const Color(0xFF2A2A3E),
            ),
          ),
          const SizedBox(height: 24),

          // Current app
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text(
                    _currentApp,
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _currentStatus,
                    style: const TextStyle(fontSize: 13, color: Colors.white54),
                  ),
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: _currentProgress,
                      minHeight: 4,
                      backgroundColor: const Color(0xFF2A2A3E),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Results list
          Expanded(
            child: ListView.builder(
              itemCount: _results.length,
              itemBuilder: (context, index) {
                final result = _results[index];
                return ListTile(
                  leading: Icon(
                    result.success ? Icons.check_circle : Icons.error,
                    color: result.success ? Colors.green : Colors.red,
                  ),
                  title: Text(result.appName, style: const TextStyle(fontSize: 14)),
                  subtitle: result.success
                      ? null
                      : Text(
                          result.error ?? 'Unknown error',
                          style: const TextStyle(fontSize: 11, color: Colors.red),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                );
              },
            ),
          ),

          // Done button
          if (_currentIndex >= _totalCount && _results.isNotEmpty)
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: () {
                  ref.read(clonesProvider.notifier).refresh();
                  Navigator.of(context).popUntil((route) => route.isFirst);
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.primary,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                child: Text(
                  'Done (${_results.where((r) => r.success).length}/${_results.length} successful)',
                ),
              ),
            ),
        ],
      ),
    );
  }

  void _toggleApp(String packageName) {
    setState(() {
      if (_selectedPackages.contains(packageName)) {
        _selectedPackages.remove(packageName);
      } else {
        _selectedPackages.add(packageName);
      }
    });
  }

  void _clearSelection() {
    setState(() => _selectedPackages.clear());
  }

  Future<void> _startBatchClone(AsyncValue<List<AppInfo>> appsAsync) async {
    final apps = appsAsync.valueOrNull;
    if (apps == null) return;

    final selectedApps = apps
        .where((a) => _selectedPackages.contains(a.packageName))
        .toList();

    setState(() {
      _isCloning = true;
      _totalCount = selectedApps.length;
      _currentIndex = 0;
      _results.clear();
    });

    for (int i = 0; i < selectedApps.length; i++) {
      final app = selectedApps[i];

      setState(() {
        _currentIndex = i;
        _currentApp = app.appName;
        _currentStatus = 'Starting...';
        _currentProgress = 0.0;
      });

      try {
        await CloneService.cloneApp(
          appInfo: app,
          onProgress: (status, progress) {
            if (mounted) {
              setState(() {
                _currentStatus = status;
                _currentProgress = progress;
              });
            }
          },
        );

        _results.add(_BatchResult(appName: app.appName, success: true));
      } catch (e) {
        _results.add(_BatchResult(
          appName: app.appName,
          success: false,
          error: e.toString(),
        ));
      }

      if (mounted) setState(() {});
    }

    // Mark as complete
    if (mounted) {
      setState(() {
        _currentIndex = _totalCount;
        _currentStatus = 'All done!';
        _currentProgress = 1.0;
      });
    }
  }
}

class _BatchResult {
  final String appName;
  final bool success;
  final String? error;

  _BatchResult({required this.appName, required this.success, this.error});
}
