<p align="center">
  <img src="assets/app-icon-source.png" alt="Blocker icon" width="120" />
</p>

# Blocker `v1.0.6.9`

> **Current Version:** `v1.0.6.9` | **Platform:** Android 8.0+ (API 26+) | **License:** Open Source

A privacy-focused, zero-telemetry Android distraction and content blocker built with Pure Kotlin, Jetpack Compose, a native C++ matching engine, and Android Accessibility Services.

> [!IMPORTANT]
> **Recommendation: Install the Debug Version First!**  
> We strongly recommend installing and testing the **Debug version** (`Blocker-debug-1.0.6.9.apk`) before installing the Release build.  
> - **Test with 3-Second Timers:** The Debug build uses short **3-second** unlock delay timers, allowing you to freely explore, configure your custom blocklists, test focus schedules, and adjust settings without risk of accidentally locking yourself out.  
> - **Upgrade When Ready:** Once you have thoroughly tested the app and confirmed everything suits your exact needs, proceed to install the **Release version** (`Blocker-release-1.0.6.9.apk`), which enforces real **1 to 30 day** / 24-hour covenant delayed-unlock timers with strict anti-tamper security and no backdoors.

> [!WARNING]
> **Play Protect Warning During Installation**  
> Because Blocker is an open-source, sideloaded app that utilizes Android Accessibility Services and Device Administrator permissions for tamper resistance, Google Play Protect may warn or block installation:  
> - **Quick Install:** On the prompt, tap **"More details"** -> **"Install anyway"**.  
> - **If Blocked:** Temporarily disable Play Protect during installation:  
>   1. Open **Google Play Store** -> tap your **Profile Icon** (top right) -> select **Play Protect**.  
>   2. Tap the **Settings gear** in the top right corner.  
>   3. Turn off **"Scan apps with Play Protect"**.  
>   4. Install Blocker, then you can turn Play Protect back on once installation finishes.

---

Blocker runs entirely on-device: no remote DNS, no tracking servers, no telemetry, and no account required. It blocks distracting websites, full-screen short-form video players (YouTube Shorts, Instagram Reels, Facebook Reels), and adult domains across browsers and apps.

---

## What's New in v1.0.6.9

- **Strict Focus Mode App Blocking & Exact System Whitelist:**
  - Enforced instantaneous (0ms) app blocking during manual Focus, Daily Schedules, and Prayer Focus sessions: any non-allowed application is kicked to Home with an instant blackout shield and counted in daily blocked statistics.
  - Eliminated all risky `contains("android")` substring matching in favor of an exact system whitelist (`isStrictCoreSystemExempt`): only exact `"com.android.systemui"`, exact `"android"`, Blocker package, default launcher, active IMEs, and dialers are exempt. Settings (`com.android.settings`) is blocked during Focus Mode unless explicitly whitelisted or launching permission intents.
  - Added dual-engine enforcement: runs on every Accessibility event and backed by a 250ms foreground watchdog (`BlockerForegroundService`) to ensure non-allowed apps cannot stay in the foreground.
- **Dedicated Distraction Shields & Clean Page Hierarchy:**
  - Cleaned up the **Focus** page to strictly house Focus Session Status/Timer, Daily Schedules, Allowed Essential Apps Whitelist, and Focus Notification Suppressor.
  - Social media and short-form distraction controls are unified on the **Home** Executive Dashboard:
    - **Separate Instagram Shields:** Independent toggles for **"Block Instagram Reels"** (`block_insta_reels`) and **"Block Instagram Explore"** (`block_insta_search`).
    - **Complete Facebook Video & Reels Shield:** Blocks both Facebook Reels and all playing Facebook Videos (Watch/Video tabs, dedicated video player classes `VideoPlayer`, `FullScreenVideo`, `WatchFeed`, `VideoHome`, `Tahoe`, etc., active playback controls `"Pause video"`, `"Enter fullscreen"`, `"Rewind 10 seconds"`, and click-to-play thumbnails) while **100% preserving Facebook Stories** (`isFacebookStoryViewerOrStoryClick` evaluated first).
    - **YouTube Shorts Shield:** Rapid ejection from Shorts player back to subscriptions or timeline.
