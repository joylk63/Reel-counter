# Reel Counter — Android (Nothing OS inspired)

A local-first Android app that tracks scrolling activity in Instagram and Facebook Lite and shows a small floating count bubble.

## Features
- Separate counters for Instagram and Facebook Lite
- Approximate automatic counting from vertical accessibility scroll events
- Small floating overlay near the top of the screen while either target app is foregrounded
- Manual +1 / −1 correction in the main app
- Daily target, per-platform limit alert, 30-day history and 7-day report
- Nothing OS-inspired monochrome UI with lime accent
- No account login and no network permission

## Build APK
1. Open this repository in Android Studio.
2. Use JDK 17 and install Android SDK Platform 35 if prompted.
3. Let Gradle sync complete and select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Install the resulting `app-debug.apk` on your Android phone.

GitHub Actions also builds a debug APK on every push to `main`. Open the **Actions** tab, choose the latest **Android APK Build** run, and download the `reel-counter-debug-apk` artifact after the run succeeds.

## First-run setup for automatic counter
1. Open Reel Counter and grant **Display over other apps** when prompted (if the app offers it, otherwise open Android Settings → Apps → Special app access → Display over other apps → Reel Counter).
2. Open Android Settings → Accessibility → Installed/Downloaded apps → **Reel Counter Auto Counter**, then enable it.
3. Open Instagram or Facebook Lite. A small floating bubble should show the count for the current app.
4. Disable the accessibility service in the same Settings page whenever you want to stop automatic counting.

Names of Android settings vary by phone. On some Android versions, notification permission is also needed for usage-limit alerts.

## Important accuracy limitation
Instagram and Facebook Lite do not provide a public API/event that reliably announces “the next Reel started.” This app therefore uses Android accessibility **vertical scroll events** as a heuristic. It can count ordinary feed scrolls as well as Reels, and some Reel transitions may not produce a scroll event. It is an approximate counter, not a guaranteed exact Reel detector. The floating bubble is shown while Instagram or Facebook Lite is active, not exclusively on the Reel viewer. No screen text or account data is transmitted by this app.

Counts are stored locally in app preferences. Clearing app data or uninstalling the app removes them.
