# 🗞️ GhostGram

**GhostGram** is a professional, high-end Telegram client designed for focus, productivity, and absolute privacy. It transforms your messaging experience into a unified "Morning Edition" newspaper, allowing you to consume updates without the distractions of traditional chat interfaces.

---

## 🌟 Key Features

### 📰 The Gazette Feed
Messages from your selected groups and channels are aggregated into a professional editorial layout.
*   **Article Cards**: Content is presented as news articles rather than chat bubbles.
*   **Drop Caps & Serif Typography**: A premium "newsprint" aesthetic for a high-quality reading experience.
*   **Dateline Headers**: Every dispatch is professionally time-stamped and categorized.

### 🕵️ 100% Stealth Mode
Prioritizes your privacy by eliminating all presence telemetry.
*   **Read-only Access**: Messaging and outgoing interactions are disabled to ensure your "Online" status is never broadcast.
*   **Presence Protection**: Core TDLib integration enforces a permanent offline state.
*   **Zero Footprint**: Browse your feeds with the peace of mind that your activity is invisible to others.

### 🎯 Focus & Productivity
*   **Radar Keywords**: Highlight critical updates (e.g., "urgent", "deadline") to ensure you never miss what matters.
*   **Zen Timer**: Integrated focus sprint timer to help you stay on task while reading.
*   **Isolation Mode**: Blocks out the noise of non-added chats, showing you only what you've explicitly allowed.

### 🛠️ Advanced Customization
*   **Granular Scaling**: Accessibility text scaling ranging from 40% to 250%.
*   **Dynamic Theming**: Full support for Dark and Light modes with a specialized "Newsprint" light theme.
*   **Organized Control**: A streamlined settings menu for effortless configuration.

---

## 🚀 Getting Started

### Prerequisites
*   Android 8.0+ (Oreo) or later.
*   A Telegram account.
*   Telegram API credentials (`API_ID` and `API_HASH`).

### Setup
1.  Obtain your API ID and Hash from [my.telegram.org](https://my.telegram.org).
2.  Configure your environment variables in the **Secrets panel** in AI Studio or a `.env` file:
    *   `TELEGRAM_API_ID`: Your Telegram API ID.
    *   `TELEGRAM_API_HASH`: Your Telegram API Hash.
3.  Launch the app and sign in with your phone number.

---

## 🏗️ Building from Source

This project uses **Gradle** with Kotlin DSL.

```bash
# Build the Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew test
```

Download the latest automated debug APK for your device:

| Device architecture | APK |
| --- | --- |
| Most Android phones (64-bit ARM) | [arm64-v8a](https://github.com/SeriousRasCode/GhostGram/releases/latest/download/GhostGram-arm64-v8a-debug.apk) |
| Older Android phones (32-bit ARM) | [armeabi-v7a](https://github.com/SeriousRasCode/GhostGram/releases/latest/download/GhostGram-armeabi-v7a-debug.apk) |
| 64-bit x86 emulator | [x86_64](https://github.com/SeriousRasCode/GhostGram/releases/latest/download/GhostGram-x86_64-debug.apk) |
| 32-bit x86 emulator | [x86](https://github.com/SeriousRasCode/GhostGram/releases/latest/download/GhostGram-x86-debug.apk) |
| Unsure which one to use | [Universal APK](https://github.com/SeriousRasCode/GhostGram/releases/latest/download/GhostGram-debug.apk) |

Each push to `main` or `master` publishes these builds. CI generates a fresh debug signing key for each build, so uninstall an older CI build before installing a newer one.

---

## 🛡️ Privacy Policy
GhostGram is a privacy-first application. It does not track user behavior, and all communication with Telegram servers happens directly from your device using the official TDLib library. Your credentials are never shared with third parties.

---

Built with ❤️ by **Tedros Teshome** | Software Engineer
