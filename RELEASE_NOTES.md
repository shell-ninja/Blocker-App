# Blocker Release Notes

This document contains release notes, changelogs, and notable changes for all releases of Blocker.

---

## v1.0.6.5 (Current Release)

**Release Date:** October 2026  
**Version:** 1.0.6.5  
**Build:** 33

### Zero-Latency Press-and-Hold Defense & Destructive Shortcut Shield
- **0ms Instant Press-and-Hold Blocking on Launchers (BlockerHero Philosophy):**
  - Blocks press-and-hold (long-press) exclusively for the Blocker app icon across all Android launchers (Transsion HiOS/XOS, Samsung OneUI, Pixel, MIUI, Nova, Lawnchair, etc.).
  - Other apps on the device remain 100% interactive (can be long-pressed, rearranged, uninstalled, or inspected normally).
  - Normal single-tap to open Blocker works instantly without hindrance.
  - Eliminated OS-level delivery delay: reduced `android:notificationTimeout` from `50ms` to `0ms` and enabled `TYPE_VIEW_LONG_CLICKED` in service configuration.
  - Moved launcher detection to the very first line of `onAccessibilityEvent` for sub-millisecond evaluation with pure in-memory string matching (eliminating synchronous Binder IPC roundtrips).
  - Fires `GLOBAL_ACTION_BACK` and `GLOBAL_ACTION_HOME` in under a millisecond, collapsing any launcher popup/context menu and aborting drag gestures before drop targets or "App info" shortcuts can render.
- **Launcher Popup & Destructive Shortcut Intercept:**
  - If a launcher context menu or drop target ("App info", "Uninstall", "Delete", "Remove") is clicked, it is instantly intercepted and dismissed.
- **Settings App Info Destructive Action Shield:**
  - Intercepts clicks on "Uninstall", "Disable", "Force stop", "Clear storage", and "Deactivate" inside App Info in 0ms, preventing fast-finger bypasses.
  - Debounce delays removed from auto-cancel and kick actions so 100% of detection events execute immediately.

### Instant Zero-Latency Browser Interception (BlockerHero Benchmark)
- **Pre-bundled Binary Blocklist Asset (`blocklist.bin`):** Pre-compiles all 9,355 adult domains and 59 keywords into an 81.6 KB flat binary embedded in APK assets. Extracted and memory-mapped (`mmap`) into C++ memory in 0.5ms on service start with zero dependency on React Native JS runtime.
- **Stage 0 Browser Gatekeeper:** Browser URL inspection runs at line 1 of `onAccessibilityEvent` ahead of any 75ms throttles, usage logging, or settings checks.
- **Zero-IPC Event Source Fast Path (`ev.source`):** Directly reads the address bar node from the event source on URL updates, bypassing the expensive 15–35ms `rootInActiveWindow` Binder IPC traversal.
- **100% Solid Obsidian Blackout Shield (`0xFF070512`):** Covers the entire screen in pure opaque obsidian in 0ms on the current VSYNC, eliminating photon bleed and transparency leaks.
- **Zero-Delay Tab Ejection:** Replaced the legacy 300ms `goToGoogle` simulation (`ACTION_CLICK` -> 150ms -> `ACTION_SET_TEXT` -> 150ms -> `ACTION_IME_ENTER`) with immediate `GLOBAL_ACTION_BACK` at 0ms and a backup ejection at 80ms.
- **600ms Transient Cooldown:** Tuned `quietUntil` from 5000ms to 600ms so subsequent adult tabs are protected immediately.

### Headless Native C++ Engine & Memory Footprint Optimization
- **Mmap Flat Binary Search (`libblocker_engine.so`):** Compiled for `armeabi-v7a` (14KB) and `arm64-v8a` (20KB). Implements branchless FNV-1a 64-bit sorted domain binary search and flat Aho-Corasick automaton with zero heap allocations on hot path.
- **Zero-Copy JNI Primitive Passing:** Reusable pre-allocated `CharArray` buffers eliminate JVM garbage collection churn.
- **Decoupled Background Execution:** React Native JS host (`reactNativeHost`) is lazily instantiated, keeping background RAM usage under 8MB on 1GB RAM devices.

