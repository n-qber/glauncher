# glauncher

A minimalist, bloat-free Android app launcher written in Kotlin. It displays a simple list of installed applications and launches them when tapped. Nothing more.

## Features

- **Minimalist List**: Displays installed launcher applications in an alphabetical list.
- **Direct Launch**: Tapping an item opens the application.
- **Auto Refresh**: Updates the list automatically when returning to the home screen.
- **Self-Updating**: Automatically checks GitHub Releases for new APK versions and prompts to update.
- **Home Screen Support**: Configured as an Android Home/Launcher app (`Intent.CATEGORY_HOME`).
- **Modern Package Visibility**: Properly configured for Android 11+ (API 30+) with package visibility queries.

## Tech Stack

- **Language**: Kotlin
- **Platform**: Android (minSdk 26, targetSdk 34, compileSdk 34)
- **UI**: AndroidX RecyclerView + AppCompat
- **Build System**: Gradle (Kotlin DSL)
- **Environment**: Nix (`shell.nix` with Android SDK 34, OpenJDK 17, and Gradle)

## Development Environment

Enter the Nix shell to get the Android SDK, JDK 17, and Gradle preconfigured:

```bash
nix-shell
```

## Building & Installing

### Build Debug APK

```bash
# Inside nix-shell or with Gradle installed:
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install to Connected Device / Emulator

```bash
./gradlew installDebug
```

Or using `adb`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

When you press the Home button on your Android device, select **GLauncher** and choose **Always** to set it as your default launcher.
