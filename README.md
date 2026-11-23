<p align="center">
  <img src="assets/app-icon-source.png" alt="Blocker icon" width="120" />
</p>

# Blocker `v1.0.7`

> **Current Version:** `v1.0.7` | **Platform:** Android 8.0+ (API 26+) | **License:** Open Source

A privacy-focused, zero-telemetry Android distraction and content blocker built with Pure Kotlin, Jetpack Compose, a native C++ matching engine, and Android Accessibility Services.

> [!IMPORTANT]
> **Recommendation: Install the Debug Version First!**  
> We strongly recommend installing and testing the **Debug version** (`Blocker-debug-1.0.7.apk`) before installing the Release build.  
> - **Test with 3-Second Timers:** The Debug build uses short **3-second** unlock delay timers, allowing you to freely explore, configure your custom blocklists, test focus schedules, and adjust settings without risk of accidentally locking yourself out.  
> - **Upgrade When Ready:** Once you have thoroughly tested the app and confirmed everything suits your exact needs, proceed to install the **Release version** (`Blocker-release-1.0.7.apk`), which enforces real **1 to 30 day** / 24-hour covenant delayed-unlock timers with strict anti-tamper security and no backdoors.

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

## What's New in v1.0.7

- **Opt-in 10-Minute Temporary Accessibility Pause (Banking App Compatibility & Tamper Shield):**
  - **Permission Setup Opt-In:** Added an optional toggle during initial setup ("Temporary Accessibility Pause") to allow users to enable a 10-minute accessibility pause for banking applications that flag active accessibility services. Defaults to OFF; when OFF, the feature remains completely hidden from Settings.
  - **Strict 10-Minute Timer & Real-Time Countdown:** In Settings, users who opted in can trigger a 10-minute temporary accessibility pause with an active countdown display.
  - **Uncompromising Settings & Uninstall Shield:** While Accessibility is paused, a foreground guard service (`AccessibilityPauseGuardService`) runs with 100ms usage tracking, completely locking access to System Settings, PackageInstaller, Google Play Store, and OEM Security apps with touch-blocking overlays and instant Home redirects.
  - **Device Administrator Protection:** Intercepts deactivation requests while paused to prevent uninstall bypasses.
  - **Automatic & Mandatory Re-Enable:** At the exact 10-minute mark, `AccessibilityPauseExpiryReceiver` automatically restores accessibility (if `WRITE_SECURE_SETTINGS` is granted) or displays a persistent, unclosable re-enable prompt until Blocker Accessibility is reconnected.
