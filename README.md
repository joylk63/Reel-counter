# Reel Counter — Android (Nothing OS inspired)

A small Android app to manually track short-form videos/Reels watched in **Instagram** and **Facebook Lite**.

## Features
- Separate daily counters for Instagram and Facebook Lite
- Manual +1 and undo (−1)
- Daily target and usage-limit alert
- Last 30 days of local history
- 7-day weekly report and daily average
- Nothing OS-inspired monochrome UI with lime accent
- Local-only storage; no account login or network permission

## Build the APK
1. Download or clone this repository.
2. Open the repository folder in Android Studio.
3. Use JDK 17 and install Android SDK Platform 35 if prompted.
4. Let Gradle Sync finish.
5. Connect an Android phone with USB debugging enabled, or use an emulator; click **Run**.
6. To generate an APK, select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
7. Select **Locate** in the build notification, then transfer the APK to your phone and install it. Allow installation from that file manager when Android asks.

## Important behavior
This is a manual counter. Open the app after watching a Reel and tap the matching platform's **+1** button. It does not automatically read either app's private data, detect Reels, or run an accessibility service. The usage alert is triggered when a manual count reaches the chosen threshold; Android notification permission is needed on Android 13+.

Counts and settings stay in the app's local preferences. Clearing app data or uninstalling the app removes them.

## Requirements
- Android 8.0 (API 26) or newer
- Android Studio with JDK 17
- Android SDK Platform 35
