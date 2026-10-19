# glauncher (Grid Launcher)

A minimalist, ultra-fast 3×2 Grid Launcher for Android written in Kotlin.

Instead of endless scrolling or typing out full app names, GLauncher organizes your installed apps into **6 interactive circular buckets (3 rows × 2 columns)** that dynamically funnel down character by character with each tap.


## Screenshots
<img width="270" height="585" alt="Screenshot_20261008_142157_GLauncher" src="https://github.com/user-attachments/assets/a1c34c4e-1f07-4ba9-afa0-e1fd63ad943f" />
<img width="270" height="585" alt="Screenshot_20261008_142246_GLauncher" src="https://github.com/user-attachments/assets/c13c507b-1284-42f9-9fbe-9f20273a04f6" />
<img width="270" height="585" alt="Screenshot_20261008_142220_GLauncher" src="https://github.com/user-attachments/assets/6ddabea8-0008-4b4b-996e-37b1117c5160" />


## Why???
It took me time to open apps, and the issue with this is that in this small time window I would forget what I had to do. So opening apps was actually a focus intensive task ;-( Also, I realized I can type my number password on my phone without thinking (because it becomes automatic) so if I had a "code" for every app, this issue would go away, another point is that using linux I've had experience with "drun" launchers, where you have to type the app name to open it, and usually the scenario is that: you will *almost* ALWAYS know the full name of the app you want to open; So I mixed all of these points into this app. It may sound weird but I literally prefer using this now than any other launcher.

### Disadvantages:
- You lose the view of "all of the apps" that you usually have with native launcher


## Concept & How It Works

- **3×2 Grid (6 Circles)**: Your screen displays 6 large, easy-to-tap circular nodes.
- **Dynamic Letter Funneling**:
  - The entire app library is partitioned across the 6 circles by letter range (e.g. `A – E`, `F – I`, `J – M`, `N – R`, `S – U`, `V – Z`).
  - Tapping a circle narrows the candidate pool down to that range and updates the 6 circles with the next discriminating characters (e.g., `Ch – Cl`, `Co – Cr`).
  - Common prefixes are automatically advanced: for apps like *Mercado Livre* and *Mercado Pago*, it skips straight to differentiating between `L` and `P`!
- **Instant Launch on Single App**: As soon as a bucket contains only 1 app, it displays the actual **app icon & name**. Tapping it launches the app immediately.
- **Back & Reset Navigation**:
  - Tapping the back arrow or pressing your phone's back button steps back one level.
- **In-Memory Cache**:
  - Installed apps are loaded once into memory for 0ms interaction latency.
  - Automatically updates in the background when an app is installed, updated, or uninstalled via a system broadcast receiver.
- **Self-Updating**: Automatically checks GitHub Releases for new APK versions and prompts to update.

## Tech Stack

- **Language**: Kotlin
- **Platform**: Android (minSdk 26, targetSdk 34, compileSdk 34)
- **UI**: AndroidX AppCompat, custom SquareFrameLayout
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