### Bilingual Blocker Popup Overlays (English & বাংলা)
- **Initial Language Selection (Step 0):** Users choose between English and বাংলা with live Quranic preview cards before setting up system permissions.
- **Dedicated Popup Messages Language Card in Settings:** Users can switch popup language at any time in `SettingsScreen.tsx`.
- **Authentic Islamic Translations in Bengali:**
  - Quranic verses (Surah Al-Alaq 96:14, Al-Isra 17:36, An-Nur 24:30, An-Nisa 4:1, Al-Isra 17:34) and Hadiths (Sahih al-Bukhari 6412) translated with reverent, accurate Islamic Bengali phrasing.
  - Headings (`"আল্লাহকে ভয় করুন"`), Subheadings (`"আপনার আখিরাত ধ্বংস করবেন না"`), and Buttons (`"আস্তাগফিরুল্লাহ"`, `"বুঝেছি"`, `"পড়াশোনায় ফিরুন"`).
- **Scope Restriction:** Localization strictly applies to blocker popup warning overlays; main application UI remains in English.
- **Native HarfBuzz Shaping:** Zero external font dependencies; standard Android `TextView` natively renders Bengali typography with zero memory overhead.

---

## v1.0.6.4
**Version:** 1.0.6.4  
**Build:** 32

### Hardware-Level Anti-Tamper & Power Lockdown
- **Hardware Anti-Tamper Enforcement (`DISALLOW_SAFE_BOOT` via Device Owner):**
  - Resolved hardware-level anti-tamper vulnerabilities where restricted boot modes could be leveraged to bypass protection.
  - When provisioned as Device Owner, Blocker automatically enforces `UserManager.DISALLOW_SAFE_BOOT` through `DevicePolicyManager.addUserRestriction()`.
  - Automatically verified and applied on device boot (`BootReceiver`), package update (`MY_PACKAGE_REPLACED`), Device Admin/Owner activation (`BlockerDeviceAdminReceiver`), app startup (`BlockerNativeModule`), Accessibility Service connection, and Foreground Service start (`BlockerForegroundService`).
  - Wrapped safely in error guards so regular Device Admin functionality remains fully intact and unaffected.
- **Scheduled Power Off Prevention & Auto-Cancellation:**
  - Prevents users from scheduling an automated shutdown (e.g. 1 minute later) to bypass delay timers or power restrictions.
  - Intercepts and blocks access to "Scheduled power on/off", "Auto power off", and "Auto restart" configuration screens across MediaTek (`com.mediatek.schpwronoff`), Transsion (Infinix/Tecno), Xiaomi, Samsung, and ColorOS.
  - Actively intercepts scheduled shutdown countdown dialogs, automatically clicks "Cancel" / "Dismiss", sends back commands, and kicks to home to prevent the phone from powering down.
  - When provisioned as Device Owner, automatically freezes and hides scheduled power-off packages so RTC hardware alarms cannot trigger.
- **Prevent Power off & Restart Shield (2-Hour Cooldown Delay):**
  - Added an active barrier in the Accessibility Service that immediately intercepts and dismisses the on-screen Power Menu (Global Actions dialog) across AOSP, Samsung, Transsion (Infinix/Tecno), Xiaomi, and ColorOS devices.
  - Instantly fires back/collapse actions the moment the power menu dialog is summoned, preventing users from tapping "Power off" or "Restart".
  - Can be toggled from Settings under the Uninstall & Settings Shield card.
  - Turning ON takes effect immediately. Turning OFF is protected behind a **2-hour delay timer** (`power_menu`), giving the user a cool-off window to reconsider and stay focused before the change can be confirmed.
- **Deactivate Device Owner in Settings with Anti-Tamper Lock Protection:**
  - Added Device Owner status indicator ("Active" / "Inactive") in the Settings screen under the Uninstall & Settings Shield section.
  - When Device Owner is active, provides a "Deactivate" action.
  - Immediate deactivation is strictly prevented while master protection or uninstall shield is active, or while any lock/cooldown timer is running.
  - Deactivation requests queue into the tamper-resistant countdown delay timer (`device_owner`), requiring confirmation only after the cooldown delay finishes.
  - Deactivation cleanly unhides packages and removes restrictions before calling `dpm.clearDeviceOwnerApp()`.

