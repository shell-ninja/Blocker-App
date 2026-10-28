<p align="center">
  <img src="assets/app-icon-source.png" alt="Blocker icon" width="120" />
</p>

# Blocker `v1.0.6.5`

> **Current Version:** `v1.0.6.5` | **Platform:** Android 8.0+ (API 26+) | **License:** Open Source

A privacy-focused, zero-telemetry Android distraction and content blocker built with React Native and native Android Accessibility Services.

> [!IMPORTANT]
> **Recommendation: Install the Debug Version First!**  
> We strongly recommend installing and testing the **Debug version** (`Blocker-release-debug-{VERSION}.apk`) before installing the Release build.  
> - **Test with 1-Minute Timers:** The Debug build uses short **1-minute** unlock delay timers, allowing you to freely explore, configure your custom blocklists, test focus schedules, and adjust settings without risk of accidentally locking yourself out.  
> - **Upgrade When Ready:** Once you have thoroughly tested the app and confirmed everything suits your exact needs, proceed to install the **Release version** (`Blocker-release-{VERSION}.apk`), which enforces real **1 to 30 day** delayed-unlock timers with strict anti-tamper security and no backdoors.

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

## What's New in v1.0.6.5

- **Bilingual Blocker Popup Overlays (English & বাংলা):**
  - **Setup Prompt (Step 0):** On initial app launch, before configuring permissions or defaults, the user is asked whether they want blocker popup messages in English or বাংলা.
  - **Authentic Islamic Translations in Bengali:** Blocker popup overlays display reverent Quranic verses and Hadiths in authentic Bengali:
    - Main Alert: `"আল্লাহকে ভয় করুন"` (*Fear Allah*)
    - Subheading: `"আপনার আখিরাত ধ্বংস করবেন না"` (*Do not destroy your Akhirah*)
    - Action Buttons: `"আস্তাগফিরুল্লাহ"` (*Astaghfirullah*) / `"বুঝেছি"` (*Understood*) / `"পড়াশোনায় ফিরুন"` (*Back to Study*)
    - Surah Al-Alaq (96:14), Surah Al-Isra (17:36), Surah An-Nur (24:30), Surah An-Nisa (4:1), Surah Al-Isra (17:34), and Sahih al-Bukhari (6412).
  - **Dedicated Settings Card:** Users can change their popup language at any time in Settings.
  - **Scope Restriction:** Localization applies exclusively to the blocker popup overlays and warning dialogs; the main application UI and navigation remain in English.
  - **Zero Font Overhead:** Utilizes standard Android `TextView` HarfBuzz conjunct shaping with native system fonts.

- **Zero-Latency Press-and-Hold Defense (BlockerHero Philosophy):**
  - Blocks press-and-hold (long-press) exclusively for the Blocker app icon across all Android launchers (Transsion HiOS/XOS, Samsung OneUI, Pixel, MIUI, Nova, Lawnchair, etc.) to prevent dragging to uninstall or opening App Info shortcuts.
  - Other apps on the device remain 100% interactive (can be long-pressed, rearranged, uninstalled, or inspected normally).
  - Normal single-tap to open Blocker works instantly without hindrance.
  - Reduced `android:notificationTimeout` from `50ms` to `0ms` and enabled `TYPE_VIEW_LONG_CLICKED` in service configuration.
  - Instantaneous dual `GLOBAL_ACTION_BACK` + `GLOBAL_ACTION_HOME` aborts launcher context menus in sub-millisecond execution.
  - Intercepts clicks on "App info", "Uninstall", "Disable", "Force stop", and "Clear storage" in 0ms without debounce delay.

- **Instant Zero-Latency Browser Interception (BlockerHero Benchmark):**
  - **Pre-bundled Binary Blocklist Asset (`blocklist.bin`):** Pre-compiles all 9,355 adult domains and 59 keywords into an 81.6 KB flat binary embedded in APK assets. Extracted and memory-mapped (`mmap`) into C++ memory in 0.5ms on service start.
  - **Stage 0 Browser Gatekeeper:** Browser URL inspection runs at line 1 of `onAccessibilityEvent` ahead of any 75ms throttles, usage logging, or settings checks.
  - **Zero-IPC Event Source Fast Path (`ev.source`):** Directly reads the address bar node from the event source on URL updates, bypassing the expensive 15–35ms `rootInActiveWindow` Binder IPC traversal.
  - **100% Solid Obsidian Blackout Shield (`0xFF070512`):** Covers the entire screen in pure opaque obsidian in 0ms on the current VSYNC, eliminating photon bleed and transparency leaks.
  - **Zero-Delay Tab Ejection:** Immediate `GLOBAL_ACTION_BACK` at 0ms and a backup ejection at 80ms to eject blocked tabs instantly.
  - **600ms Transient Cooldown:** Absorbs browser transitions without leaving subsequent adult tabs unguarded.

- **Headless Native C++ Engine & Minimal Memory Footprint:**
  - Branchless FNV-1a 64-bit binary search and flat Aho-Corasick automaton with zero heap allocations on hot path.
  - React Native JS runtime (`reactNativeHost`) is lazily instantiated, keeping background RAM usage under 8MB on 1GB RAM devices.

---

## What's New in v1.0.6.4

