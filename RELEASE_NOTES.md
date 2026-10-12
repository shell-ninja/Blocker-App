# Blocker Release Notes

This document contains release notes, changelogs, and notable changes for all releases of Blocker.

---

## v1.0.6.1 (Current Release)

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
- **Restored Facebook Reels Interception:**
  - Added Litho TalkBack selection state scanning (`cd.contains("selected")`) and Reels viewer signatures (`reelsviewer`, `channelfeed`, `warion`, audio tags) to reliably block FB Reels.
- **Opt-In Granular Distraction Shields:**
  - Facebook Reels, Instagram Reels, Instagram Search, and YouTube Shorts now default to **OFF (`false`)**, giving users complete choice over what to block in Focus Mode > Distraction Shields.
- **Study Mode Emergency Exit:**
  - Restored immediate emergency unlock via 4-second long-press, properly disabling DND, unmuting audio streams, and resetting active study blocks without delay timer queue trapping.

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
