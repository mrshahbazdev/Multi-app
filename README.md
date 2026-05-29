# App Cloner - Android Multi App Clone

Kisi bhi Android app ka clone banao — alag package name, alag data, alag account. No root required!

## How It Works

```
Select App → Extract APK → Change Package Name → Re-Sign → Install Clone
```

APK ek ZIP file hai. Hum:
1. APK extract karte hain device se
2. AndroidManifest.xml mein package name change karte hain
3. Naye keystore se sign karte hain
4. Install karte hain — Android ise naye app ke taur pe treat karta hai!

## Features

- [x] Installed apps scan & list
- [x] Single APK cloning
- [x] Split APK support (App Bundles)
- [x] Custom clone name
- [x] Multiple clones per app
- [x] Clone management (launch, delete)
- [x] Dark theme UI
- [ ] Custom app icon
- [ ] Batch cloning
- [ ] Clone backup/export

## Tech Stack

| Component | Technology |
|-----------|-----------|
| UI | Flutter 3.x (Dart) |
| State | Riverpod 2.x |
| Database | Hive (local) |
| Native | Kotlin |
| APK Manipulation | Custom AXML parser |
| Signing | JAR v1 signing |

## Project Structure

```
lib/
├── main.dart                 # Entry point
├── app.dart                  # MaterialApp
├── core/
│   ├── constants.dart        # App constants, method names
│   └── theme.dart            # Dark/light themes
├── models/
│   ├── app_info.dart         # Installed app model
│   └── clone_info.dart       # Clone model (Hive)
├── providers/
│   ├── app_list_provider.dart  # App scanning state
│   └── clone_provider.dart     # Clone management state
├── services/
│   ├── native_bridge.dart    # MethodChannel calls
│   └── clone_service.dart    # Clone logic orchestration
└── ui/
    ├── screens/
    │   ├── home_screen.dart
    │   ├── app_picker_screen.dart
    │   ├── clone_config_screen.dart
    │   ├── cloning_progress_screen.dart
    │   └── settings_screen.dart
    └── widgets/
        ├── app_tile.dart
        └── clone_tile.dart

android/.../kotlin/
├── MainActivity.kt           # Flutter activity
├── NativeBridge.kt           # MethodChannel handler
├── AppScanner.kt             # PackageManager scanner
├── ApkExtractor.kt           # APK extraction
├── ApkModifier.kt            # AXML binary XML modifier
├── ApkSigner.kt              # APK signing (v1)
├── CloneInstaller.kt         # APK installer (Intent + PackageInstaller)
└── InstallReceiver.kt        # Install result handler
```

## Setup

```bash
# 1. Clone this repo
git clone <repo-url>
cd app_cloner

# 2. Get dependencies
flutter pub get

# 3. Generate Hive adapters
flutter pub run build_runner build

# 4. Run
flutter run
```

## Android Permissions Required

- `QUERY_ALL_PACKAGES` — Installed apps scan karne ke liye
- `REQUEST_INSTALL_PACKAGES` — Clone install karne ke liye
- `REQUEST_DELETE_PACKAGES` — Clone uninstall karne ke liye

## Known Limitations

1. **Signature-checking apps** — Apps jo apni signature check karte hain (e.g., banking apps) clone mein crash ho sakte hain
2. **GMS dependent apps** — Cloned apps ko alag Google account chahiye for push notifications
3. **Split APKs** — Kuch complex App Bundles properly modify nahi hote

## Roadmap

See `app-cloner-new-roadmap.md` for full development roadmap.

## License

MIT
