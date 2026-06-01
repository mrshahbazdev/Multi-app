import 'package:hive/hive.dart';
import 'package:app_cloner/core/constants.dart';

/// Local persistence + business rules for the Free / Premium tier.
///
/// NOTE: Unlocking premium here is a local placeholder. Real production
/// monetization should validate a Google Play Billing purchase (e.g. via the
/// `in_app_purchase` package) and call [setPremium] only after the purchase is
/// acknowledged. The rest of the app depends only on this abstraction, so
/// wiring real billing is a drop-in change.
class PremiumService {
  static Box get _box => Hive.box(AppConstants.settingsBox);

  /// Whether the user has unlocked premium.
  static bool get isPremium =>
      _box.get(AppConstants.prefIsPremium, defaultValue: false) as bool;

  /// Number of bonus clones earned by watching rewarded ads.
  static int get rewardedClones =>
      _box.get(AppConstants.prefRewardedClones, defaultValue: 0) as int;

  /// Effective clone allowance for free users (base limit + rewarded bonus).
  static int get freeCloneAllowance =>
      AppConstants.maxFreeClones + rewardedClones;

  /// Whether the user may create another clone given [currentCloneCount].
  static bool canCreateClone(int currentCloneCount) {
    if (isPremium) return true;
    return currentCloneCount < freeCloneAllowance;
  }

  /// Remaining clones available to a free user (null/unlimited for premium).
  static int? remainingFreeClones(int currentCloneCount) {
    if (isPremium) return null;
    final remaining = freeCloneAllowance - currentCloneCount;
    return remaining < 0 ? 0 : remaining;
  }

  /// Whether ads should be shown (premium users are ad-free).
  static bool get shouldShowAds => !isPremium;

  /// Unlock premium. See class note about real billing.
  static Future<void> setPremium(bool value) async {
    await _box.put(AppConstants.prefIsPremium, value);
  }

  /// Grant one bonus clone for watching a rewarded ad.
  static Future<void> addRewardedClone() async {
    await _box.put(AppConstants.prefRewardedClones, rewardedClones + 1);
  }
}
