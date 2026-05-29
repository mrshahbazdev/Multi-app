class AppConstants {
  static const String appName = 'App Cloner';
  static const String clonePrefix = 'com.clone';
  static const String keystorePassword = 'cloner_ks_2024';
  static const String keystoreAlias = 'clone_key';
  static const int maxFreeClones = 3;

  // MethodChannel
  static const String channelName = 'com.appcloner/native';

  // Methods
  static const String methodGetApps = 'getInstalledApps';
  static const String methodGetAppIcon = 'getAppIcon';
  static const String methodCloneApp = 'cloneApp';
  static const String methodInstallApk = 'installApk';
  static const String methodUninstallApp = 'uninstallApp';
  static const String methodLaunchApp = 'launchApp';
  static const String methodIsAppInstalled = 'isAppInstalled';
  static const String methodGetApkSize = 'getApkSize';
  static const String methodCleanupCache = 'cleanupCache';
  static const String methodGetStorageInfo = 'getStorageInfo';

  // Clone status
  static const String statusExtracting = 'extracting';
  static const String statusModifying = 'modifying';
  static const String statusSigning = 'signing';
  static const String statusInstalling = 'installing';
  static const String statusComplete = 'complete';
  static const String statusFailed = 'failed';
}
