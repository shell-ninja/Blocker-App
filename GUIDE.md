# Blocker: build the APK on Arch Linux and install it on your phone

You build on your **Arch Linux PC** and run the app on your **Android phone**. No Android Studio, and
no programming knowledge needed — one script does steps 1 to 7 below.

Plan for time and disk on the very first run: 20 to 45 minutes and about 3 to 5 GB of downloads
(less if you already have the Android SDK, Java or Node — the script checks and skips what's already
there). Later rebuilds take a minute or two.

## What is in this folder

| Item | What it is |
|---|---|
| `build_apk.sh` | The one script: checks/installs tools, creates the project, builds the APK |
| `05_install_on_phone.sh` | Optional: installs the APK over USB with `adb` |
| `blocklists/block-list.md` | The main block list (websites, keywords, TLDs) |
| `blocklists/my_list.txt` | Your own quick additions on top of it |
| `blocklists/whitelist.txt` | Words/phrases that should never be blocked |
| `assets/app-icon-source.png` | The app icon artwork |
| `VERSION` | The app's version number (e.g. `1.0.0`) — edit this to bump it |
| `.build_number` | Internal build counter — auto-managed, don't edit |
| `source/` | The app's code (you don't have to touch it) |
| `env.sh`, `patch_android.py`, `import_lists.py`, `generate_icons.py`, `embed_icon.py`, `set_version.py` | Helpers the script calls |

Open a terminal in this folder. If you got the `.tar.gz`: `tar xzf blocker.tar.gz && cd blocker && chmod +x *.sh`.

---

## Step 0. Prepare your phone (2 minutes) — only if you'll use USB install

If you plan to copy the APK over some other way (browser download, KDE Connect, a cable in plain
file-transfer mode), skip this and go to Step 1.

For USB install via `adb`:
1. **Settings → About phone**, tap **Build number** 7 times. This turns on Developer options.
2. **Settings → System → Developer options → USB debugging: ON.**
3. (Xiaomi/Redmi/Poco only) also turn on **Install via USB**.

If another app on your phone (a blocker, parental control, etc.) is preventing you from turning on
USB debugging or Developer options, that's that app doing its job — resolve it there, or just skip
USB entirely and copy the file across some other way instead.

## Step 1. Add your own block list (optional now, editable any time)

The main list already ships in `blocklists/block-list.md`. To add more of your own:

- **`blocklists/my_list.txt`** — one entry per line. `example.com` blocks that site and its subdomains;
  anything else blocks that whole word (so "camera" is never caught by a "cam" rule).
- **`blocklists/whitelist.txt`** — phrases that should never be blocked even if they contain a blocked
  word, e.g. `sex education` or `adulteducation`. A few common ones are pre-filled.

Both are re-imported automatically every time you run `build_apk.sh`, so you can keep editing and
rebuilding.

## Step 2. Build

```bash
./build_apk.sh
```

This single script:
1. Installs Java 17, Node.js, `adb`, `unzip` and `git` with `pacman` if any are missing (asks for
   your sudo password only if something needs installing).