---

## v1.0.6.3

**Release Date:** October 2026  
**Version:** 1.0.6.3  
**Build:** 28

### Security & Blocking Engine Hardening
- **Unconditional App Kick Defense (Zero-Debounce Kick):**
  - Resolved an issue where rapidly launching a blocked game/app from the home screen or recents menu could bypass blocking: previously, the 700ms debounce guard was evaluated before kicking, allowing rapid repeated app launches to slip past.
  - The kick action (`GLOBAL_ACTION_BACK` + `kickToHome()`) is now executed **unconditionally on 100% of detections** with zero debounce delay. The 700ms debounce window is strictly reserved for UI overlays and statistics counting.
- **Background Process Termination (`killBackgroundProcesses`):**
  - When a blocked app or game is kicked out, Blocker automatically terminates its background processes via `ActivityManager.killBackgroundProcesses(pkg)` with permission `KILL_BACKGROUND_PROCESSES`.
  - Prevents intensive native games (Unity, Unreal Engine, OpenGL/Vulkan) from remaining active in the background, keeping audio running, or lingering in device RAM.
- **Continuous Active-Window Watchdog (`appBlockWatchdog`):**
  - Added a dedicated 350ms continuous polling watchdog that inspects `rootInActiveWindow` and `currentFgPkg`.
  - Ensures that even if a full-screen hardware-accelerated game bypasses or silences accessibility UI event dispatches, the watchdog immediately detects the foreground package and kicks the user back home.
- **Immediate Top-Priority Event Intercept:**
  - Placed blocked app checks at the top of `onAccessibilityEvent` directly after interactive checks, avoiding any 75ms throttling or downstream view processing delays.

---

## v1.0.6.2

**Release Date:** October 2026  
**Version:** 1.0.6.2  
**Build:** 24

### Security & Anti-Tamper Hardening
- **Universal App Hiding & Freezer Defense (`guardAppHidingAndFreezer`):**
  - Defeats package hibernation and freezing bypasses across all Android OEM launchers and privacy suites where hiding Blocker suspended the package and killed the Accessibility Service.
  - **Comprehensive Multi-OEM Coverage:**
    - **Samsung One UI:** Traps Home screen "Hide apps on Home and Apps screens" (`HideAppsActivity`, `HideAppsSettingActivity`) and drawer long-press popups.
    - **Xiaomi / Redmi / POCO (HyperOS & MIUI):** Intercepts Security app "Hidden apps" (`com.miui.securitycenter`, `AppLockHiddenAppsActivity`) and MIUI Home drawer hide gestures.
    - **OnePlus / Oppo / Realme (OxygenOS, ColorOS, Realme UI):** Blocks Privacy "Hide apps" (`com.coloros.safecenter`, `com.oplus.safecenter`, `HideAppActivity`).
    - **Vivo / iQOO (Funtouch OS & OriginOS):** Intercepts Privacy and App Encryption "Hide App" (`com.vivo.permissionmanager`, `SoftHideActivity`).
    - **Transsion (Infinix XOS & Tecno HiOS):** Intercepts XOS Launcher Desktop Settings, XHide (`com.xui.xhide`), and App Freezer (`com.transsion.freezer`).
    - **Huawei & Honor (EMUI & MagicOS):** Intercepts PrivateSpace and hidden application settings (`com.huawei.systemmanager`, `com.hihonor.systemmanager`).
    - **Stock & Third-Party Launchers:** Covers Pixel Launcher, Nothing Launcher, Motorola Launcher, Nova Launcher, Lawnchair, Microsoft Launcher, Niagara Launcher, and Smart Launcher.
  - **Multi-Tier Defense Architecture:**
    1. **Desktop & Drawer Long-Press Interception:** Intercepts `TYPE_VIEW_LONG_CLICKED` on Blocker's icon on the launcher desktop and drawer, kicking immediately to home and preventing the context menu (Hide / Freeze / Uninstall / Drag) from appearing. Regular taps (`TYPE_VIEW_CLICKED`) continue to launch Blocker normally.
    2. **Destructive Action Interception:** Intercepts clicks on "Hide", "Freeze", "Send to Freezer", "Disable", or "Uninstall" in launcher popups.
    3. **Screen & Activity Interception:** Detects and kicks users out of hidden app configuration screens, App Freezer, and XHide.
    4. **Multi-Select Picker Protection:** Proactively intercepts taps on Blocker's row or toggle switch inside any app hiding or freezing list.