- **Focus Mode Notification Bar Turn-off Time:**
  - Streamlined the notification panel during active Focus sessions: removed CPU-draining chronometer ticking and progress bars in favor of a clean, quiet ongoing notification: `"Focus Mode Active  •  Focus mode will be off at [time]"` which dismisses automatically upon completion.
- **Onboarding Keep-Alive & Unrestricted Battery Usage Option:**
  - Added an **"Unrestricted Battery Usage"** row to the initial permission setup with title (`maxLines = 1`), subtitle, and lifecycle intent tracking.
  - Enhanced permission lifecycle so the app never accidentally closes or minimizes when navigating to Android Settings during onboarding, requiring core permissions before the final **"Start Protection & Enter Blocker"** button.
- **3D Tactile Card Architecture & Visual Design:**
  - Modernized the UI with multi-stop vertical beveled borders and purple ambient/spot colored shadows (`ambientColor = Color.Black, spotColor = Color(0xFF7C3AED)`).
  - Upgraded the Home screen with an Executive Dashboard, 3-tile bento, and recessed 3D well for the Daily Blocked Activity chart.
- **Daily App Usage Studio & Usage Limits (`App Blocker` Tab):**
  - Added a 3-way switcher: **"Block Apps"**, **"Usage Limits"**, and **"Daily Usage"** with interactive screen time bar charts and top app breakdown.
- **Local Backup & Restore (`Settings` Tab):**
  - Added local encrypted/checksummed backup and restore for focus schedules, allowed apps, and custom blocking rules.
- **Incognito & SafeSearch Guard (`Web` Tab):**
  - Real-time Stage-0 browser inspection preventing private tabs and enforcing SafeSearch parameters across all major Android browsers.

---

## Core Features

- **Pomodoro Study Mode (Full Phone Lockdown):** Seamlessly integrated into the Focus page with a segmented switcher.
  - **Customizable Cycles:** Set study focus intervals (15m to 180m), rest breaks (3m to 15m), and session rounds (1 to 6).
  - **Aggressive Phone Lockdown:** All apps, home launcher, settings, and navigation are blocked during study sessions, returning directly to the running study timer within milliseconds.
  - **Quick Settings & Status Bar Shield:** Automatically collapses notification shade pulls (`StatusBarManager.collapsePanels()`), preventing unauthorized toggling of Wi-Fi, mobile data, or airplane mode.
  - **Complete Silencing & Do Not Disturb:** Automatically activates Do Not Disturb (`INTERRUPTION_FILTER_NONE`) and zeroes ringtone, media, notification, and system volume streams, cleanly restoring prior volumes upon completion.
  - **Interactive Wi-Fi Controls:** Monitor Wi-Fi status and toggle network connectivity on demand directly within Blocker without breaking lockdown.
  - **Display Standby Friendly:** Safely turn off the screen using the physical power button; unlocking returns straight to the active timer.
  - **Emergency Safeguard:** Dialer and emergency telecom services are explicitly whitelisted for critical safety.
- **Anti-Tamper & Clock Manipulation Defense:**
  - **Date & Time Tamper Shield:** Dual-layer protection (instant window interception + continuous screen text scanner) prevents users from manipulating device date/time in Android Settings to prematurely expire delayed unlock timers.
  - **System Settings Guard:** Strictly blocks access to Developer Options, Device Administrator settings, and App Info / Force Stop / Uninstall screens.
  - **Debugging Lockdown:** Automatically detects and disables USB and Wireless debugging if enabled.