- **Bulletproof OEM & Custom Home Launcher Protection (`SystemExemptions.kt` & `FocusEnforcer.kt`):**
  - Resolved an issue where Focus Mode app blocking incorrectly blocked OEM home launchers (including Infinix/Transsion's `com.transsion.XOSLauncher`, HiOS, HyperOS, OneUI, Pixel launcher, and 3rd-party launchers like Lawnchair and Nova).
  - Built a centralized single-source-of-truth system exemptions engine (`SystemExemptions.isNeverBlockPackage`):
    - Added API-33-safe `PackageManager.queryIntentActivities` & `resolveActivity` using `ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())`.
    - Added runtime cached per-package `CATEGORY_HOME` probing (`ConcurrentHashMap<String, Boolean>`) to automatically identify any launcher installed on the device.
    - Normalized all package evaluations to lowercase (`Locale.ROOT`) to eliminate case-sensitive comparison bugs on OEM packages (`com.transsion.XOSLauncher`).
    - Added built-in recognition for Transsion companion system services (`smartpanel`, `magiclauncher`, `desktop`, `zerohome`, `minusone`, `hiassistant`, `aivoiceassistant`, etc.).
    - Hardened the final execution sink gate with `SystemExemptions.isNeverBlockPackage(context, pkg)` to make accidental launcher blocking architecturally impossible.
  - Eliminated stale `UsageStatsManager` fallback false blocks following `GLOBAL_ACTION_HOME`:
    - Checks visible `TYPE_APPLICATION` windows in `AccessibilityService.windows` for active launchers before falling back to usage stats.
    - Enforces a 1,200ms grace period (`lastFocusHomeKickElapsedMs`) after kicking out an unallowed app so Focus Mode does not re-trigger during the Home transition animation.
    - Dynamic package-change `BroadcastReceiver` automatically clears probe caches and refreshes launcher definitions on package installs, updates, or removals.
- **Litho-Aware Facebook Video & Reels Guard (Zero Newsfeed Refresh Loops):**
  - Overhauled Facebook distraction detection in `FacebookReelsDetector.kt` using reverse-order Litho accessibility tree traversal (`childCount - 1 downTo 0`, cap 350 nodes) to detect full-screen Reels and playing videos without traversing deep horizontal feed posts.
  - Prioritized detection pipeline:
    - **Facebook Stories 100% Whitelisted:** Story trays and viewers (`"story"`, `"stories"`, `"'s story"`, `"add to story"`, `"your story"`, `StoryViewer`) are verified first and guaranteed uninterrupted.
    - **Dedicated Full-Screen Reels:** Verified by right-edge vertical action button stacks (`abs(dx) <= 52dp`, `abs(dy) >= 36dp`), top overlay controls, and bottom comment bars.
    - **Watch / Video Tabs:** Verified by top-bar tab navigation in the top 24% of the screen.
  - Non-looping inline pause: Pauses playing videos within the main Newsfeed without firing `GLOBAL_ACTION_BACK`, completely stopping feed reload loops, screen freezes, and duplicate overlays.
  - Single-back and delayed escalation for dedicated full-screen video players with 1,500ms debounce and 1,300ms auto-dismissing HUD banner.
  - Zero fallback to native C++ engine on Facebook packages, preventing false-positive triggers from feed timestamps and seekbars.
- **High-Craft UI Polish & Interaction Design:**
  - Standardized 3D card geometry across the app: `Elevated3DCard` and `Hero3DCard` updated with `RoundedCornerShape(22.dp)`, 1px beveled specular rim gradient, and balanced ambient/spot drop shadows (`0xFF7C3AED` 45% / `Color.Black` 35%). Standardized `Recessed3DWell` with `RoundedCornerShape(14.dp)`.
  - Floating Cyber Bottom Dock upgraded with `Modifier.navigationBarsPadding()` for edge-to-edge system navigation bars, animated tab pill width (`40.dp` to `48.dp`), and animated icon/label tint transitions (`tween(180)`).
  - Smooth Compose tab crossfades: Wrapped root navigation switcher in `Crossfade(animationSpec = tween(180))` for seamless screen transitions.
  - Standardized `StatusPillBadge` typography with `11.5.sp`, `FontWeight.SemiBold`, subtle glowing status dot, and tabular numbers (`tnum`).
  - Refined `QuickShieldRow` on the Home screen with `42.dp` squircle pedestals, `20.dp` icons, `12.dp` spacing, and balanced subtitle line heights.
- **Clean Monotonic Upgrade Pipeline:**
  - Standardized APK deliverables: `Blocker-debug-1.0.7.apk` and `Blocker-release-1.0.7.apk` (clean naming without `-v` prefix).
  - Monotonic `versionCode` progression (`1007138` debug / `1107138` release, `versionName = "1.0.7"`) ensuring smooth in-place upgrades.

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
* Outputs the binary to: `apk/Blocker-debug-1.0.7.apk`.

#### Release Build (When Ready for Real Protection):
```bash
./build_apk.sh release
```
* Enforces real **24-hour / 1 to 30 day** delayed-unlock timers.
* Strict anti-tamper security: settings changes cannot be bypassed without waiting the full duration.
* Outputs the binaries to: `apk/Blocker-release-1.0.7.apk` and `apk/Blocker-debug-1.0.7.apk`.

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
