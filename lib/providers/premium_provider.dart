import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_cloner/services/premium_service.dart';

/// Premium tier state, backed by [PremiumService] (Hive).
final premiumProvider =
    StateNotifierProvider<PremiumNotifier, PremiumState>((ref) {
  return PremiumNotifier();
});

class PremiumState {
  final bool isPremium;
  final int rewardedClones;

  const PremiumState({
    required this.isPremium,
    required this.rewardedClones,
  });

  int get freeCloneAllowance => PremiumService.freeCloneAllowance;
  bool get shouldShowAds => !isPremium;

  PremiumState copyWith({bool? isPremium, int? rewardedClones}) => PremiumState(
        isPremium: isPremium ?? this.isPremium,
        rewardedClones: rewardedClones ?? this.rewardedClones,
      );
}

class PremiumNotifier extends StateNotifier<PremiumState> {
  PremiumNotifier()
      : super(PremiumState(
          isPremium: PremiumService.isPremium,
          rewardedClones: PremiumService.rewardedClones,
        ));

  Future<void> upgradeToPremium() async {
    await PremiumService.setPremium(true);
    state = state.copyWith(isPremium: true);
  }

  /// Restore / reset premium (e.g. for testing or "restore purchases").
  Future<void> setPremium(bool value) async {
    await PremiumService.setPremium(value);
    state = state.copyWith(isPremium: value);
  }

  Future<void> grantRewardedClone() async {
    await PremiumService.addRewardedClone();
    state = state.copyWith(rewardedClones: PremiumService.rewardedClones);
  }
}