- **Hardened Content & Distraction Shields:**
  - **URL User-Info Domain Spoofing Defense:** Hardened domain extraction parser (`hostOf`) strips user credentials (`user@host`) and decodes percent-encoded characters, eliminating bypasses like `https://safe.org@blocked-domain.com`.
  - **Granular Distraction Shields:** Independent toggles for YouTube Shorts, Instagram Reels, and Facebook Reels without breaking main app feeds.
  - **Offline Content Database:** Fast, local, obfuscated adult domain database with custom domain blocklists and keyword whitelisting.
- **In-App Updater & Security Verification:**
  - **Automated Update Checks:** Checks for official GitHub releases on an active 2-hour cycle (with 24-hour backoff if an update is pending) with in-app changelog viewer and direct APK installer.
  - **Package Identity Verification:** Enforces strict package name validation (`pi.packageName == rc.packageName`) before invoking Android package installer on downloaded APKs.
  - **URL Sanitization:** Release links are strictly validated against official GitHub repositories.
  - **Automatic Storage Cleanup:** Purges cached APK files immediately upon successful installation.
- **Delayed Unlock Timers:** Any attempt to weaken protections, unblock apps, or shorten timers requires waiting out an unbreakable delay of 24 hours to 30 days.

---

## Screenshots

<table>
  <tr>
    <td align="center" width="33%">
      <img src="ScreenShots/home.jpg" width="220" alt="Home dashboard" /><br/>
      <b>Home Dashboard</b><br/>
      <sub>Master protection switch, 7-day blocked activity chart, live counters, and system health.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/focus-mode1.jpg" width="220" alt="Focus mode and custom duration" /><br/>
      <b>Focus Mode &amp; Custom Timer</b><br/>
      <sub>Preset durations, manual Hours/Minutes inputs with steppers, and daily focus schedules.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/focus-mode-2.jpg" width="220" alt="Focus mode app whitelist" /><br/>
      <b>Focus App Whitelist</b><br/>
      <sub>Searchable app picker with User, System, and All Apps filters for permitted apps.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="ScreenShots/focus-study-mode.jpg" width="220" alt="Study mode lockdown" /><br/>
      <b>Study Mode (Lockdown)</b><br/>
      <sub>Full phone lockout with motivational quote, DND/Wi-Fi quick controls, and study cycles.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/focus-prayer-time.jpg" width="220" alt="Prayer time Salah focus mode" /><br/>
      <b>Prayer Time (Salah) Focus</b><br/>
      <sub>Daily Fajr–Isha and Friday Jumu'ah focus windows with live countdown and Auto-DND sync.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/focus-reels-shots-blocker.jpg" width="220" alt="Distraction and short-form shield" /><br/>
      <b>Short-Form Shields</b><br/>
      <sub>Eject YouTube Shorts, Instagram Reels, Explore, and Facebook Reels while keeping chats open.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="ScreenShots/apps-blocking.jpg" width="220" alt="Apps and packages blocking" /><br/>
      <b>Apps &amp; Usage Limits</b><br/>
      <sub>Track daily screen time per app, enforce daily usage caps, or block distracting apps outright.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/web-filtering.jpg" width="220" alt="Web and keywords filtering" /><br/>
      <b>Web &amp; Keywords</b><br/>
      <sub>Default 9,357-site blocklist, custom websites/keywords, and built-in C++ Block Engine Simulator.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/settings-1.jpg" width="220" alt="Settings and security covenant lock" /><br/>
      <b>Settings &amp; Covenant Lock</b><br/>
      <sub>24-hour protection delay period, master password parental lock, and battery exemption status.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" colspan="3">
      <img src="ScreenShots/settings-2.jpg" width="220" alt="Admin shield, language, and app updates" /><br/>
      <b>Admin Shield, Language &amp; Updater</b><br/>
      <sub>Device Admin &amp; Power-Off lock, English/বাংলা reminders, and built-in GitHub release updater.</sub>
    </td>
  </tr>