- **Hardware Anti-Tamper Enforcement (`DISALLOW_SAFE_BOOT`):**
  - Fixes an anti-tamper loophole where restricted boot modes could be leveraged to bypass accessibility services and uninstall the app.
  - When provisioned as Device Owner (`adb shell dpm set-device-owner com.blocker/.MyDeviceAdminReceiver`), Blocker enforces `UserManager.DISALLOW_SAFE_BOOT` across startup, reboots, and service connections.
- **Scheduled Power Off Prevention & Auto-Cancellation:**
  - Prevents setting scheduled shutdowns (e.g. 1 minute later) to bypass locks or restart restrictions.
  - Intercepts and blocks access to "Scheduled power on/off", "Auto power off", and "Auto restart" settings across OEM skins (Infinix XOS, MediaTek, Samsung, Xiaomi, ColorOS).
  - Actively intercepts scheduled shutdown countdown dialogs, automatically presses "Cancel" / "Dismiss", and kicks to home to keep the phone powered on.
  - Freezes and hides scheduled power packages via Device Owner to stop RTC hardware alarms from triggering.
- **Prevent Power off & Restart Shield (2-Hour Cooldown):**
  - Actively intercepts and dismisses the on-screen Power Menu (Global Actions dialog) across OEM skins (Infinix XOS, Samsung OneUI, Pixel, MIUI, ColorOS), preventing impulsive power offs and restarts.
  - Can be toggled on immediately from Settings. Disabling requires waiting for a **2-hour delay timer** before the change can be confirmed.
- **Deactivate Device Owner with Delay Lock Protection:**
  - View Device Owner status directly in Settings.
  - Deactivate Device Owner when needed, protected behind the anti-tamper countdown delay timer if master protection, shield, or any lock is active. Cleanly clears restrictions and unregisters device ownership.

---

## What's New in v1.0.6.3

- **Unconditional App & Game Kick Defense:**
  - Fixed a bypass where rapidly launching a blocked app or game from recents or home screen could slip past due to the 700ms debounce guard. Kicking to home and back actions (`GLOBAL_ACTION_BACK` + `kickToHome()`) now run **unconditionally on 100% of detection events** with 0ms debounce.
- **Background Process Termination:**
  - Terminating background processes of blocked applications via `ActivityManager.killBackgroundProcesses(pkg)` immediately after kicking, preventing heavy native games (OpenGL/Vulkan/Unity) from remaining active in RAM or background.
- **Continuous Active-Window Watchdog:**
  - Added a 350ms continuous polling watchdog inspecting `rootInActiveWindow` and foreground packages, guaranteeing instant kicks even if a full-screen hardware-accelerated game silences accessibility event dispatches.
- **Top-Priority Accessibility Interception:**
  - Blocked app detection now takes immediate top priority in `onAccessibilityEvent` directly after interactive checks, avoiding any 75ms throttling or downstream view processing delays.

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
      <img src="ScreenShots/home-page.jpg" width="220" alt="Home page" /><br/>
      <b>Home</b><br/>
      <sub>Live blocking stats, master toggle, and permission health check.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/focus-page.jpg" width="220" alt="Focus mode" /><br/>
      <b>Focus Mode</b><br/>
      <sub>Instant distraction-free sessions with preset or custom timers.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/study-mode.jpg" width="220" alt="Study mode" /><br/>
      <b>Study Mode (Lockdown)</b><br/>
      <sub>Aggressive phone lockdown, custom intervals, DND, and quick settings shield.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="ScreenShots/set-daily-schedule.jpg" width="220" alt="Daily schedules" /><br/>
      <b>Daily Schedules</b><br/>
      <sub>Automate recurring focus blocks with precise time pickers.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/block-shots-reels.jpg" width="220" alt="Distraction shields" /><br/>
      <b>Distraction Shields</b><br/>
      <sub>Exit YouTube Shorts, Instagram Reels, and Facebook video feeds automatically.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/block-app-page.jpg" width="220" alt="App blocker" /><br/>
      <b>Apps &amp; Scans</b><br/>
      <sub>Block installed apps outright or exempt safe apps from screen scanning.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="ScreenShots/set-usage-limit.jpg" width="220" alt="Usage limit" /><br/>
      <b>Usage Limits</b><br/>
      <sub>Enforce maximum daily screen time per app protected by delay locks.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/check-blocked-page.jpg" width="220" alt="Web and keywords" /><br/>
      <b>Web &amp; Keywords</b><br/>
      <sub>Adult domain database, custom domain blocklist, and keyword whitelisting.</sub>
    </td>
    <td align="center" width="33%">
      <img src="ScreenShots/settings-page.jpg" width="220" alt="Settings page" /><br/>
      <b>Settings &amp; Shield</b><br/>
      <sub>Uninstall shield, wireless debugging locks, and 1-30 day delay timers.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" colspan="3">
      <img src="ScreenShots/update-app-feature.jpg" width="220" alt="App update feature" /><br/>
      <b>In-App Updater</b><br/>
      <sub>Automated 2-hour update checks, in-app changelog viewer, and direct APK installation.</sub>
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
* Uses **1-minute** delay timers for protection unlocks so you do not get locked out while testing.
* Automatically imports blocklists, increments the build number, patches React Native files, and compiles.
* Outputs the binary to: `apk/Blocker-debug-{build_number}.apk`.

#### Release Build (When Ready for Real Protection):
```bash
./build_apk.sh release
```
* Enforces real **1 to 30 day** delayed-unlock timers.
* Strict anti-tamper security: settings changes cannot be bypassed without waiting the full duration.
* Outputs the binary to: `apk/Blocker-release-{build_number}.apk`.

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
