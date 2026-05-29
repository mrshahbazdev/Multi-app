import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/models/clone_info.dart';
import 'package:app_cloner/services/clone_service.dart';

// All clones
final clonesProvider = StateNotifierProvider<ClonesNotifier, List<CloneInfo>>((ref) {
  return ClonesNotifier();
});

class ClonesNotifier extends StateNotifier<List<CloneInfo>> {
  ClonesNotifier() : super([]) {
    loadClones();
  }

  void loadClones() {
    state = CloneService.getAllClones();
  }

  void refresh() {
    loadClones();
  }
}

// Cloning progress
final cloningProgressProvider = StateProvider<CloningProgress?>((ref) => null);

class CloningProgress {
  final String status;
  final double progress;
  final String appName;

  CloningProgress({
    required this.status,
    required this.progress,
    required this.appName,
  });
}