</table>

---

## Architecture Overview

```text
Blocker/
├── source/                  # Source files (working directory)
│   ├── App.tsx              # Main entry point and bottom tab navigation
│   ├── android/             # Native Kotlin code
│   │   ├── AccessibilityService.kt   # System-wide window tracker & content interceptor
│   │   ├── BlockerNativeModule.kt    # React Native bridge, delay locks & APK updater
│   │   ├── DeviceAdmin.kt            # Device Administrator receiver
│   ├── src/                 # React Native UI & Business logic
│   │   ├── screens/         # Screens (HomeScreen, AppBlockerScreen, BlocklistScreen, FocusScreen, SettingsScreen, UpdateModal)
│   │   ├── services/        # State management (ProtectionManager, BlocklistEngine, UpdateService)
│   │   ├── hooks/           # Reactive hooks (useProtection, useAppUpdate)
│   │   ├── native/          # TypeScript bridge definitions (BlockerNative)
│   │   └── data/            # Static data (appVersion, categories, userLists)
├── blocklists/              # Blocklist source files
│   ├── block-list.md        # Obfuscated adult domain, keyword, and TLD database
│   ├── my_list.txt          # Plaintext user additions (1 per line)
│   └── whitelist.txt        # Plaintext whitelist entries (1 per line)
├── assets/                  # App icon source artwork (app-icon-source.png)
├── build_apk.sh             # Main automated build script
├── 05_install_on_phone.sh   # ADB install script for connected device
├── import_lists.py          # Blocklist parser & userLists generator
├── patch_android.py         # Copies source into React Native project and patches Android files
├── set_version.py           # Auto-increments version and build numbers
└── env.sh                   # Environment variables (Android SDK, Java paths)
```

---

## Prerequisites & Required Packages

Building Blocker requires:

1. **Operating System:** Linux (Arch Linux recommended; Debian/Ubuntu supported with equivalent packages)
2. **Java Development Kit:** JDK 17 (OpenJDK 17)
3. **Node.js:** Node.js LTS (v18 or v20) and `npm`
4. **Android SDK & Build Tools:**
   - Android SDK Platform 34 (`platforms;android-34`)
   - Build Tools 34.0.0 (`build-tools;34.0.0`)
   - NDK 25.1.8937393 (`ndk;25.1.8937393`)
   - CMake 3.22.1 (`cmake;3.22.1`)
   - Android Command-Line Tools (`cmdline-tools;latest`)
   - Android Platform Tools (`adb`)
5. **Python:** Python 3.8+ with standard library (used by build helper scripts)
6. **System Utilities:** `git`, `unzip`, `bash`

### Package Installation Commands

#### Arch Linux:
```bash
sudo pacman -S --needed jdk17-openjdk nodejs npm android-tools unzip git python
sudo archlinux-java set java-17-openjdk
```

