# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Flutter
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# App Cloner native
-keep class com.appcloner.app.** { *; }

# Google Mobile Ads (AdMob) & Play Services
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# Play Core (Flutter deferred components / split install) - not bundled
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# apksig (runtime signing of cloned APKs)
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# BouncyCastle (keystore + certificate generation)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
