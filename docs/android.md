# Tempo for Android

Native Kotlin + Jetpack Compose app in `android/`. Same workouts, cues and history as the web app;
backups move between them (`docs/backup-format.md`).

## Build
```bash
cd android
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```
Needs JDK 17 and the Android SDK (`android/local.properties` points at `~/Android/Sdk`).

## Install on the phone (APK, free)
1. Copy `app-debug.apk` to the phone (USB, Google Drive, email to yourself…), or with USB debugging on:
   `~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Tap the file. Android asks once to allow installs from that app (Files/Drive): allow it.
3. Open Tempo. On the first Start, allow notifications (that's the live countdown).

Updates: install the new APK over the old one. Workouts and history stay, because the APK is
always signed with this laptop's debug key. If this laptop's `~/.android/debug.keystore` is ever
lost, export a backup first; a differently signed APK requires uninstalling.

## Battery settings (some phones)
Samsung, Xiaomi, OnePlus and others can kill background apps aggressively. If a workout ever
stops with the screen off: Settings → Apps → Tempo → Battery → **Unrestricted**.

## How it stays alive (ADR-002)
- `WorkoutService` is a `mediaPlayback` foreground service with a notification, so Android treats
  it like a music player and doesn't kill it with the screen off or the app swiped away.
- The workout is rendered into one WAV (same as the web app) and played by `MediaPlayer` with a
  partial wake lock. The player position is the only clock.
- A 200 ms ticker (runs inside the service) drives vibration, the notification and saving progress.
- The notification countdown is the system chronometer, so it ticks even when the app's code doesn't run.
- Phone call / other audio takes focus → pause; focus comes back → resume. Headphones unplugged → pause.