---

## v1.0.6.1

**Release Date:** October 2026  
**Version:** 1.0.6.1  
**Build:** 19

### Security Hardening & Audit Mitigations
- **Monotonic Hardware Time Tracking (`SecureTime`):**
  - Replaced vulnerable wall-clock checks with hardware uptime tracking via `SystemClock.elapsedRealtime()`.
  - Persists monotonic hardware anchors to prevent timer-bypass exploits via ADB (`date -s`), NTP changes, or SIM removal.
- **HMAC-SHA256 State Integrity Verification:**
  - Enforced cryptographic HMAC-SHA256 checksums (`computeIntegrityHmac`) across critical SharedPreferences keys.
  - Automatically detects and reverts manual file edits or root manipulation.
- **Code Signing Certificate Verification:**
  - Enforces cryptographic package signature checks (`GET_SIGNING_CERTIFICATES` / `GET_SIGNATURES`) on downloaded updates in `launchInstallIntent()` matching the currently installed package.
- **Device Admin Deactivation Mitigation:**
  - Closes the deactivation window in `onDisableRequested()` with immediate back/home actions and dialog cancellation.
  - Automatically prompts immediate re-activation and posts a high-priority alert notification (ID 1002) if disabled.
- **Reboot Accessibility Shield:**
  - `BootReceiver` checks `Settings.Secure` on boot and package replacement.
  - Automatically restores the Accessibility Service or posts high-priority alert notification 1003 if inactive.
- **Memory & Resource Safety:**
  - Capped `collectText` recursion depth to 16 with guaranteed `recycle()` calls across all accessibility node walks.
  - Enforced 2MB size cap and JSON validation on backup state restoration.

### Performance & User Experience
- **0ms Instantaneous Block Overlay:**
  - Unified scrim and card into a single-window `FrameLayout` overlay displayed synchronously before the Home action kicks, eliminating overlay delay and IPC latency.
  - Short-circuited `guardBlockerAppInfo` for non-settings applications, saving 50-100ms on every accessibility event.
- **Restored Facebook Reels & Scrollable Video Interception with Newsfeed Guard:**
  - Blocks dedicated full-screen Reels viewer playback, dedicated Reels/Video tabs, and the full-screen scrollable video viewer (ChannelFeed / Warion) launched from newsfeed video clicks.
  - Safely protects normal newsfeed browsing (posts, status updates, stories, header search) from false exits.
- **Opt-In Granular Distraction Shields:**
  - Facebook Reels, Instagram Reels, Instagram Search, and YouTube Shorts now default to **OFF (`false`)**, giving users complete choice over what to block in Focus Mode > Distraction Shields.
- **Study Mode Emergency Exit:**
  - Restored immediate emergency unlock via 4-second long-press, properly disabling DND, unmuting audio streams, and resetting active study blocks without delay timer queue trapping.
- **Intelligent 2-Hour Update Check Cycle:**
  - Automated update checks now run on an active 2-hour cycle (in both foreground app lifecycle and persistent background service) instead of 24 hours.
  - When a new update is detected, the user is notified and the cycle backs off to 24 hours if not updated immediately to avoid spam. Resets back to 2 hours once updated.

---

## v1.0.6

**Release Date:** October 2026  
**Version:** 1.0.6  
**Build:** 12

### Changes & Fixes
- **Unrestricted Background Service Access:**
  - Whitelisted battery optimization intents (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) so users can grant background execution without being blocked by App Info protection.
