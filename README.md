# WhatsApp Notification & Media Logger (Grey Hat Edition)

A powerful Android tool designed for personal use to intercept and archive WhatsApp messages and media, even if the sender uses "Delete for Everyone" or "View Once".

## 🚀 Capabilities

- **Notification Logging**: Captures every WhatsApp notification text instantly and saves it to a local Room database.
- **"Unsent" Recovery**: Detects when a user deletes a message. Because the original text was saved upon receipt, the log remains visible even after it disappears from WhatsApp.
- **Media Interception (The Race Condition Hack)**: 
    - Monitors WhatsApp's media directories in real-time using `FileObserver`.
    - Automatically copies incoming images/videos to a "Vault" (`Pictures/WhatsAppVault`) before WhatsApp can execute a deletion command.
    - Supports SD Card detection for external storage.
- **In-Chat Scraping**: Uses an `AccessibilityService` to "read" the screen. This ensures messages are captured even when you are actively chatting (where notifications don't usually appear).
- **View Once Support**: Identifies "View Once" notifications and archives the metadata so you know exactly when they were received.

## 🛠 Tech Stack

- **Language**: Kotlin
- **Database**: Room (SQLite)
- **Background Processing**: Foreground Service + Coroutines
- **System Hooks**: NotificationListenerService, AccessibilityService, FileObserver

## 📦 How to Install / Get the APK

1. **Build the APK**:
   - Open the project in **Android Studio**.
   - Go to `Build` > `Build Bundle(s) / APK(s)` > `Build APK(s)`.
   - Once finished, click **"Locate"** in the popup to find `app-debug.apk`.
2. **Transfer to Phone**: Send the `.apk` file to your device.
3. **Grant Critical Permissions**:
   - **Notification Access**: Required to log background messages.
   - **Accessibility Service**: Required to scrape in-chat messages.
   - **All Files Access**: Required for the Media Interceptor to "win" the race against WhatsApp's delete command.

## 🔄 Updating the App

To ensure the app updates (preserving your database) rather than performing a fresh install:
1. Keep the **Package Name** identical (`com.example.notificationlogger`).
2. Increase the `versionCode` in `app/build.gradle.kts` for every new build.
3. Use the same computer to build the APK (to maintain the same debug signing key).

---

### ⚠️ Disclaimer
*This project is for educational and personal use only. It bypasses standard app isolation and privacy features. It is not intended for distribution on the Google Play Store.*