#### Ubuntu / Debian:
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk nodejs npm adb unzip git python3
sudo update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java
```

---

## Building the APK

The primary build pipeline is controlled by `build_apk.sh`.

### 1. Configure Environment (`env.sh`)

Ensure `env.sh` points to your installed Android SDK and Java 17 locations:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export ANDROID_HOME="$HERE/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

If you do not have the Android SDK command-line tools yet, place `commandlinetools-linux-*_latest.zip` directly in the `Blocker-App` directory (or `~/Downloads`). You can also place `android-ndk-r25b-linux.zip` and `gradle-8.3-all.zip` in `Blocker-App` to skip large downloads over the network. The build script automatically detects and sets them up.

### 2. Build Commands

#### Debug Build (Recommended First Step):
```bash
./build_apk.sh debug
```
* Uses **3-second** delay timers for protection unlocks so you do not get locked out while testing.
* Automatically imports blocklists, increments the build number, patches Android project files, and compiles.
* Outputs the binary to: `apk/Blocker-debug-1.0.6.9.apk`.

#### Release Build (When Ready for Real Protection):
```bash
./build_apk.sh release
```
* Enforces real **24-hour / 1 to 30 day** delayed-unlock timers.
* Strict anti-tamper security: settings changes cannot be bypassed without waiting the full duration.
* Outputs the binaries to: `apk/Blocker-release-1.0.6.9.apk` and `apk/Blocker-debug-1.0.6.9.apk`.

### 3. Install on Device

#### Direct APK Sideloading (Phone):
1. Copy or download the compiled APK to your Android device.
2. Open the file to start installation.
3. If **Google Play Protect** blocks the install:
   - Tap **"More details"** -> **"Install anyway"**.
   - If blocked completely, open **Google Play Store** -> tap your **Profile** (top right) -> **Play Protect** -> tap **Settings** -> temporarily turn off **"Scan apps with Play Protect"** while installing. You can turn it back on after installation.

---

## How to Modify and Customize

### 1. Editing App Logic & UI
All application development takes place under the `source/` folder:
* **UI Screens:** Edit `source/src/screens/` (`HomeScreen.tsx`, `SettingsScreen.tsx`, `FocusScreen.tsx`, `UpdateModal.tsx`, etc.).
* **Component Styling:** Edit `source/src/ui.tsx` for shared components, colors, and design system.
* **Native Android Interception:** Edit `source/android/AccessibilityService.kt` to modify window state detection, URL bar checking, or anti-tamper logic.
* **Native Modules & Updater:** Edit `source/android/BlockerNativeModule.kt` to modify Kotlin methods exposed to React Native.

**Important:** Do **not** edit directly inside `project/ (or $BLOCKER_DIR)` - that directory is the build target. Always edit inside `source/`. The build script copies files from `source/` into `project/ (or $BLOCKER_DIR)` automatically during the build process.

To manually sync changes from `source/` into the build target without a full rebuild:
```bash
python3 patch_android.py ~/Blocker source
```

### 2. Adding or Modifying Blocklists
* **Custom Additions:** Open `blocklists/my_list.txt` and add one domain or keyword per line.
* **Whitelist Items:** Open `blocklists/whitelist.txt` and add entries (e.g., words like `camera` or `essex` that contain false-positive substrings).
* After updating text lists, regenerate the TypeScript database:
  ```bash
  python3 import_lists.py
  ```

### 3. Changing the App Icon
* Replace `assets/app-icon-source.png` with your desired PNG image (recommended 1024x1024).
* Regenerate all Android mipmap icon resolutions:
  ```bash
  python3 generate_icons.py
  python3 embed_icon.py
  ```

---

## Required Android Permissions

Once installed on a physical device, Blocker requires the following permissions to provide full protection:
1. **Accessibility Service:** Required to inspect active window activities, detect open URL bars in web browsers, scan text for blocked keywords, detect short-form video players (YouTube Shorts, Reels), and enforce Study Mode phone lockdown.
2. **Display Over Other Apps (Overlay):** Used to display the non-intrusive blocked notification banner and reminder screen.
3. **Usage Access (Package Usage Stats):** Required to calculate daily per-app usage time and enforce daily app limits.
4. **Device Administrator:** Prevents immediate uninstallation while Protection Mode is active.
5. *(Optional)* **Do Not Disturb (Notification Policy Access):** Allows Pomodoro Study Mode to automatically suppress all notifications, alerts, and ringtones during focus sessions.
6. *(Optional)* **Write Secure Settings:** Allows Blocker to automatically and instantly turn off USB & Wireless debugging if enabled (`adb shell pm grant com.blocker android.permission.WRITE_SECURE_SETTINGS`).

---

## Detailed Release History

For complete release notes, changelogs, and historical version details, see [`RELEASE_NOTES.md`](RELEASE_NOTES.md).

---

## License & Credits

* **Developer:** [Shell Ninja](https://github.com/shell-ninja)