- **Do Not Disturb Permission Exemption:**
  - Whitelisted DND notification policy access (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`) to allow silent mode enforcement during study sessions.
- **Instant 0ms App Info Tamper Protection:**
  - Eliminated high-overhead Binder IPC lookups during settings navigation that previously caused touch latency when viewing installed apps.
  - Restored instantaneous, single-frame exit (`GLOBAL_ACTION_BACK`) when tapping Blocker in the system App List.
- **Date & Time Tamper Protection:**
  - Closed vulnerability where users could advance the device clock in Android Settings to bypass multi-day delayed unlock timers.
  - Implemented dual-layer protection (instant window interception + continuous screen scanner) that immediately locks out Date & Time settings while Protection Mode or Study Mode is active.
- **Lockout Stability & Zero Minimize/Reopen Loops:**
  - Differentiated navigation bar touches from notification shade expansions in `com.android.systemui`, preventing accidental minimization during active sessions.
  - Debounced home screen launcher navigation to bring Blocker smoothly to the front during active lockdown sessions without thrashing.
  - Intercepted Android hardware back buttons and edge gestures with `BackHandler`.
- **Notification Shade & Quick Settings Collapse:**
  - Automated panel collapsing via `StatusBarManager.collapsePanels()` and `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` prevents pulling down the notification shade, blocking unauthorized toggling of Wi-Fi, mobile data, or airplane mode.
- **Removed AI Aesthetics:**
  - Purged all flare/sparkle icons across the interface, replacing them with clean academic icons (`BookOpen`, `Bookmark`).

---

## v1.0.5

**Release Date:** October 2026  
**Version:** 1.0.5  
**Build:** 10

### Features
- **Pomodoro Study Mode:**
  - Added aggressive full-phone lockdown mode integrated into the Focus page.
  - Customizable focus cycles (15m-180m), rest breaks (3m-15m), and session rounds (1-6).
  - All apps, home launcher, settings, and navigation blocked during study sessions.
  - Complete silencing: DND (`INTERRUPTION_FILTER_NONE`) and zeroed volume streams.
  - Interactive Wi-Fi controls: monitor and toggle Wi-Fi directly within Blocker without breaking lockdown.
  - Emergency safeguard: dialer and emergency telecom services explicitly whitelisted.

---

## v1.0.4

**Release Date:** October 2026  
**Version:** 1.0.4  
**Build:** 8

### Fixes & Enhancements
- **Facebook Scrolling Fix:**
  - Resolved an issue where scrolling through Facebook newsfeed videos triggered erroneous back actions.
- **In-App Updater Enhancements:**
  - Monotonic `versionCode` calculation to prevent `INSTALL_FAILED_VERSION_DOWNGRADE` errors.
  - Pre-install package verification and on-demand cache purge in settings.
- **Focus Schedule Stability:**
  - Prevented schedule editing resets during background state sync.

---

## v1.0.3.1

**Release Date:** October 2026  
**Version:** 1.0.3.1  
**Build:** 5

### Improvements
- Persistent sticky foreground service for OEM background killer defense.
- Accurate daily per-app usage limits via `UsageStatsManager`.
- Per-schedule focus allowed apps list.
- Improved color palette and native system time picker integration.

---

## v1.0.3

**Release Date:** October 2026  
**Version:** 1.0.3  
**Build:** 3

### Security
- Instant exit from App Info page inside Settings.
- Hardened Device Administrator anti-tamper checks.
- Prevented uninstallation attempts while Protection Mode is active.

---

## v1.0.2

**Release Date:** October 2026  
**Version:** 1.0.2  
**Build:** 2

### Features & Security
- Integrated GitHub Releases in-app updater with automated 24-hour update checks.
- Added in-app markdown changelog viewer and direct APK installer.
- Cryptographic URL sanitization and automated storage cache cleanup.
- Enforced release vs. debug build separation to prevent accidental downgrades.

---

## v1.0.1

**Release Date:** October 2026  
**Version:** 1.0.1  
**Build:** 1

### Initial Refinements
- Added app branding and mipmap icon generation assets.
- Cleaned up navigation bar and accessibility kick timing.

---

## v1.0.0

**Release Date:** October 2026  
**Version:** 1.0.0  
**Build:** 0

### Initial Release
- Core React Native UI with bottom tab navigation.
- Native Android Accessibility Service content blocking engine.
- Browser URL bar interception across major Android web browsers.
- Local adult domain database with custom domain blocklists and keyword whitelisting.
- 1-30 day delayed unlock timers.
- Device Administrator uninstall guard.
