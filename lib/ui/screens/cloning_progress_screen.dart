import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/models/app_info.dart';
import 'package:app_cloner/providers/clone_provider.dart';
import 'package:app_cloner/services/clone_service.dart';

class CloningProgressScreen extends ConsumerStatefulWidget {
  final AppInfo appInfo;
  final String cloneName;

  const CloningProgressScreen({
    super.key,
    required this.appInfo,
    required this.cloneName,
  });

  @override
  ConsumerState<CloningProgressScreen> createState() => _CloningProgressScreenState();
}

class _CloningProgressScreenState extends ConsumerState<CloningProgressScreen> {
  String _status = 'Preparing...';
  double _progress = 0.0;
  bool _isComplete = false;
  bool _hasError = false;
  String _errorMessage = '';

  @override
  void initState() {
    super.initState();
    _startCloning();
  }

  Future<void> _startCloning() async {
    try {
      await CloneService.cloneApp(
        appInfo: widget.appInfo,
        customName: widget.cloneName,
        onProgress: (status, progress) {
          if (mounted) {
            setState(() {
              _status = status;
              _progress = progress;
            });
          }
        },
      );

      if (mounted) {
        setState(() {
          _isComplete = true;
          _status = 'Clone created successfully!';
          _progress = 1.0;
        });
        ref.read(clonesProvider.notifier).refresh();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage = e.toString();
          _status = 'Cloning failed';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cloning'),
        automaticallyImplyLeading: _isComplete || _hasError,
      ),
      body: WillPopScope(
        onWillPop: () async => _isComplete || _hasError,
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Status icon
              _buildStatusIcon(),
              const SizedBox(height: 32),

              // App name
              Text(
                widget.appInfo.appName,
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Clone: ${widget.cloneName}',
                style: const TextStyle(
                  fontSize: 14,
                  color: Colors.white54,
                ),
              ),
              if (widget.appInfo.hasSplitApks) ...[
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    color: Colors.orange.withOpacity(0.15),
                  ),
                  child: Text(
                    'Split APK (${widget.appInfo.splitApkPaths?.length ?? 0} splits)',
                    style: const TextStyle(fontSize: 12, color: Colors.orange),
                  ),
                ),
              ],
              const SizedBox(height: 32),

              // Progress bar
              if (!_isComplete && !_hasError) ...[
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: LinearProgressIndicator(
                    value: _progress,
                    minHeight: 8,
                    backgroundColor: const Color(0xFF2A2A3E),
                    valueColor: AlwaysStoppedAnimation<Color>(
                      Theme.of(context).colorScheme.primary,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // Status text
              Text(
                _status,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 14,
                  color: _hasError ? Colors.red : Colors.white70,
                ),
              ),

              if (_hasError) ...[
                const SizedBox(height: 8),
                Text(
                  _errorMessage,
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 12, color: Colors.red),
                ),
              ],

              const SizedBox(height: 32),

              // Action buttons
              if (_isComplete) ...[
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton(
                    onPressed: () {
                      Navigator.of(context).popUntil((route) => route.isFirst);
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Theme.of(context).colorScheme.primary,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: const Text('Done'),
                  ),
                ),
              ],
              if (_hasError) ...[
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: OutlinedButton(
                    onPressed: () {
                      setState(() {
                        _hasError = false;
                        _errorMessage = '';
                        _progress = 0;
                        _status = 'Retrying...';
                      });
                      _startCloning();
                    },
                    style: OutlinedButton.styleFrom(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: const Text('Retry'),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusIcon() {
    if (_isComplete) {
      return Container(
        width: 100,
        height: 100,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: Colors.green.withOpacity(0.1),
        ),
        child: const Icon(Icons.check_circle, color: Colors.green, size: 64),
      );
    }
    if (_hasError) {
      return Container(
        width: 100,
        height: 100,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: Colors.red.withOpacity(0.1),
        ),
        child: const Icon(Icons.error, color: Colors.red, size: 64),
      );
    }
    return SizedBox(
      width: 100,
      height: 100,
      child: CircularProgressIndicator(
        strokeWidth: 6,
        valueColor: AlwaysStoppedAnimation<Color>(
          Theme.of(context).colorScheme.primary,
        ),
      ),
    );
  }
}
