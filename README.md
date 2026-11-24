# Pocket Fake Call

> **Discreet meeting escape call triggered via double-tilt pocket gestures.**

An enterprise-grade, privacy-first Android application developed in modern Kotlin and Jetpack Compose/Views. Built with strict adherence to modern Android architecture, lifecycle awareness, and hardware sensor efficiency.

---

## 🚀 Key Features

- **Stealth gesture trigger**: Double-tilt or proximity sensor cover inside your pocket.
- Pixel-perfect incoming call UI mimicking official Android/OneUI phone dialer.
- Realistic ringtone audio playback and synchronized vibration patterns.
- Customizable caller name, number, avatar, and pre-recorded voice audio.

---

## 🛠️ Hardware & Sensor Architecture

* **Sensors & APIs:** `Accelerometer, Proximity Sensor, Vibrator, RingtoneManager`
* **Declared Permissions:** `VIBRATE, WAKE_LOCK, FOREGROUND_SERVICE, POST_NOTIFICATIONS`
* **Target SDK:** Android 14 (API 34)
* **Minimum SDK:** Android 8.0 (API 26+)
* **Architecture:** MVVM / Unidirectional Data Flow (UDF) with Coroutines & StateFlow

---

## 🔒 Privacy & Permission Transparency

This application is engineered under the **Zero Unnecessary Permissions** doctrine:
* **No Hidden Trackers:** Contains zero advertising SDKs or third-party behavioral analytics.
* **No Accessibility Privilege Misuse:** All background operations utilize sanctioned Android APIs (`WorkManager`, `ForegroundService`, `JobScheduler`).
* **Air-Gapped Operation:** Runs locally on your device hardware without requiring external cloud servers.

---

## 📦 Building and Running

### Prerequisites
* Android Studio Iguana / Jellyfish / Ladybug or newer
* Android SDK 34
* JDK 17+

### Build APK via CLI
```bash
# Clone the repository
git clone https://github.com/naik2571/Pocket-Fake-Call.git
cd Pocket-Fake-Call

# Build Debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Licensed under the [Apache License 2.0](LICENSE).
