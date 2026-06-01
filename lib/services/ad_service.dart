import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:app_cloner/services/premium_service.dart';

/// Central AdMob integration (banner / interstitial / rewarded).
///
/// Uses Google's official **test** ad unit IDs so the app runs without a real
/// AdMob account. Replace [_bannerUnitId], [_interstitialUnitId] and
/// [_rewardedUnitId] (and the AdMob App ID in AndroidManifest.xml) with your
/// production IDs before release.
class AdService {
  AdService._();
  static final AdService instance = AdService._();

  bool _initialized = false;
  InterstitialAd? _interstitialAd;
  RewardedAd? _rewardedAd;

  // ---- Test ad unit IDs (https://developers.google.com/admob/android/test-ads)
  static const String _testBanner = 'ca-app-pub-3940256099942544/6300978111';
  static const String _testInterstitial =
      'ca-app-pub-3940256099942544/1033173712';
  static const String _testRewarded =
      'ca-app-pub-3940256099942544/5224354917';

  // In release builds wire your real unit IDs here.
  static const bool _useTestAds = true;

  static String get _bannerUnitId =>
      _useTestAds ? _testBanner : 'YOUR_BANNER_AD_UNIT_ID';
  static String get _interstitialUnitId =>
      _useTestAds ? _testInterstitial : 'YOUR_INTERSTITIAL_AD_UNIT_ID';
  static String get _rewardedUnitId =>
      _useTestAds ? _testRewarded : 'YOUR_REWARDED_AD_UNIT_ID';

  static String get bannerUnitId => _bannerUnitId;

  /// Ads only run on mobile platforms and never for premium users.
  bool get adsSupported => !kIsWeb && (Platform.isAndroid || Platform.isIOS);

  Future<void> initialize() async {
    if (_initialized || !adsSupported) return;
    await MobileAds.instance.initialize();
    _initialized = true;
    preloadInterstitial();
    preloadRewarded();
  }

  // ---------------------------------------------------------- Interstitial
  void preloadInterstitial() {
    if (!_initialized || !PremiumService.shouldShowAds) return;
    InterstitialAd.load(
      adUnitId: _interstitialUnitId,
      request: const AdRequest(),
      adLoadCallback: InterstitialAdLoadCallback(
        onAdLoaded: (ad) => _interstitialAd = ad,
        onAdFailedToLoad: (_) => _interstitialAd = null,
      ),
    );
  }

  /// Show an interstitial if one is ready and the user is not premium.
  Future<void> showInterstitial() async {
    if (!PremiumService.shouldShowAds) return;
    final ad = _interstitialAd;
    if (ad == null) {
      preloadInterstitial();
      return;
    }
    ad.fullScreenContentCallback = FullScreenContentCallback(
      onAdDismissedFullScreenContent: (ad) {
        ad.dispose();
        _interstitialAd = null;
        preloadInterstitial();
      },
      onAdFailedToShowFullScreenContent: (ad, _) {
        ad.dispose();
        _interstitialAd = null;
        preloadInterstitial();
      },
    );
    await ad.show();
  }

  // -------------------------------------------------------------- Rewarded
  void preloadRewarded() {
    if (!_initialized) return;
    RewardedAd.load(
      adUnitId: _rewardedUnitId,
      request: const AdRequest(),
      rewardedAdLoadCallback: RewardedAdLoadCallback(
        onAdLoaded: (ad) => _rewardedAd = ad,
        onAdFailedToLoad: (_) => _rewardedAd = null,
      ),
    );
  }

  bool get isRewardedReady => _rewardedAd != null;

  /// Show a rewarded ad. [onReward] fires when the user earns the reward.
  /// Returns true if an ad was shown.
  Future<bool> showRewarded({required VoidCallback onReward}) async {
    final ad = _rewardedAd;
    if (ad == null) {
      preloadRewarded();
      return false;
    }
    ad.fullScreenContentCallback = FullScreenContentCallback(
      onAdDismissedFullScreenContent: (ad) {
        ad.dispose();
        _rewardedAd = null;
        preloadRewarded();
      },
      onAdFailedToShowFullScreenContent: (ad, _) {
        ad.dispose();
        _rewardedAd = null;
        preloadRewarded();
      },
    );
    await ad.show(onUserEarnedReward: (_, __) => onReward());
    return true;
  }

  void dispose() {
    _interstitialAd?.dispose();
    _interstitialAd = null;
    _rewardedAd?.dispose();
    _rewardedAd = null;
  }
}