2. Checks for an existing Android SDK in the Blocker-App directory (or `~/Android/Sdk`). If it's not there, it looks for a
   `commandlinetools-linux-*.zip` in the `Blocker-App` directory (or `~/Downloads`)
   and sets it up automatically. You can also place `android-ndk-r25b-linux.zip` and `gradle-8.3-all.zip` directly in `Blocker-App`
   to automatically install NDK and Gradle without downloading them over the network. If it can't find one, it tells you the download link
   (<https://developer.android.com/studio#command-line-tools-only>, Linux) and how to point it at
   the file: `ANDROID_CMDLINE_ZIP=/path/to/the.zip ./build_apk.sh`.
3. Imports your blocklists and builds the app icon.
4. Creates the React Native project in `~/Blocker` (first run only — later runs reuse it).
5. Copies in Blocker's code and native modules.
6. Installs the two small JS packages the icons use.
7. Builds the APK.

Run it with no argument (or `debug`) for your **first install** — the debug build shortens every
"day" of a delay timer to **1 minute**, so you can test locks without waiting 24 hours:

```bash
./build_apk.sh debug
```

At the end you get **`~/Blocker-debug.apk`**.

## Step 3. Install on your phone

**Option A — USB cable with `adb` (needs Step 0):**
```bash
./05_install_on_phone.sh debug
```

**Option B — copy the file (no developer settings needed):**
1. Serve it over Wi-Fi from the PC:
   ```bash
   mkdir -p /tmp/apk && cp ~/Blocker-debug.apk /tmp/apk && cd /tmp/apk && python3 -m http.server 8000
   ```
   Find your PC's address with `ip -4 addr`, then open `http://<that address>:8000/Blocker-debug.apk`
   in the phone's browser. Ctrl+C on the PC when done.
2. Or copy it another way: USB in file-transfer mode, KDE Connect, Bluetooth, a cloud drive.
3. Tap the file on the phone. Allow **Install unknown apps** for whichever app opened it.
4. If Play Protect warns you, choose **Install anyway** — normal for an app you built yourself.

## Step 4. First launch: grant the four permissions

Open **Blocker**. The setup screen shows all four live as **Granted / Missing**:

1. **Restricted settings (Android 13+): do this first.** Long-press the Blocker icon → **App info** →
   **⋮** (top right) → **Allow restricted settings**. Skipping this leaves the Accessibility switch
   greyed out.
2. **Accessibility service:** tap Grant → Blocker → **Use Blocker** → Allow.
3. **Device admin:** tap Grant → **Activate this device admin app**. If the button opens the wrong
   screen on your phone, it tells you so and points you to Settings → Security → Device admin apps.
4. **Usage access:** tap Grant → select Blocker → allow.
5. **Display over other apps:** tap Grant → select Blocker → allow.

When all four say Granted, **Continue** unlocks.

## Step 5. Test everything (debug build)

1. **Protection:** Home → turn **Protection** on.
2. **Site blocking:** in Chrome, type a blocked word into the address bar. The tab redirects straight
   to Google and a 5-second popup explains why — the browser is never locked or held open.
3. **App blocking:** Apps tab → block an app → open it → sent to the home screen.
4. **Screen monitoring (always on):** open a notes app and type a blocked word. A 5-second popup
   appears and the app closes immediately — no lock, no waiting period. If it's a browser tab
   instead, that tab redirects to Google the same way as address-bar blocking. Reopening the same
   app right away won't immediately re-trigger — there's a brief cooldown per app so a static bit of
   text can't loop the popup.
5. **Scan exemptions:** in Apps, tap the eye icon on an app (e.g. another security app whose own
   screens mention words like "adult") to stop scanning just that app.
6. **Whitelist:** in Web & keywords → Whitelist, add a phrase like "sex education" and confirm the
   words inside it stop tripping the scanner (needs the delay timer while Protection is on). Only
   your own entries show up in this list — Blocker's built-in protective phrases stay applied in the
   background without cluttering it.
7. **Timers:** turn Protection off. It says the change is locked. Wait 1 minute (debug build), then
   tap **Confirm change**.
8. **Focus mode:** Focus tab → keep or adjust the essential apps (up to 5; WhatsApp, Messenger and
   Google Translate are preselected) → pick a duration → **Start**. Everything else gets kicked out;
   use the **Open** button for an essential app. Try **End focus now** — it asks you to wait.
9. **Daily schedules:** in Focus, tap **+** (or **Add** in the Daily schedules card) to create a
   schedule — it defaults to the next rounded hour. Tap it to expand, then tap a time to open the
   clock-style picker (12-hour AM/PM) and set both a start and end a couple of minutes from now.
   Confirm focus mode turns itself on and off at those times, and that you can add more than one
   schedule.
10. **Settings shield:** with Protection on, open Settings → Apps → Special access → Device admin apps.
    You're sent home.

## Step 6. Build the real version for daily use

```bash
./build_apk.sh release
./05_install_on_phone.sh release      # or copy ~/Blocker-release.apk to the phone
```

This installs over the debug build and keeps your settings. **From here on, timers are real:** 24
hours by default, 1 to 30 days if you change it. Before relying on it:
- Confirm your essential apps and schedule in the Focus tab.
- Confirm the delay you want in Settings — longer applies immediately, shorter always waits.

The APK is signed with the standard debug key React Native projects ship with. That's fine for your
own phone, and a new build always updates the old one in place. It can't go on the Play Store, and
you don't need it there.

---

## What changed in this build

- **"adult" removed from the keyword block list** — it was catching legitimate words like
  "adulthood" and "adult education."
- **Device admin is harder to switch off from Settings.** Blocker now recognizes the actual system
  screens for managing device admins (not just their on-screen text) and blocks entry to the admin
  list outright, and blocks the per-admin screen once Blocker's admin is already active — while still
  allowing that same screen through during onboarding, since that's how Blocker is first granted admin.
  This is a real narrowing, not a full guarantee: Android's Device Admin API has no way for an app to
  veto its own deactivation once the user reaches that confirmation dialog, on any OEM screen this
  version doesn't yet recognize. Only Android's separate, much stricter "Device Owner" mode can fully
  prevent that, and it isn't available to an app installed normally like this one.
- **Focus mode timer refined.** The countdown could drift or show a stale number for a few seconds
  after you changed it; changes now refresh the real remaining time first, and the screen polls faster
  while focus is running so a schedule starting or ending shows up within about 2 seconds instead of 5.
- **Minimal animations throughout** — buttons and icons give a light press response, switching tabs
  cross-fades, and the Focus schedule accordion expands and collapses smoothly instead of snapping.
- **App icon is no longer cropped, masked, or cut.** Both the square and "round" launcher icon files
  are your full, unmasked artwork, and the app no longer ships an adaptive-icon layer that would let
  Android reshape it — the only remaining cropping is a launcher's own icon-shape setting, which is
  outside any app's control.
- **Fixed false positives on the home screen launcher.** The launcher (and system UI) are now fully
  exempt from screen scanning, which was the cause of words like "adult" or "cam" being reported
  there with nothing matching visibly on screen — off-screen widget/feed text was being read as part
  of the same screen. A repeat-popup loop is also fixed: after any screen-content match, that app
  gets a short cooldown before it's scanned again.
- **Whitelist screen now only lists your own entries.** Built-in protective phrases (like "sex
  education") still apply automatically; they just don't clutter the list or show up as removable.
- **No internal file paths or filenames appear anywhere in the app's UI.**
- **Screen monitoring is always on** and no longer has a Settings toggle — it's core to how Blocker
  works.
- **App header** shows your icon and "Blocker" in the accent color at the top of every main screen.
- **Focus mode now supports multiple named daily schedules** instead of just one — add as many as you
  like from the **+** button, each with its own accordion row, 12-hour clock-dial time picker, and
  on/off switch.
- **Confirmation popups redesigned** to match the app's theme — a dimmed backdrop and dark card with
  themed buttons, instead of the plain grey system dialog.
- **Block list sourced from `blocklists/block-list.md`** — 9,355 sites, 60 keywords and 4 TLDs — plus
  your own additions in `my_list.txt`.
- **The app now has a version number**, shown at the bottom of Settings — see below.

## Versioning

Every build now carries a version, shown at the bottom of the Settings tab as "Blocker 1.0.0 (build 3)".

- **`VERSION`** — the marketing version (`1.0.0`, `1.1.0`, and so on). Edit this file by hand whenever
  you want to bump it, or set it for a single build without editing the file:
  ```bash
  APP_VERSION=1.1.0 ./build_apk.sh release
  ```
- **`.build_number`** — an internal counter that goes up by 1 on *every* build, debug or release.
  Don't edit it by hand. This is what actually satisfies Android's rule that a newer install must have
  a higher internal version number (`versionCode`) than the one already on the phone — `VERSION` alone
  (`versionName`) is just the label a person reads and doesn't have to keep increasing.
- Both feed straight into `android/app/build.gradle`'s `versionCode`/`versionName` on every build, so
  you never need to touch that file.
- A practical habit: bump `VERSION` when you make a change worth naming (a new feature, a real fix);
  let `.build_number` take care of itself for everything else, including repeated test builds.

## Focus mode: how it behaves

- While it runs, **every app and Settings are locked**, including Blocker's own other tabs — you only
  see the countdown and **Open** buttons for your essential apps.
- Always allowed: your essential apps, phone calls, your keyboard, the home screen, system dialogs
  (permissions, share sheet, file picker) and Google Play Services.
- A **one-off timer** and any number of **daily schedules** can all be set at once; focus is active
  whenever any of them says so.
- **Longer timer, a schedule turned on or widened, or a brand-new schedule:** applies immediately.
  **Shorter timer, a schedule narrowed or turned off while it's currently active, deleting an active
  schedule, ending early, or allowing an extra essential app:** waits for your delay timer (24 hours
  by default). If focus ends on its own first, a still-waiting change is dropped.
- Removing an essential app is always immediate, since that only makes the lock stricter.
- Survives a phone restart.

## Whitelist and scan exemptions

- **Whitelist** (Web & keywords tab): a word or phrase here is never blocked, even where it contains a
  blocked keyword. Adding one needs the delay timer while Protection is on (it's an exception, so it
  weakens blocking); removing one is instant. The list only ever shows what you've added yourself.
- **Scan exemption** (Apps tab, eye icon): stops on-screen keyword scanning for one specific app. Block
  list and browser address-bar checks still apply normally to that app. Exempting an app needs the
  delay timer while Protection is on; un-exempting is instant.

## When you change the code, icon, or your lists

Edit `blocklists/*.txt`/`*.md`, `assets/app-icon-source.png`, or files under `source/`, then run
`./build_apk.sh debug` (or `release`) again and reinstall. The script re-imports and re-copies
everything for you and reuses the existing `~/Blocker` project.

## Emergency exit (debug build only)

If you lock yourself out while testing, wait out the 1-minute timer. If that isn't possible, connect
over USB and run:

```bash
source env.sh
adb shell dpm remove-active-admin com.blocker/.BlockerDeviceAdminReceiver
adb uninstall com.blocker
```

These are meant to work on debug builds only — **the release build has no back door.** That's the
point of it, so only install release once you're ready.

## Troubleshooting

| Problem | Fix |
|---|---|
| `java: version` errors / "Unsupported class file major version" | Run `sudo archlinux-java set java-17-openjdk`, then retry. |
| "SDK location not found" | Check `~/Blocker/android/local.properties` has `sdk.dir=/home/YOU/Android/Sdk`. The script writes it on first run. |
| Script can't find your SDK zip | Set the path explicitly: `ANDROID_CMDLINE_ZIP=/path/to/it.zip ./build_apk.sh`. |
| `pacman` can't find a Node package | Run `pacman -Ss nodejs-lts`, install one (Node 20 or 22), then retry. |
| Phone missing from `adb devices` | Set USB mode to File transfer and re-accept the debugging prompt on the phone. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old Blocker first (needs the timer, or the debug-only emergency exit). |
| Accessibility switch greyed out | Do "Allow restricted settings" in Step 4. |
| Blocking stops after a while | Settings → Battery → Blocker → **Unrestricted**. Some brands (Xiaomi, Oppo, Samsung) kill background services otherwise. |
| Old 32-bit phone | Build with `ARCH=armeabi-v7a ./build_apk.sh release`. |
| `npm install` fails on `lucide-react-native`/`react-native-svg` | These were pinned for React Native 0.73 but versions drift over time — try `npm install lucide-react-native@latest react-native-svg@latest` inside `~/Blocker` and rebuild. |
| Build fails somewhere else | Run `./build_apk.sh debug` again and copy the **first** red error line into a message to me. |

## Good to know

- **Privacy:** screen text is checked on the phone and never stored or sent anywhere. Only counts
  (blocks today) are kept. Password fields are skipped.
- **Screen monitoring only reads text Android exposes** — not text inside images/video, games that
  draw their own text, or apps that hide their screen (banking apps, for example).
- **Browsers covered for address-bar blocking:** Chrome, Edge, Brave, Firefox, Opera, Samsung
  Internet, DuckDuckGo, Vivaldi, Kiwi. Any other browser is still covered by screen monitoring and the
  App Blocker.
- **This build is Android only.**
