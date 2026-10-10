package com.blocker

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        // package -> URL bar view IDs (add a line to support another browser)
        val BROWSER_URL_IDS: Map<String, List<String>> = mapOf(
            "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
            "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
            "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
            "org.mozilla.firefox" to listOf(
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox:id/url_bar_title"
            ),
            "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
            "com.opera.mini.native" to listOf("com.opera.mini.native:id/url_field"),
            "com.sec.android.app.sbrowser" to listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text"
            ),
            "com.duckduckgo.mobile.android" to listOf(
                "com.duckduckgo.mobile.android:id/omnibarTextInput"
            ),
            "com.vivaldi.browser" to listOf("com.vivaldi.browser:id/url_bar"),
            "com.kiwibrowser.browser" to listOf("com.kiwibrowser.browser:id/url_bar")
        )

        private val SETTINGS_PKGS = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.coloros.settings",
            "com.oplus.safecenter",
            "com.oplus.settings",
            "com.vivo.settings",
            "com.oppo.launcher",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.transsion.phonemaster",
            "com.transsion.phonemanager",
            "com.transsion.settings",
            "com.transsion.ossettingsext",
            "com.transsion.powercenter",
            "com.transsion.batterylab",
            "com.transsion.applock",
            "com.scorpio.securitycom",
            "com.android.storagemanager",
            "com.android.managedprovisioning",
            "com.mediatek.engineermode",
            "com.debug.loggerui",
            "com.coloros.securitypermission",
            "com.oplus.securitypermission"
        )
        private val DEV_OPTIONS_CLASSES = setOf(
            "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity",
            "com.android.settings.Settings\$DevelopmentSettingsActivity",
            "com.android.settings.development.DevelopmentSettingsDashboardActivity",
            "com.android.settings.DevelopmentSettings",
            "com.samsung.android.settings.development.DevelopmentSettings"
        )
        private val DIRECT_TERMS = listOf(
            "device admin apps", "special app access", "device administrators",
            "device admin", "device administrator", "admin apps",
            "developer options", "development settings"
        )
        private val APP_RISK_TERMS = listOf(
            "deactivate", "uninstall", "force stop", "accessibility", "turn off", "remove", "disable", "clear data",
            "clear storage", "manage space", "device admin", "admin apps", "modify system settings",
            "deactivate this device admin app", "deactivate this device administrator"
        )

        private val GRANULAR_TARGET_PKGS = setOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.instagram.android",
            "com.google.android.youtube"
        )

        private val YT_SHORTS_PLAYER_IDS = listOf(
            "com.google.android.youtube:id/shorts_player_container",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/shorts_container",
            "com.google.android.youtube:id/reel_watch_fragment",
            "com.google.android.youtube:id/shorts_video_feed",
            "com.google.android.youtube:id/reel_recycler"
        )
        private val INSTA_REELS_PLAYER_IDS = listOf(
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/feed_reel_viewer",
            "com.instagram.android:id/clips_swipe_refresh_layout"
        )
        private val INSTA_SEARCH_INPUT_IDS = listOf(
            "com.instagram.android:id/action_bar_search_edit_text",
            "com.instagram.android:id/search_edit_text"
        )
        private val FB_REELS_PLAYER_IDS = listOf(
            // Dedicated full-screen Facebook Katana Reels viewers
            "com.facebook.katana:id/fb_shorts_viewer_container",
            "com.facebook.katana:id/fb_shorts_video_container",
            "com.facebook.katana:id/fb_shorts_viewer_view_pager",
            "com.facebook.katana:id/fb_shorts_unified_viewer",
            "com.facebook.katana:id/fb_shorts_viewer_fragment",
            "com.facebook.katana:id/fb_shorts_root_container",
            "com.facebook.katana:id/fb_shorts_container",
            "com.facebook.katana:id/reels_viewer_root",
            "com.facebook.katana:id/reels_video_view",
            "com.facebook.katana:id/reels_video_feed",
            "com.facebook.katana:id/watch_and_go_reels_container",
            "com.facebook.katana:id/short_video_feed_fragment",
            "com.facebook.katana:id/fb_shorts_player_fragment",
            // Dedicated Facebook Lite Reels viewers
            "com.facebook.lite:id/reels_screen",
            "com.facebook.lite:id/reels_player",
            "com.facebook.lite:id/video_player_reels"
        )
        // The AOSP list screen only ever means "browse admins to possibly deactivate one" — block outright.
        private val DEVICE_ADMIN_LIST_CLASSES = setOf(
            "com.android.settings.DeviceAdminSettings",
            "com.android.settings.Settings\$DeviceAdminSettingsActivity",
            "com.samsung.android.settings.security.DeviceAdminSettings",
            "com.android.settings.applications.DeviceAdminSettings"
        )
        // This same activity is also how Blocker's own onboarding grants admin the first time, so it's only
        // blocked once admin is already active (i.e. this view can only be for managing/deactivating it).
        private val DEVICE_ADMIN_DETAIL_CLASSES = setOf(
            "com.android.settings.DeviceAdminAdd",
            "com.samsung.android.settings.security.DeviceAdminAdd",
            "com.android.settings.applications.DeviceAdminAdd"
        )
        // App Info & Storage screens — blocked the instant they open for Blocker's own package
        private val APP_INFO_CLASSES = setOf(
            "com.android.settings.Settings\$AppInfoDashboardActivity",
            "com.android.settings.applications.AppInfoDashboardFragment",
            "com.android.settings.applications.AppInfoDashboard",
            "com.android.settings.applications.InstalledAppDetailsTop",
            "com.android.settings.applications.InstalledAppDetails",
            "com.samsung.android.settings.applications.AppInfoDashboardActivity",
            "com.samsung.android.settings.application.ApplicationsDetailsActivity"
        )
        private val APP_STORAGE_CLASSES = setOf(
            "com.android.settings.deviceinfo.StorageItemPreferenceController",
            "com.android.settings.applications.AppStorageSettings",
            "com.android.settings.Settings\$StorageUseActivity",
            "com.android.settings.applications.manageapplications.ManageApplications"
        )
        // packages that must keep working during focus mode (calls, permission/share/file dialogs, Google services)
        private val FOCUS_SYSTEM_PKGS = setOf(
            "com.android.incallui", "com.android.server.telecom", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.android.intentresolver", "com.android.documentsui",
            "com.google.android.documentsui", "com.google.android.providers.media.module", "com.google.android.gms"
        )
        private val RELOAD_KEYS = setOf("domains", "keywords", "tlds", "whitelist", "apps", "scan_exempt", "focus_apps", "block_fb_reels", "block_insta_reels", "block_insta_search", "block_yt_shorts", "active", "shield", "admin_disable_requested", "app_limits")
        // Window titles (as shown on screen) that signal a dangerous settings screen — OEM-agnostic
        private val ADMIN_SCREEN_TITLES = setOf(
            "device admin apps", "device administrators", "device admin",
            "device admin app", "device administrator", "admin apps",
            "administrator", "device management"
        )
        private val STORAGE_SCREEN_TITLES = setOf(
            "storage", "storage & cache", "storage and cache",
            "clear storage", "clear data", "manage storage", "app storage"
        )
        private const val SCAN_GAP_MS = 1000L
        private const val MAX_SCAN_CHARS = 20_000
        private const val REDIRECT_URL = "https://www.google.com"

        // ── Blocker App Info guard ──
        // Static OS button labels, matched case-insensitively. Add another UI language here if you ever need one.
        // "Deactivate" covers the "Deactivate & uninstall" device-admin page that Uninstall leads to.
        private val APP_INFO_ACTION_TEXTS = listOf(
            "Uninstall", "Force stop", "Deactivate", "Disable", "Storage & cache", "Storage", "Clear storage", "Clear data", "Manage space", "Open by default", "Permissions"
        )
        // Activity class fragments of the Device admin screens (list and "Deactivate & uninstall" page)
        private val DEVICE_ADMIN_CLASS_HINTS = listOf("deviceadmin")
        // Phrases that used to ship as blocked keywords. Ignored even if an older saved list still contains them.
        private val RETIRED_KEYWORDS = setOf("usb debugging", "oem unlocking", "oem unlock", "bare")
        // Activity / fragment class-name fragments that announce "this window is an App Info screen"
        private val APP_INFO_CLASS_HINTS = listOf("appinfo", "installedapp", "appdetail", "applicationdetail", "applicationsdetail")
        private const val APP_INFO_FAST_TICK_MS = 15L      // scan cadence right after a window opens
        private const val APP_INFO_SLOW_TICK_MS = 60L      // scan cadence while merely sitting in Settings
        private const val APP_INFO_FRESH_MS = 1_500L       // how long after an opening the fast cadence lasts
        private const val APP_INFO_IMMEDIATE_GAP_MS = 20L  // min gap between event-triggered scans
        private const val APP_INFO_HOLD_OPEN_MS = 2_500L   // keep scanning this long after a window opens
        private const val APP_INFO_HOLD_ACTIVE_MS = 600L   // ...and this long after any other Settings activity
        private const val APP_INFO_DEEP_EVERY = 6          // every Nth scan also allows the fallback walk
        private const val APP_INFO_KICK_GAP_MS = 500L      // min gap between popup/stat updates
        private const val APP_INFO_WALK_BUDGET = 200       // max nodes the fallback walk will ever visit
        private const val APP_INFO_WALK_DEPTH = 25
        private const val APP_INFO_MAX_TEXT = 120          // longer strings can't be a name / version / button label
        // Settings.Global flags that let a computer talk to the phone over adb (USB, and Wireless debugging)
        private val ADB_SETTING_KEYS = listOf(Settings.Global.ADB_ENABLED, "adb_wifi_enabled")
        private const val ADB_ENFORCE_GAP_MS = 1_000L
        // No-computer fallback: drive Developer options itself and flip the USB/Wireless debugging switch off
        private const val ADB_ROW_LABEL = "USB debugging"
        private const val ADB_WIFI_ROW_LABEL = "Wireless debugging"
        private const val ADB_FIX_WINDOW_MS = 14_000L      // hard limit for one attempt
        private const val ADB_FIX_MAX_PER_10MIN = 3        // attempts per 10 minutes, so it can never loop
        private const val ADB_FIX_MAX_SCROLLS = 20
        private const val ADB_FIX_STEP_GAP_MS = 250L
        private const val ADB_FIX_CLICK_GAP_MS = 1_500L
        private const val BIT_ACTION = 1
        private const val BIT_NAME = 2
        private const val BITS_ALL = BIT_ACTION or BIT_NAME

        /** Set by BlockerDeviceAdminReceiver.onDisableRequested() to signal an instant kick. */
        @Volatile
        var pendingAdminKick = false

        @Volatile
        var instance: BlockerAccessibilityService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var lastHit = 0L
    private val lastScan = HashMap<String, Long>()
    private val quietUntil = HashMap<String, Long>() // after a redirect, wait briefly before re-scanning that browser
    private val hideRunnable = Runnable { removeOverlay() }

    // system UI and launchers are never scanned or blocked (blocking the launcher would trap the user)
    private var neverBlock: Set<String> = setOf("com.android.systemui", "android")
    private var discoveredSettingsPkgs: Set<String> = emptySet()
    @Volatile private var viewingBlockerSettingsUntil: Long = 0L
    @Volatile private var lastPollKick = 0L
    private var outsideSettingsCount = 0

    // App Info guard state. Scanning runs on its own thread so it never queues behind the main-thread
    // handlers (which can spend 100+ ms walking Settings trees); everything shared is @Volatile or locked.
    @Volatile private var appLabel: String = "Blocker"   // what Settings shows as the app's name
    @Volatile private var appVersionName: String = ""    // what Settings shows as the app's version
    @Volatile private var lastAppInfoKick = 0L
    private val appInfoLock = Any()
    private var appInfoCandidatePkg = ""                 // guarded by appInfoLock
    private var appInfoHotUntil = 0L                     // guarded by appInfoLock (uptime ms)
    private var appInfoFreshUntil = 0L                   // guarded by appInfoLock (uptime ms)
    private var appInfoLoopRunning = false               // guarded by appInfoLock
    private var appInfoThread: HandlerThread? = null     // guarded by appInfoLock
    private var appInfoBg: Handler? = null               // guarded by appInfoLock
    private var appInfoTick = 0                          // scan thread only
    @Volatile private var appInfoNowPending = false      // an event-triggered scan is already queued
    @Volatile private var lastImmediateUp = 0L
    private var lastWindowOpenUp = 0L                    // main thread only: last TYPE_WINDOW_STATE_CHANGED in Settings
    private var appInfoWatchPkg = ""                     // main thread only
    private var appInfoWatchUntil = 0L                   // main thread only
    private val appInfoLoop = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val pkg: String
            val next: Long
            synchronized(appInfoLock) {
                if (now >= appInfoHotUntil || !shieldEngaged()) {
                    appInfoLoopRunning = false
                    return
                }
                pkg = appInfoCandidatePkg
                next = if (now < appInfoFreshUntil) APP_INFO_FAST_TICK_MS else APP_INFO_SLOW_TICK_MS
            }
            scanAppInfoOnce(pkg, deep = (++appInfoTick % APP_INFO_DEEP_EVERY == 0))
            appInfoBg?.postDelayed(this, next)
        }
    }

    private val appInfoNow = Runnable {
        appInfoNowPending = false
        val pkg = synchronized(appInfoLock) { appInfoCandidatePkg }
        if (shieldEngaged()) scanAppInfoOnce(pkg, deep = false)
    }

    // USB-debugging lock state (main thread)
    private var lastAdbEnforce = 0L
    private var lastAdbNotice = 0L
    private var adbPermWarned = false
    private var adbFixUntil = 0L
    private var adbFixScrolls = 0
    private var lastAdbFixClick = 0L
    private val adbFixStarts = ArrayList<Long>()
    private val adbFixTick = object : Runnable {
        override fun run() {
            if (!adbFixActive()) return
            if (!shieldEngaged() || !adbIsOn()) {
                finishAdbUiFix()
                return
            }
            stepAdbUiFix()
            main.postDelayed(this, ADB_FIX_STEP_GAP_MS)
        }
    }
    private val adbObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            enforceAdbOff(notify = true)
        }
    }

    fun kickToHomeAndCancel() {
        main.post {
            runCatching { autoCancelDialog(rootInActiveWindow) }
            kickToHome()
        }
    }

    private fun autoCancelDialog(root: android.view.accessibility.AccessibilityNodeInfo?) {
        val targets = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        if (root != null) targets.add(root)
        runCatching {
            for (win in windows) {
                if (win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                win.root?.let { targets.add(it) }
            }
        }
        for (node in targets) {
            val nPkg = node.packageName?.toString().orEmpty()
            // Strictly guard: ONLY cancel dialogs in Settings, Installer, or Android system dialogs.
            // NEVER search or click anything in launchers, system UI, or third-party apps!
            if (nPkg.isEmpty() || nPkg in neverBlock || nPkg == "com.android.systemui" ||
                (!isSettingsPkg(nPkg) && !isInstallerPkg(nPkg) && nPkg != "android")) {
                continue
            }
            if (!isSettingsPkg(nPkg) && isInstallOrUpdateDialog(root = node)) {
                continue // Do not cancel installer buttons for installation or update
            }
            // Double check: if node contains Install or Update or Staging, NEVER cancel unless it's in Settings!
            val nodeText = StringBuilder()
            collectText(node, nodeText, 0, intArrayOf(60))
            val nTxt = nodeText.toString().lowercase()
            if (!isSettingsPkg(nPkg) && "uninstall" !in nTxt && ("install" in nTxt || "update" in nTxt || "staging" in nTxt)) {
                continue
            }

            // Standard AlertDialog button2 is the Negative/Cancel button
            val cancelById = node.findAccessibilityNodeInfosByViewId("android:id/button2").firstOrNull { it.isClickable }
                ?: node.findAccessibilityNodeInfosByViewId("android:id/button3").firstOrNull {
                    it.isClickable && it.text?.toString()?.trim()?.equals("Cancel", ignoreCase = true) == true
                }

            if (cancelById != null) {
                cancelById.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                cancelById.recycle()
                break
            }

            // If finding by text, REQUIRE EXACT FULL STRING MATCH (e.g. text must be exactly "Cancel" or "No", NEVER a substring matching "Notes"!)
            val cancelByText = node.findAccessibilityNodeInfosByText("Cancel")
                ?.firstOrNull {
                    val t = it.text?.toString()?.trim()
                    it.isClickable && t.equals("Cancel", ignoreCase = true)
                } ?: node.findAccessibilityNodeInfosByText("No")
                ?.firstOrNull {
                    val t = it.text?.toString()?.trim()
                    it.isClickable && t.equals("No", ignoreCase = true)
                }

            if (cancelByText != null) {
                cancelByText.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                cancelByText.recycle()
                break
            }
        }
    }

    private fun kickToHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        runCatching { startActivity(homeIntent) }
    }

    // Continuous settings guard: polls every 150 ms while the user is inside any settings app
    private var settingsPollActive = false
    private val settingsPollRunnable = object : Runnable {
        override fun run() {
            val shieldOn = BlockerStore.active(this@BlockerAccessibilityService) &&
                BlockerStore.shield(this@BlockerAccessibilityService) &&
                !BlockerStore.guardOpen(this@BlockerAccessibilityService, "shield")

            if (!shieldOn) {
                settingsPollActive = false
                outsideSettingsCount = 0
                return
            }


            // React to static pendingAdminKick signal from DeviceAdminReceiver
            if (pendingAdminKick) {
                pendingAdminKick = false
                viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
                pollKick("Device Admin deactivation is locked while Protection Mode is active.")
            }

            val root = rootInActiveWindow
            val pkg = root?.packageName?.toString()

            // Inside Blocker itself - unconditionally halt poll and clear any timers
            if (pkg == packageName) {
                settingsPollActive = false
                outsideSettingsCount = 0
                viewingBlockerSettingsUntil = 0L
                main.removeCallbacks(this)
                return
            }

            // Polling is active while in Settings/Installer OR while the timer is live (covers
            // the case where device admin was deactivated and the uninstall dialog is about to appear).
            val nowTs = System.currentTimeMillis()
            val inSettings = pkg != null && (isSettingsPkg(pkg) || isInstallerPkg(pkg) || pkg == "android")
            val timerActive = nowTs < viewingBlockerSettingsUntil
            if (!inSettings && !timerActive) {
                outsideSettingsCount++
                if (outsideSettingsCount >= 2) {
                    settingsPollActive = false
                    outsideSettingsCount = 0
                } else {
                    main.postDelayed(this, 150)
                }
                return
            }

            outsideSettingsCount = 0
            performSettingsScreenGuard(root)

            // Scan all windows in Z-order for overlay / confirmation dialogs
            val wins = runCatching { windows }.getOrNull()
            if (wins != null) {
                for (w in wins) {
                    // Never scan our own accessibility overlay
                    if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                    val r = runCatching { w.root }.getOrNull() ?: continue
                    val rPkg = r.packageName?.toString().orEmpty()
                    if (rPkg == packageName) {
                        runCatching { r.recycle() }
                        continue
                    }
                    if (rPkg.isNotEmpty() && !isSettingsPkg(rPkg) && !isInstallerPkg(rPkg) && rPkg != "android") {
                        runCatching { r.recycle() }
                        continue
                    }
                    val winText = StringBuilder()
                    collectText(r, winText, 0, intArrayOf(150))
                    val rawWt = winText.toString().lowercase()
                    if ("🔐" in rawWt || "tamper protection" in rawWt || "focus mode" in rawWt) {
                        runCatching { r.recycle() }
                        continue
                    }
                    val wt = rawWt.replace("prevents blocker from being uninstalled.", "")
                    val isOurApp = "blocker" in wt || packageName.lowercase() in wt
                    val recentlyActive = System.currentTimeMillis() < viewingBlockerSettingsUntil

                    // Skip installer windows if they are not uninstall attempts
                    if (!isSettingsPkg(rPkg) && (isInstallerPkg(rPkg) || isInstallOrUpdateDialog(root = r))) {
                        val isUninstall = "uninstall" in wt || "do you want to uninstall" in wt
                        if (!isUninstall) {
                            runCatching { r.recycle() }
                            continue
                        }
                    }

                    // Uninstall dialog: catch by isOurApp OR by timer context (any settings/installer
                    // window recently associated with Blocker's own admin/app-info screens).
                    val isUninstallDialog = "do you want to uninstall" in wt ||
                        ("uninstall" in wt && ("cancel" in wt || "ok" in wt || "app" in wt))
                    val isUninstall = isUninstallDialog && (isOurApp || recentlyActive)
                    val isClearStorage = ("delete app data" in wt || "all this app" in wt || "all of this app" in wt ||
                        ("clear" in wt && ("data" in wt || "storage" in wt))) && (isOurApp || recentlyActive)
                    val isDeactivate = ("deactivate" in wt && ("admin" in wt || "device" in wt)) || "deactivate this device admin" in wt

                    if (isUninstall || isClearStorage || isDeactivate) {
                        viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
                        autoCancelDialog(r)
                        runCatching { r.recycle() }
                        pollKick("Action is locked while Protection Mode is active.")
                        break
                    }
                    runCatching { r.recycle() }
                }
            }
            main.postDelayed(this, 150)
        }
    }

    private fun startSettingsPoll() {
        outsideSettingsCount = 0
        if (!settingsPollActive) {
            settingsPollActive = true
            main.postDelayed(settingsPollRunnable, 50)
        }
    }

    /** Direct kick used by the poll loop — auto-cancels any dialog and forces Home immediately. */
    private fun pollKick(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastPollKick < 1000L) return
        lastPollKick = now
        runCatching { autoCancelDialog(rootInActiveWindow) }
        kickToHome()
        BlockerStore.incr(this, "tamper")
        showOverlay("🔐 Tamper Protection", "$message\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)", 5000, "Fear Allah and remain steadfast", "Understood")
    }

    /**
     * Called every ~150 ms while the foreground window belongs to a settings package.
     * Scans the root node tree directly — no event timing dependency.
     */
    private fun performSettingsScreenGuard(root: android.view.accessibility.AccessibilityNodeInfo) {
        val rootPkg = root.packageName?.toString().orEmpty()
        if (rootPkg == packageName) return
        if (rootPkg.isNotEmpty() && !isSettingsPkg(rootPkg) && !isInstallerPkg(rootPkg) && rootPkg != "android") return

        // If an install or update dialog is in foreground, allow it:
        if (isInstallOrUpdateDialog(root = root)) return

        // Keeps the App Info scanner alive for as long as the user is sitting inside Settings, even if no
        // accessibility event arrives (e.g. when a slow handler delayed it).
        if (rootPkg.isNotEmpty()) armAppInfoScan(rootPkg, fresh = false, holdMs = APP_INFO_HOLD_ACTIVE_MS)
        val cls = root.className?.toString().orEmpty()

        // ── Device Admin list (any OEM) ──
        if (DEVICE_ADMIN_LIST_CLASSES.any { cls.contains(it, ignoreCase = true) } ||
            cls.contains("DeviceAdmin", ignoreCase = true) ||
            cls.contains("AdminSettings", ignoreCase = true)) {
            viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
            autoCancelDialog(root)
            pollKick("Device Admin settings are locked while Protection Mode is active.")
            return
        }
        // ── Device Admin detail / deactivate ──
        if (DEVICE_ADMIN_DETAIL_CLASSES.any { cls.contains(it, ignoreCase = true) } ||
            cls.contains("DeviceAdminAdd", ignoreCase = true)) {
            viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
            autoCancelDialog(root)
            pollKick("Device Admin deactivation is locked while Protection Mode is active.")
            return
        }

        // ── Full text scan ──
        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(600))
        val rawT = sb.toString().lowercase()
        if ("🔐" in rawT || "tamper protection" in rawT || "focus mode" in rawT) return
        val t = rawT.replace("prevents blocker from being uninstalled.", "")

        // Device Admin screens by content
        val hasAdminContent = ADMIN_SCREEN_TITLES.any { it in t } ||
            "device admin" in t || "device administrator" in t ||
            "deactivate this device admin" in t || "deactivate this device administrator" in t
        if (hasAdminContent) {
            // Set timer so any subsequent package installer uninstall dialog is caught
            viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
            autoCancelDialog(root)
            pollKick("Device Admin settings are locked while Protection Mode is active.")
            return
        }

        // Developer Options
        val hasDevTitle = "developer options" in t || "development settings" in t || "developer mode" in t
        val hasDevMarker = "wireless debugging" in t ||
            "logger buffer" in t || "stay awake" in t || "desktop backup password" in t
        if (hasDevTitle || hasDevMarker) {
            if (adbFixActive()) return // allow automated USB debugging fix
            pollKick("Developer options are locked while Protection Mode is active.")
            return
        }

        // Blocker-specific: check if screen is Blocker's App Info, Storage, or Settings
        val isOurApp = "blocker" in t || packageName.lowercase() in t

        // Blocker-specific: kick the instant ANY settings screen mentions our app.
        // This covers App Info, Storage, Force Stop, Uninstall — all in one sweep.
        if (isOurApp) {
            val isUnknownAppsPermission = ("install unknown" in t || "unknown apps" in t || "allow from this source" in t) &&
                "force stop" !in t && "clear" !in t && "uninstall" !in t && "storage" !in t
            if (!isUnknownAppsPermission && !isInstallOrUpdateDialog(root = root)) {
                viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
                autoCancelDialog(root)
                if (isActualSettingsPkg(rootPkg)) {
                    kickFromAppInfo()
                } else {
                    pollKick("Blocker cannot be uninstalled while Protection Mode is active.")
                }
                return
            }
        }

        // ── Uninstall attempt in package installer / outside settings ──
        val nowForUninstall = System.currentTimeMillis()
        val hasUninstallRisk = ("do you want to uninstall" in t || "uninstallation" in t || "uninstalling" in t ||
            (("uninstall" in t || "delete" in t) && ("ok" in t || "cancel" in t))) &&
            nowForUninstall < viewingBlockerSettingsUntil
        if (hasUninstallRisk) {
            viewingBlockerSettingsUntil = nowForUninstall + 30_000L
            autoCancelDialog(root)
            if (isActualSettingsPkg(rootPkg)) {
                kickFromAppInfo()
            } else {
                pollKick("Blocker cannot be uninstalled while Protection Mode is active.")
            }
            return
        }

        // If we recently saw Blocker's App Info, block any storage/clear sub-screen too.
        // Also independently detect storage screen content mentioning Blocker.
        val nowTs2 = System.currentTimeMillis()
        val storageRisk = STORAGE_SCREEN_TITLES.any { it in t } ||
            "clear storage" in t || "clear data" in t || "clear cache" in t ||
            "delete app data" in t || "manage space" in t || "storage & cache" in t
        if (storageRisk) {
            // Always block if Blocker content is detected on a storage screen
            if (nowTs2 < viewingBlockerSettingsUntil) {
                viewingBlockerSettingsUntil = nowTs2 + 30_000L
                autoCancelDialog(root)
                if (isActualSettingsPkg(rootPkg)) {
                    kickFromAppInfo()
                } else {
                    pollKick("App storage for Blocker is locked while Protection Mode is active.")
                }
            }
        }
    }

    @Volatile private var domains: Set<String> = emptySet()
    @Volatile private var keywords: Set<String> = emptySet()
    @Volatile private var tlds: Set<String> = emptySet()
    @Volatile private var whitelist: Set<String> = emptySet()
    @Volatile private var apps: Set<String> = emptySet()
    @Volatile private var exempt: Set<String> = emptySet()
    @Volatile private var focusAllowed: Set<String> = emptySet()
    @Volatile private var appLimits: Map<String, Int> = emptyMap()
    @Volatile private var currentFgPkg: String? = null
    @Volatile private var fgStartTime: Long = 0L

    private val appLimitWatchdog = object : Runnable {
        override fun run() {
            val pkg = currentFgPkg
            if (pkg != null && pkg != packageName && pkg !in neverBlock && !isSettingsPkg(pkg) && !isInstallerPkg(pkg)) {
                checkAppUsageLimit(pkg)
            }
            main.postDelayed(this, 5000L)
        }
    }

    @Volatile private var blockFbReels = false
    @Volatile private var blockInstaReels = false
    @Volatile private var blockInstaSearch = false
    @Volatile private var blockYtShorts = false
    private val lastGranularHit = HashMap<String, Long>()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key != null && key in RELOAD_KEYS) reload()
        // Instant kick when DeviceAdminReceiver signals a deactivation attempt
        if (key == "admin_disable_requested") {
            val ts = prefs.getLong("admin_disable_requested_ts", 0L)
            if (BlockerStore.active(this) && BlockerStore.shield(this) &&
                !BlockerStore.guardOpen(this, "shield") &&
                System.currentTimeMillis() - ts < 15_000L) {
                main.post {
                    lockOutOfSettings("Device Admin deactivation is locked while Protection Mode is active.")
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (currentFgPkg != null && fgStartTime > 0L) {
                        val delta = (System.currentTimeMillis() - fgStartTime).coerceAtLeast(0L)
                        if (delta > 0L) {
                            BlockerStore.addAppUsageMillis(this@BlockerAccessibilityService, currentFgPkg!!, minOf(delta, 60_000L))
                        }
                    }
                    currentFgPkg = null
                    fgStartTime = 0L
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    fgStartTime = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        BlockerForegroundService.start(this)
        loadAppIdentity()
        BlockerStore.seedDefaultApps(this)   // default app blocks work even before Blocker's own UI is ever opened
        neverBlock = neverBlock + launcherPackages() + cameraPackages()
        reload()
        BlockerStore.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)
        }
        runCatching {
            for (k in ADB_SETTING_KEYS) contentResolver.registerContentObserver(Settings.Global.getUriFor(k), false, adbObserver)
        }
        enforceAdbOff(notify = false)
        main.post(appLimitWatchdog)
    }

    override fun onDestroy() {
        main.removeCallbacks(appLimitWatchdog)
        if (instance === this) instance = null
        BlockerStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { contentResolver.unregisterContentObserver(adbObserver) }
        synchronized(appInfoLock) {
            appInfoBg?.removeCallbacks(appInfoLoop)
            appInfoThread?.quitSafely()
            appInfoThread = null
            appInfoBg = null
            appInfoLoopRunning = false
        }
        removeOverlay()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private fun launcherPackages(): Set<String> = runCatching {
        packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun cameraPackages(): Set<String> = runCatching {
        val pm = packageManager
        val img = pm.queryIntentActivities(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), 0)
            .mapNotNull { it.activityInfo?.packageName }
        val vid = pm.queryIntentActivities(Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE), 0)
            .mapNotNull { it.activityInfo?.packageName }
        (img + vid).toSet()
    }.getOrDefault(emptySet())

    private fun settingsPackages(): Set<String> = runCatching {
        val pm = packageManager
        val s1 = pm.queryIntentActivities(Intent(android.provider.Settings.ACTION_SETTINGS), 0)
            .mapNotNull { it.activityInfo?.packageName }
        val s2 = pm.queryIntentActivities(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")),
            0
        ).mapNotNull { it.activityInfo?.packageName }
        (s1 + s2).toSet()
    }.getOrDefault(emptySet())

    private fun isInstallerPkg(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("packageinstaller") ||
            lower.contains("packagemanager") ||
            (lower.contains("installer") && !lower.contains("permission")) ||
            lower == "com.huawei.appmarket"
    }

    private fun isInstallOrUpdateDialog(ev: AccessibilityEvent? = null, root: AccessibilityNodeInfo? = null): Boolean {
        val evPkg = ev?.packageName?.toString().orEmpty()
        val rootPkg = root?.packageName?.toString().orEmpty()
        val pkg = if (evPkg.isNotEmpty()) evPkg else rootPkg

        // Pure settings (e.g. com.android.settings) is NEVER an install/update dialog
        val lowerPkg = pkg.lowercase()
        if (lowerPkg == "com.android.settings" || lowerPkg == "com.samsung.android.settings") return false

        val evCls = ev?.className?.toString().orEmpty().lowercase()
        val rootCls = root?.className?.toString().orEmpty().lowercase()

        val sb = StringBuilder()
        if (root != null) collectText(root, sb, 0, intArrayOf(120))
        if (ev != null) {
            for (t in ev.text) if (t != null) sb.append(' ').append(t)
            ev.contentDescription?.let { sb.append(' ').append(it) }
        }
        val text = sb.toString().lowercase()

        // If it explicitly asks to uninstall or deactivate, or has App Info controls, it is NOT an install/update dialog
        if ("uninstall" in text || "deactivate" in text || "do you want to uninstall" in text ||
            "force stop" in text || "clear data" in text || "clear storage" in text ||
            "storage & cache" in text || "manage space" in text || "permissions" in text) return false

        // Check text hints
        val hasInstallText = "do you want to update" in text ||
            "do you want to install" in text ||
            "update this app" in text ||
            "install this app" in text ||
            "update this application" in text ||
            "install this application" in text ||
            "staging app" in text ||
            "staging" in text ||
            "installing…" in text ||
            "installing..." in text ||
            "app installed" in text ||
            "scanning for risks" in text ||
            "install anyway" in text ||
            (("install" in text || "update" in text) && ("cancel" in text || "done" in text || "open" in text))

        if (hasInstallText) return true

        val isInstallClass = evCls.contains("install") || rootCls.contains("install") ||
            evCls.contains("staging") || rootCls.contains("staging")
        if (isInstallClass && ("update" in text || "install" in text || "cancel" in text)) return true

        return false
    }

    private fun isSettingsPkg(pkg: String): Boolean {
        val lower = pkg.lowercase()
        // Never treat file managers, document pickers, or downloaders as settings
        if (lower.contains("file") || lower.contains("explorer") || lower.contains("document") ||
            lower.contains("download") || lower.contains("archive") || lower.contains("commander") ||
            lower.contains("totalcmd") || lower.contains("zarchiver") || lower.contains("nbu.files")) {
            return false
        }
        if (pkg in SETTINGS_PKGS || pkg in discoveredSettingsPkgs) return true
        return lower.contains("settings") ||
            lower.contains("packageinstaller") ||
            lower.contains("safecenter") ||
            lower.contains("securitycenter") ||
            lower.contains("phonemanager") ||
            lower.contains("phonemaster") ||
            lower.contains("appmanager") ||
            lower.contains("storagemanager") ||
            lower.contains("securitycom") ||
            lower.contains("scorpio") ||
            lower.contains("ossettingsext") ||
            lower.contains("permissioncontroller") ||
            lower.contains("securitypermission") ||
            lower.contains("managedprovisioning") ||
            lower.contains("engineermode") ||
            lower.contains("devicepolicy")
    }

    private fun isActualSettingsPkg(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (lower.contains("installer") || lower.contains("permission") || lower.contains("launcher") ||
            lower.contains("systemui") || lower.contains("phonemaster") || lower.contains("phonemanager")) return false
        return pkg in discoveredSettingsPkgs || lower.contains("settings") || lower.contains("ossettingsext") || lower.contains("scorpio")
    }

    private fun reload() {
        discoveredSettingsPkgs = settingsPackages()
        neverBlock = setOf("com.android.systemui", "android") + launcherPackages() + cameraPackages()
        domains = BlockerStore.set(this, "domains")
        keywords = BlockerStore.set(this, "keywords") - RETIRED_KEYWORDS
        tlds = BlockerStore.set(this, "tlds")
        whitelist = BlockerStore.set(this, "whitelist")
        apps = BlockerStore.set(this, "apps")
        exempt = BlockerStore.set(this, "scan_exempt")
        focusAllowed = BlockerStore.focusApps(this) + neverBlock + FOCUS_SYSTEM_PKGS + imePackages() + packageName
        blockFbReels = BlockerStore.granularToggle(this, "block_fb_reels")
        blockInstaReels = BlockerStore.granularToggle(this, "block_insta_reels")
        blockInstaSearch = BlockerStore.granularToggle(this, "block_insta_search")
        blockYtShorts = BlockerStore.granularToggle(this, "block_yt_shorts")
        appLimits = BlockerStore.appLimits(this)
        enforceAdbOff(notify = false)
    }

    private val todayUsageCache = HashMap<String, Pair<Long, Long>>() // pkg -> (timestamp, usageMs)
    private val lastEventTime = HashMap<String, Long>()
    private val lastUsageCheck = HashMap<String, Long>()

    private fun getEffectiveUsageMillis(pkg: String): Long {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true
        val now = System.currentTimeMillis()
        val cached = todayUsageCache[pkg]
        val base = if (cached != null && (now - cached.first < 4000L)) {
            cached.second
        } else {
            val fresh = BlockerStore.getTodayUsageMillis(this, pkg)
            todayUsageCache[pkg] = Pair(now, fresh)
            fresh
        }
        val activeSession = if (isInteractive && currentFgPkg == pkg && fgStartTime > 0L) {
            (now - fgStartTime).coerceAtLeast(0L)
        } else 0L
        return base + activeSession
    }

    private fun formatLimitDuration(min: Int): String = when {
        min >= 60 -> {
            val h = min / 60
            val m = min % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        }
        else -> "${min}m"
    }

    private fun checkAppUsageLimit(pkg: String): Boolean {
        val limitMin = appLimits[pkg] ?: return false
        if (limitMin <= 0) return false
        val usedMs = getEffectiveUsageMillis(pkg)
        val limitMs = limitMin * 60_000L
        if (usedMs >= limitMs) {
            val limitStr = formatLimitDuration(limitMin)
            hit(
                "apps",
                "Fear Allah",
                "${labelOf(pkg)} has reached your daily usage limit of $limitStr. Guard your time and deen.\n\n“Take advantage of five before five: your youth before your old age, your health before your sickness, your wealth before your poverty, your free time before your busyness, and your life before your death.”\n— Hadith",
                "Do not destroy your Akhirah"
            )
            return true
        }
        return false
    }

    private fun imePackages(): Set<String> = runCatching {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        try {
            val ev = e ?: return
            val pkg = ev.packageName?.toString() ?: return

            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isInteractive == false) {
                return
            }

            val now = System.currentTimeMillis()
            // Throttle rapid WINDOW_CONTENT_CHANGED events outside settings to eliminate scroll/animation lag
            if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !isSettingsPkg(pkg) && !isInstallerPkg(pkg)) {
                val lastEv = lastEventTime[pkg] ?: 0L
                if (now - lastEv < 75L) return
            }
            lastEventTime[pkg] = now

            if (pkg != packageName && pkg !in neverBlock && !isSettingsPkg(pkg) && !isInstallerPkg(pkg)) {
                if (currentFgPkg != pkg) {
                    if (currentFgPkg != null && fgStartTime > 0L) {
                        val delta = (now - fgStartTime).coerceAtLeast(0L)
                        BlockerStore.addAppUsageMillis(this, currentFgPkg!!, minOf(delta, 60_000L))
                    }
                    currentFgPkg = pkg
                    fgStartTime = now
                }
            } else if (pkg in neverBlock) {
                if (currentFgPkg != null && fgStartTime > 0L) {
                    val delta = (now - fgStartTime).coerceAtLeast(0L)
                    BlockerStore.addAppUsageMillis(this, currentFgPkg!!, minOf(delta, 60_000L))
                }
                currentFgPkg = null
                fgStartTime = 0L
            }

        if (pkg == packageName) {
            viewingBlockerSettingsUntil = 0L
            settingsPollActive = false
            outsideSettingsCount = 0
            main.removeCallbacks(settingsPollRunnable)
            return
        }
        // The App Info / device-admin guard runs FIRST and never stands aside, not even while Blocker is switching
        // USB debugging off (a 14 s window in which every other Settings guard pauses). It also runs before the
        // neverBlock bail-out: some OEMs host app-info pages inside packages that also answer the HOME intent.
        guardBlockerAppInfo(ev, pkg)
        if (checkAdbDialog(pkg)) return
        if (pkg in neverBlock) return
        if (isSettingsPkg(pkg) || isInstallerPkg(pkg) || pkg == "android") {
            if (checkDangerDialog(pkg, ev)) return
        }
        if (BlockerStore.focusActive(this) && focusBlocks(ev, pkg)) return
        if (!BlockerStore.active(this)) return

        // OEM-agnostic proactive intercept: fires on the window-open event BEFORE the screen is visible
        if (BlockerStore.shield(this) && !BlockerStore.guardOpen(this, "shield") && isSettingsPkg(pkg)) {
            val evCls = ev.className?.toString().orEmpty()
            val rootCls = runCatching { rootInActiveWindow?.className?.toString().orEmpty() }.getOrDefault(evCls)
            val winTitle = ev.text.joinToString(" ").trim().lowercase()  // screen title Samsung/AOSP use ev.text[0]
            val now = System.currentTimeMillis()

            // ── 1. Device Admin list screen (OEM-agnostic by title) ─────────────────
            val isAdminListTitle = ADMIN_SCREEN_TITLES.any { it in winTitle }
            val isAdminListCls   = evCls in DEVICE_ADMIN_LIST_CLASSES || rootCls in DEVICE_ADMIN_LIST_CLASSES ||
                evCls.contains("DeviceAdmin", ignoreCase = true) || rootCls.contains("DeviceAdmin", ignoreCase = true) ||
                evCls.contains("AdminSettings", ignoreCase = true) || rootCls.contains("AdminSettings", ignoreCase = true)
            if (isAdminListTitle || isAdminListCls) {
                lockOutOfSettings("Device Admin settings are locked while Protection Mode is active."); return
            }

            // ── 2. Device Admin detail / deactivate screen (title-based) ───────────
            val isAdminDetailCls = evCls in DEVICE_ADMIN_DETAIL_CLASSES || rootCls in DEVICE_ADMIN_DETAIL_CLASSES ||
                evCls.contains("DeviceAdminAdd", ignoreCase = true) || rootCls.contains("DeviceAdminAdd", ignoreCase = true)
            if (isAdminDetailCls && isOurAdminActive()) {
                lockOutOfSettings("Device Admin deactivation is locked while Protection Mode is active."); return
            }

            // ── 3. App Info screen: detect by title OR root-window scan ─────────────
            val appLabelName = if (appLabel.isNotEmpty()) appLabel else "Blocker"
            val titleMatchesBlocker = isAppNameText(winTitle, appLabelName, appVersionName) ||
                ev.text.any { t -> t != null && isAppNameText(t.toString(), appLabelName, appVersionName) }

            // Direct root scan for Blocker App Info / Settings
            val rootForScan = rootInActiveWindow
            if (rootForScan != null) {
                val sb = StringBuilder()
                collectText(rootForScan, sb, 0, intArrayOf(300))
                val txt = sb.toString().lowercase()
                val isOurApp = "blocker" in txt || packageName.lowercase() in txt
                if (isOurApp) {
                    val isUnknownApps = ("install unknown" in txt || "unknown apps" in txt || "allow from this source" in txt) &&
                        "force stop" !in txt && "clear" !in txt && "uninstall" !in txt && "storage" !in txt
                    if (!isUnknownApps && !isInstallOrUpdateDialog(ev, rootForScan)) {
                        viewingBlockerSettingsUntil = now + 30_000L
                        autoCancelDialog(rootForScan)
                        if (isActualSettingsPkg(pkg)) {
                            kickFromAppInfo()
                        } else {
                            lockOutOfSettings("Blocker app settings and storage are locked while Protection Mode is active.")
                        }
                        return
                    }
                }
            } else if (titleMatchesBlocker && !isInstallOrUpdateDialog(ev, null)) {
                viewingBlockerSettingsUntil = now + 30_000L
                if (isActualSettingsPkg(pkg)) {
                    kickFromAppInfo()
                } else {
                    lockOutOfSettings("Blocker app settings and storage are locked while Protection Mode is active.")
                }
                return
            }

            // ── 4. Storage sub-screen: always scan content, also check recent timer ──
            val isStorageTitle = STORAGE_SCREEN_TITLES.any { it in winTitle }
            val isStorageCls   = evCls in APP_STORAGE_CLASSES || rootCls in APP_STORAGE_CLASSES ||
                evCls.contains("StorageSetting", ignoreCase = true) || rootCls.contains("StorageSetting", ignoreCase = true) ||
                evCls.contains("AppStorage", ignoreCase = true) || rootCls.contains("AppStorage", ignoreCase = true)

            if (isStorageTitle || isStorageCls) {
                if (now < viewingBlockerSettingsUntil) {
                    // We were recently on Blocker's App Info — block any storage screen immediately
                    lockOutOfSettings("App storage for Blocker is locked while Protection Mode is active.")
                    return
                }
                // Even without prior context, always scan the screen content for Blocker
                val root = rootInActiveWindow
                if (root != null) {
                    val sb = StringBuilder()
                    collectText(root, sb, 0, intArrayOf(300))
                    val txt = sb.toString().lowercase()
                    if ("blocker" in txt || packageName.lowercase() in txt) {
                        viewingBlockerSettingsUntil = now + 30_000L
                        lockOutOfSettings("App storage for Blocker is locked while Protection Mode is active.")
                        return
                    }
                }
            }

            // ── 5. Any confirmation dialog for clearing/deleting data ───────────────
            //    These dialogs appear on top of Settings and might come from system UI
            val isDangerDialog = ("clear" in winTitle || "delete" in winTitle || "erase" in winTitle) &&
                "tamper" !in winTitle && "shield" !in winTitle
            if (isDangerDialog && now < viewingBlockerSettingsUntil) {
                lockOutOfSettings("App data deletion for Blocker is locked while Protection Mode is active.")
                return
            }
        }

        when {
            pkg in apps -> {
                if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    hit(
                        "apps",
                        "Fear Allah",
                        "${labelOf(pkg)} is on your block list. Guard your time and deen.\n\n“Indeed, the hearing, the sight and the heart — about all of these you will be questioned.”\n— Surah Al-Isra (17:36)",
                        "Do not destroy your Akhirah"
                    )
                }
                return
            }
            pkg in BROWSER_URL_IDS -> if (checkBrowser(pkg)) return
            isSettingsPkg(pkg) -> {
                startSettingsPoll()   // start continuous 200-ms guard whenever we enter settings
                checkSettings(ev)
                return
            }
            pkg in GRANULAR_TARGET_PKGS -> {
                if (checkGranularInterception(pkg, ev)) return
            }
        }
        if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || now - (lastUsageCheck[pkg] ?: 0L) > 5000L) {
            lastUsageCheck[pkg] = now
            if (checkAppUsageLimit(pkg)) return
        }
        if (pkg !in exempt) scanScreen(pkg)
    } catch (_: Throwable) {
        // Safe guard against any unexpected framework or node lifecycle exceptions
    }
}

    // ---------- Focus mode ----------

    /** During focus mode everything outside the essential apps / system helpers is kicked to the home screen. */
    private fun focusBlocks(ev: AccessibilityEvent, pkg: String): Boolean {
        if (pkg in focusAllowed || pkg in BlockerStore.scheduleAllowedApps(this)) return false
        val trusted = ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            rootInActiveWindow?.packageName?.toString() == pkg
        if (!trusted) return false
        val now = System.currentTimeMillis()
        if (now - lastHit >= 700) {
            lastHit = now
            BlockerStore.incr(this, "focus")
            performGlobalAction(GLOBAL_ACTION_HOME)
            showOverlay(
                "Focus Mode Active",
                "Only your essential apps are available (${leftText()} remaining).\n\n“Take advantage of five before five: your youth before your old age, your health before your sickness, your wealth before your poverty, your free time before your busyness, and your life before your death.”\n— Hadith",
                5000,
                "Guard your time for what benefits you",
                "Astaghfirullah"
            )
        }
        return true
    }

    private fun leftText(): String {
        val ms = BlockerStore.focusRemainingMs(this)
        val m = ms.coerceAtLeast(0L) / 60_000L + 1
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    // ---------- URL bar ----------

    private fun checkBrowser(pkg: String): Boolean {
        if (System.currentTimeMillis() < (quietUntil[pkg] ?: 0L)) return true
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != pkg) return false
        for (id in BROWSER_URL_IDS[pkg].orEmpty()) {
            val text = root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.text?.toString()
            val rule = if (!text.isNullOrBlank()) matchAddress(text) else null
            if (rule != null) {
                redirectBrowser(
                    pkg,
                    "sites",
                    "Fear Allah",
                    "“$rule” was blocked to safeguard your modesty and purity.\n\n“Does he not know that Allah sees?”\n— Surah Al-Alaq (96:14)",
                    "Do not destroy your Akhirah"
                )
                return true
            }
        }
        return false
    }

    /** Hostnames use substring matching (so a blocked word anywhere in the domain counts); free text uses whole words. */
    private fun matchAddress(raw: String): String? {
        val t = raw.trim().lowercase()
        hostOf(t)?.let { host ->
            var d = host
            while (true) {
                if (d in domains) return d
                val i = d.indexOf('.')
                if (i < 0) break
                d = d.substring(i + 1)
            }
            if (tlds.any { host.endsWith(it) }) return host
            val norm = host.replace("-", "").replace("_", "")
            keywords.firstOrNull { norm.contains(it) }?.let { return it }
        }
        return wholeWordHit(t)
    }

    private fun hostOf(t: String): String? {
        val h = t.removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore(':').removePrefix("www.")
        return h.takeIf { it.contains('.') && !it.contains(' ') }
    }

    // ---------- Screen content monitor ----------

    private fun scanScreen(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val now = System.currentTimeMillis()
        if (now < (quietUntil[pkg] ?: 0L)) return
        if (now - (lastScan[pkg] ?: 0L) < SCAN_GAP_MS) return
        lastScan[pkg] = now

        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(800))
        if (sb.isEmpty()) return
        val kw = wholeWordHit(sb.toString().lowercase()) ?: return
        screenHit(pkg, kw)
    }

    /**
     * Whole-word keyword match: "camera" never matches "cam", "essex" never matches "sex".
     * Supports both single-word and multi-word keywords (e.g. "live cam", "adult chat").
     * A whitelist phrase (single or multi-word, e.g. "sex education", "ai cam") shields any keyword inside it.
     */
    private fun wholeWordHit(t: String): String? {
        val safe = ArrayList<IntRange>()
        for (w in whitelist) {
            if (w.isEmpty()) continue
            var from = 0
            while (true) {
                val i = t.indexOf(w, from)
                if (i < 0) break
                safe.add(i until (i + w.length))
                from = i + 1
            }
        }

        // 1. Check multi-word keywords first (e.g. "live cam", "adult chat")
        for (kw in keywords) {
            if (!kw.contains(' ') && !kw.contains('-') && !kw.contains('_')) continue
            var from = 0
            while (from < t.length) {
                val idx = t.indexOf(kw, from)
                if (idx < 0) break
                val endIdx = idx + kw.length
                val startBoundary = (idx == 0 || !t[idx - 1].isLetterOrDigit())
                val endBoundary = (endIdx == t.length || !t[endIdx].isLetterOrDigit())
                if (startBoundary && endBoundary) {
                    val isSafe = safe.any { s -> !(endIdx <= s.first || idx >= s.last + 1) }
                    if (!isSafe) return kw
                }
                from = idx + 1
            }
        }

        // 2. Check single-word keywords with token scanning
        var i = 0
        while (i < t.length) {
            if (!t[i].isLetterOrDigit()) {
                i++
                continue
            }
            var j = i
            while (j < t.length && t[j].isLetterOrDigit()) j++
            val token = t.substring(i, j)
            if (token in keywords && safe.none { it.first <= i && j <= it.last + 1 }) return token
            i = j
        }
        return null
    }

    private fun screenHit(pkg: String, kw: String) {
        // a longer cooldown than the 1s scan gap: stops the same static or recurring text from
        // re-triggering the popup over and over while the user is looking at (or reopens) that screen
        quietUntil[pkg] = System.currentTimeMillis() + 5000
        if (pkg in BROWSER_URL_IDS) {
            BlockerStore.incr(this, "screen")
            if (Build.VERSION.SDK_INT < 30 || !goToGoogle(pkg)) performGlobalAction(GLOBAL_ACTION_BACK)
            showOverlay(
                "Fear Allah",
                "Inappropriate content was detected and redirected.\n\n“Tell the believing men to lower their gaze and guard their modesty. That is purer for them. Indeed, Allah is aware of what they do.”\n— Surah An-Nur (24:30)",
                5000,
                "Do not destroy your Akhirah",
                "Astaghfirullah"
            )
            return
        }
        BlockerStore.incr(this, "screen")
        performGlobalAction(GLOBAL_ACTION_HOME)
        showOverlay(
            "Fear Allah",
            "Inappropriate content appeared in ${labelOf(pkg)}, so it was closed.\n\n“Indeed, Allah is ever, over you, an Observer.”\n— Surah An-Nisa (4:1)",
            5000,
            "Do not destroy your Akhirah",
            "Astaghfirullah"
        )
    }

    /** Sends the current tab straight to google.com. Browsers are never locked or held open. */
    private fun redirectBrowser(pkg: String, kind: String, heading: String, body: String, subheading: String? = null) {
        quietUntil[pkg] = System.currentTimeMillis() + 5000L
        BlockerStore.incr(this, kind)
        if (Build.VERSION.SDK_INT < 30 || !goToGoogle(pkg)) performGlobalAction(GLOBAL_ACTION_BACK)
        showOverlay(heading, body, 5000, subheading, "Astaghfirullah")
    }

    @SuppressLint("NewApi")
    private fun goToGoogle(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val id = BROWSER_URL_IDS[pkg].orEmpty()
            .firstOrNull { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() } ?: return false
        val bar = root.findAccessibilityNodeInfosByViewId(id).first()
        bar.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        main.postDelayed({
            val b2 = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull() ?: return@postDelayed
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, REDIRECT_URL)
            }
            if (!b2.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                performGlobalAction(GLOBAL_ACTION_BACK) // this browser's address bar can't be edited directly
                return@postDelayed
            }
            main.postDelayed({
                rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
                    ?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }, 150)
        }, 150)
        return true
    }

    private fun labelOf(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    // ---------- Settings shield ----------

    private fun isOurAdminActive(): Boolean = runCatching {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        dpm.isAdminActive(android.content.ComponentName(this, BlockerDeviceAdminReceiver::class.java)) ||
            dpm.isAdminActive(android.content.ComponentName(this, MyDeviceAdminReceiver::class.java))
    }.getOrDefault(false)

    private fun checkAdbDialog(pkg: String): Boolean {
        if (!BlockerStore.active(this) || !BlockerStore.shield(this) || BlockerStore.guardOpen(this, "shield")) return false
        if (pkg != "com.android.systemui" && pkg != "android" && !isSettingsPkg(pkg)) return false
        val root = rootInActiveWindow ?: return false
        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(100))
        val t = sb.toString().lowercase()
        val isUsbAuth = "allow usb debugging" in t || ("usb debugging" in t && ("fingerprint" in t || "rsa" in t || "always allow" in t))
        val isWifiAuth = "allow wireless debugging" in t || ("wireless debugging" in t && ("fingerprint" in t || "always allow" in t || "pair" in t || "pairing" in t))
        if (isUsbAuth || isWifiAuth) {
            val now = System.currentTimeMillis()
            if (now - lastHit < 700) return true
            lastHit = now
            BlockerStore.incr(this, "tamper")
            val cancelBtn = root.findAccessibilityNodeInfosByViewId("android:id/button2").firstOrNull()
                ?: root.findAccessibilityNodeInfosByText("Cancel").firstOrNull()
                ?: root.findAccessibilityNodeInfosByText("cancel").firstOrNull()
                ?: root.findAccessibilityNodeInfosByText("CANCEL").firstOrNull()
            if (cancelBtn != null) {
                cancelBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cancelBtn.recycle()
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            showOverlay("🔐 Tamper Protection", "Debugging authorization is locked while Protection Mode is active.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)", 5000, "Fear Allah and remain steadfast", "Understood")
            return true
        }
        return false
    }

    private fun checkDangerDialog(pkg: String, ev: AccessibilityEvent): Boolean {
        if (pkg == packageName) return false
        if (pkg in neverBlock) return false
        if (!isSettingsPkg(pkg) && !isInstallerPkg(pkg) && pkg != "android") return false
        if (!BlockerStore.active(this) || !BlockerStore.shield(this) || BlockerStore.guardOpen(this, "shield")) return false
        val root = rootInActiveWindow ?: ev.source ?: return false
        val rootPkg = root.packageName?.toString().orEmpty()
        if (rootPkg == packageName) return false
        if (rootPkg.isNotEmpty() && !isSettingsPkg(rootPkg) && !isInstallerPkg(rootPkg) && rootPkg != "android") return false

        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(250))
        val rawT = sb.toString().lowercase()
        if ("🔐" in rawT || "tamper protection" in rawT || "focus mode" in rawT) return false
        val t = rawT.replace("prevents blocker from being uninstalled.", "")

        val isOurApp = "blocker" in t || packageName.lowercase() in t
        val now = System.currentTimeMillis()

        // If this is PackageInstaller or an install/update dialog, strictly distinguish between Uninstall vs Update/Install:
        if (isInstallerPkg(pkg) || isInstallerPkg(rootPkg) || (!isSettingsPkg(pkg) && isInstallOrUpdateDialog(ev, root))) {
            val evCls = ev.className?.toString().orEmpty().lowercase()
            val isUninstall = "do you want to uninstall" in t ||
                ("uninstall" in t && ("cancel" in t || "ok" in t || "app" in t)) ||
                "uninstall" in evCls
            if (!isUninstall) {
                // This is an install or update dialog (e.g. "Do you want to update this app?")
                // ALWAYS ALLOW IT! Never block or auto-cancel updates!
                return false
            }
        }

        // 1. Uninstall attempt (package installer or confirmation dialog)
        // Also catches generic "Do you want to uninstall this app?" from package installer
        // when we have timer context (i.e. we were just on a Device Admin or App Info screen).
        // Not gated to isInstallerPkg specifically: the caller already restricted pkg to
        // settings/installer/android, and some OEMs show this confirmation from com.android.settings
        // itself rather than a separate installer process.
        val isUninstallDialog = "do you want to uninstall" in t ||
            ("uninstall" in t && ("cancel" in t || "ok" in t || "app" in t))
        val isUninstall = isUninstallDialog && (isOurApp || now < viewingBlockerSettingsUntil)
        if (isUninstall) {
            viewingBlockerSettingsUntil = now + 30_000L
            autoCancelDialog(root)
            lockOutOfSettings("Blocker cannot be uninstalled while Protection Mode is active.")
            return true
        }

        // 2. Clear data / delete app data / storage clearing
        val isStorageDanger = "delete app data" in t || "all this app" in t || "all of this app" in t ||
            ("delete" in t && "permanently" in t) ||
            ("clear" in t && ("storage" in t || "data" in t))
        if (isStorageDanger && (isOurApp || now < viewingBlockerSettingsUntil)) {
            viewingBlockerSettingsUntil = now + 8_000L
            autoCancelDialog(root)
            lockOutOfSettings("Clearing app storage for Blocker is locked while Protection Mode is active.")
            return true
        }

        // 3. Device Admin deactivation attempt
        val isAdminDeactivate = ("deactivate" in t && ("admin" in t || "device" in t)) ||
            "deactivate this device admin" in t || "device administrator" in t
        if (isAdminDeactivate && (isOurApp || "admin" in t)) {
            autoCancelDialog(root)
            lockOutOfSettings("Device Admin settings are locked while Protection Mode is active.")
            return true
        }

        // 4. App Info screen for Blocker (Open / Uninstall / Force stop / Storage / Permissions)
        val isAppInfoScreen = isOurApp && (
            "app info" in t || "application info" in t || "app details" in t ||
            "force stop" in t || "storage" in t || "clear" in t ||
            ("notifications" in t && "permissions" in t) ||
            ("mobile data" in t && "battery" in t) ||
            ("open" in t && ("uninstall" in t || "disable" in t || "permissions" in t || "force stop" in t))
        ) && !("all apps" in t && "force stop" !in t && "storage" !in t)
        if (isAppInfoScreen) {
            viewingBlockerSettingsUntil = now + 8_000L
            autoCancelDialog(root)
            if (isActualSettingsPkg(pkg) || isActualSettingsPkg(rootPkg)) {
                kickFromAppInfo()
            } else {
                lockOutOfSettings("Blocker app settings are locked while Protection Mode is active.")
            }
            return true
        }

        return false
    }

    private fun checkSettings(ev: AccessibilityEvent) {
        if (!BlockerStore.active(this) || !BlockerStore.shield(this) || BlockerStore.guardOpen(this, "shield")) return
        val root = rootInActiveWindow ?: ev.source ?: return
        val cls = root.className?.toString().orEmpty()
        val evCls = ev.className?.toString().orEmpty()
        val evText = ev.text.joinToString(" ").lowercase()

        if (cls in DEVICE_ADMIN_LIST_CLASSES) {
            viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
            autoCancelDialog(root)
            lockOutOfSettings()
            return
        }
        if (cls in DEVICE_ADMIN_DETAIL_CLASSES ||
            cls.contains("DeviceAdminAdd", ignoreCase = true) ||
            cls.contains("DeviceAdmin", ignoreCase = true)) {
            viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
            autoCancelDialog(root)
            lockOutOfSettings()
            return
        }

        // Generic uninstall-confirmation dialog, independent of exact class/package: catches
        // "Do you want to uninstall this app?" whether it names Blocker explicitly or not, as long
        // as we recently saw Blocker-specific admin/app-info content (viewingBlockerSettingsUntil).
        // This is what actually catches the follow-up install-confirmation screen that appears
        // right after the device-admin "disable and uninstall" step.
        run {
            val evTextNow = ev.text.joinToString(" ").lowercase()
            val quickSb = StringBuilder()
            collectText(root, quickSb, 0, intArrayOf(300))
            val quickT = "${quickSb.toString().lowercase()} $evTextNow"
            val looksLikeUninstall = "do you want to uninstall" in quickT ||
                ("uninstall" in quickT && ("cancel" in quickT || "ok" in quickT))
            val nowMs = System.currentTimeMillis()
            if (looksLikeUninstall && (nowMs < viewingBlockerSettingsUntil || "blocker" in quickT)) {
                viewingBlockerSettingsUntil = nowMs + 30_000L
                autoCancelDialog(root)
                lockOutOfSettings("Blocker cannot be uninstalled while Protection Mode is active.")
                return
            }
        }

        // 1. Developer options activity & event detection
        val isDevActivity = cls in DEV_OPTIONS_CLASSES ||
            evCls in DEV_OPTIONS_CLASSES ||
            cls.contains("Development", ignoreCase = true) ||
            cls.contains("Developer", ignoreCase = true) ||
            evCls.contains("Development", ignoreCase = true) ||
            evCls.contains("Developer", ignoreCase = true) ||
            evText.contains("developer option") ||
            evText.contains("developer mode") ||
            evText.contains("development setting")
        if (isDevActivity) {
            if (adbFixActive()) return
            lockOutOfSettings("Developer options are locked while Protection Mode is active.")
            return
        }

        if (root.packageName?.toString() == packageName) return
        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(800))
        val rawT = sb.toString().lowercase()
        if ("🔐" in rawT || "tamper protection" in rawT || "focus mode" in rawT) return
        // Strip Blocker's own device-admin explanation text before matching: it legitimately contains
        // "blocker" + "uninstalled" together, which would otherwise look identical to a real attempt.
        val t = rawT.replace("prevents blocker from being uninstalled.", "")
        val fullText = "$t $evText"

        // 2. Comprehensive Developer Options content detection
        val hasDevTitle = "developer options" in fullText ||
            "developer mode" in fullText ||
            "development settings" in fullText ||
            "development options" in fullText ||
            "developer settings" in fullText

        val hasUniqueDevMarker = "wireless debugging" in fullText ||
            "desktop backup password" in fullText ||
            "logger buffer" in fullText ||
            "stay awake" in fullText ||
            "bluetooth hci snoop log" in fullText ||
            "running services" in fullText ||
            "system tracing" in fullText ||
            "mock location" in fullText ||
            "verify apps over usb" in fullText ||
            "pointer location" in fullText ||
            "show taps" in fullText ||
            "window animation scale" in fullText ||
            "transition animation scale" in fullText ||
            "animator duration scale" in fullText ||
            "force 4x msaa" in fullText ||
            "disable usb audio routing" in fullText ||
            "strict mode enabled" in fullText ||
            "background process limit" in fullText ||
            "standby apps" in fullText ||
            "show all anrs" in fullText

        if (hasDevTitle || hasUniqueDevMarker) {
            if (adbFixActive()) return
            lockOutOfSettings("Developer options are locked while Protection Mode is active.")
            return
        }

        // 3. App Storage Clearing & App Info Protection for Blocker (even if Device Admin was removed)
        val storageRiskTerms = listOf(
            "clear storage", "clear data", "manage space", "clear cache", "delete app data", "clear all data",
            "delete data", "clear app data", "all of this app's data will be deleted", "all this app's data will be deleted"
        )
        val isStorageRisk = storageRiskTerms.any { it in fullText }
        val isOurApp = "blocker" in fullText || packageName.lowercase() in fullText
        val hasRiskAction = APP_RISK_TERMS.any { it in fullText } || isStorageRisk

        // If currently viewing Blocker's settings or app details
        val isUnknownApps = ("install unknown" in fullText || "unknown apps" in fullText || "allow from this source" in fullText) &&
            "force stop" !in fullText && "clear" !in fullText && "storage" !in fullText && "uninstall" !in fullText
        if (isOurApp && !isUnknownApps && !isInstallOrUpdateDialog(ev, root)) {
            viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
            lockOutOfSettings("Blocker app settings and storage are locked while Protection Mode is active.")
            return
        }

        // If viewing storage screen/dialog: requires storage-specific terms + timer context or our app name
        val nowTs = System.currentTimeMillis()
        val isOnStorageScreen = isStorageRisk || "storage & cache" in fullText || "user data" in fullText
        if (isOnStorageScreen && (nowTs < viewingBlockerSettingsUntil || isOurApp)) {
            viewingBlockerSettingsUntil = nowTs + 30_000L
            lockOutOfSettings("Clearing app storage for Blocker is locked while Protection Mode is active.")
            return
        }

        // Independent check for system "Delete app data?" confirmation dialog
        if (("delete app data" in fullText || "clear app data" in fullText || "all this app's data will be deleted" in fullText || "all of this app's data will be deleted" in fullText) &&
            (nowTs < viewingBlockerSettingsUntil || isOurApp)) {
            lockOutOfSettings("Clearing app storage for Blocker is locked while Protection Mode is active.")
            return
        }

        // 4. Blocklist keyword check
        val kw = wholeWordHit(t)
        if (kw != null) {
            lockOutOfSettings("\"$kw\" is on your block list.")
            return
        }

        val direct = DIRECT_TERMS.any { it in fullText }
        if (direct || (isOurApp && hasRiskAction)) lockOutOfSettings()
    }

    // ---------- Blocker App Info guard ----------
    //
    // Closes the Settings > Apps > App management > App list > Blocker route (the page with Open / Uninstall /
    // Force stop) and the "Deactivate & uninstall" page that Uninstall leads to. A page is recognised when BOTH
    // are on screen at the same moment:
    //   1. the app's name   - resolved at runtime from the app's own label (= @string/app_name), exact match
    //   2. an action label  - "Uninstall", "Force stop" or "Deactivate" (static OS strings)
    // The version is deliberately NOT required: it is the last row Settings draws (bottom of the list) and the
    // Deactivate & uninstall page does not show it at all, so waiting for it only delayed or missed the block.
    //
    // Four layers, fastest first:
    //   a. Row tap: tapping a row labelled exactly "Blocker" inside Settings sends Home BEFORE the page opens.
    //   b. Device admin screens: the window class (DeviceAdminAdd etc.) is checked on the event itself, no IPC.
    //   c. Event-triggered scan: every window-open / content event queues one scan at the front of the scanner
    //      thread's queue instead of waiting for the next tick.
    //   d. Ticker: the scanner polls every 25 ms for 1.5 s after a window opens (60 ms afterwards).
    //
    // Latency model: the accessibility callback only ARMS the scanner (a few field writes, no IPC). The scanner
    // runs on its own HandlerThread, so it is never stuck behind the heavier main-thread Settings handlers. Each
    // poll is ONE targeted findAccessibilityNodeInfosByText lookup for the usual non-match (2 worst case). A
    // bounded early-exit walk is kept as a fallback for screens whose framework doesn't answer text lookups
    // (e.g. Compose) and only runs on every 6th poll.

    private fun shieldEngaged(): Boolean =
        BlockerStore.active(this) && BlockerStore.shield(this) && !BlockerStore.guardOpen(this, "shield")

    @Suppress("DEPRECATION")
    private fun loadAppIdentity() {
        appLabel = runCatching { applicationInfo.loadLabel(packageManager).toString().trim() }.getOrDefault("")
        if (appLabel.isEmpty()) appLabel = "Blocker"
        appVersionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty().trim()
        }.getOrDefault("")
    }

    private fun looksLikeAppInfoClass(cls: CharSequence?): Boolean {
        val c = cls?.toString()?.lowercase() ?: return false
        return APP_INFO_CLASS_HINTS.any { it in c }
    }

    /** Window title (event text or content description) equal to the app's name - the title bar of Blocker's own App Info page. */
    private fun titleIsAppName(ev: AccessibilityEvent): Boolean {
        val label = if (appLabel.isNotEmpty()) appLabel else "Blocker"
        for (t in ev.text) if (t != null && isAppNameText(t.toString(), label, appVersionName)) return true
        val cd = ev.contentDescription?.toString()
        if (cd != null && isAppNameText(cd, label, appVersionName)) return true
        return false
    }

    /** Accessibility-callback side: instant kick if title is Blocker, otherwise arm the scanner. */
    private fun guardBlockerAppInfo(ev: AccessibilityEvent, pkg: String) {
        if (isInstallOrUpdateDialog(ev, rootInActiveWindow)) return
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            guardBlockerRowClick(ev, pkg)
            return
        }
        val stateChange = ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!stateChange && ev.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if (pkg == "com.android.systemui" || pkg == "android") return

        val nowUp = SystemClock.uptimeMillis()
        val titleMatches = titleIsAppName(ev)
        val announced = stateChange && (looksLikeAppInfoClass(ev.className) || titleMatches)
        if (announced) {
            appInfoWatchPkg = pkg
            appInfoWatchUntil = nowUp + 5_000L
        }
        val settingsLike = (isSettingsPkg(pkg) || isInstallerPkg(pkg)) && pkg !in neverBlock
        val watched = pkg == appInfoWatchPkg && nowUp < appInfoWatchUntil
        if (settingsLike) enforceAdbOffThrottled()
        if (!settingsLike && !watched) return
        if (!shieldEngaged()) return

        if (stateChange) {
            lastWindowOpenUp = nowUp
            val cls = ev.className?.toString()?.lowercase().orEmpty()
            if (settingsLike && DEVICE_ADMIN_CLASS_HINTS.any { it in cls } && ("deviceadminadd" !in cls || isOurAdminActive())) {
                pollKick("Device Admin settings are locked while Protection Mode is active.")
                return
            }
            if (titleMatches && !isInstallOrUpdateDialog(ev, rootInActiveWindow)) {
                if (isActualSettingsPkg(pkg)) {
                    kickFromAppInfo()
                } else {
                    autoCancelDialog(rootInActiveWindow)
                    pollKick("Blocker cannot be uninstalled while Protection Mode is active.")
                }
                return
            }
        }

        // Direct root content check on window open / content change: fires immediately if Blocker App Info or Uninstall is visible
        val root = rootInActiveWindow
        if (root != null) {
            val sb = StringBuilder()
            collectText(root, sb, 0, intArrayOf(300))
            val t = sb.toString().lowercase()
            val isOurApp = "blocker" in t || packageName.lowercase() in t
            if (isOurApp) {
                val isUnknownApps = ("install unknown" in t || "unknown apps" in t || "allow from this source" in t) &&
                    "force stop" !in t && "clear" !in t && "storage" !in t && "uninstall" !in t
                if (!isUnknownApps && !isInstallOrUpdateDialog(ev, root)) {
                    if (isActualSettingsPkg(pkg)) {
                        kickFromAppInfo()
                        return
                    } else {
                        viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
                        autoCancelDialog(root)
                        pollKick("Blocker cannot be uninstalled while Protection Mode is active.")
                        return
                    }
                }
            }
        }

        if (settingsLike) startSettingsPoll()
        armAppInfoScan(
            pkg, fresh = stateChange,
            holdMs = if (stateChange) APP_INFO_HOLD_OPEN_MS else APP_INFO_HOLD_ACTIVE_MS,
            immediate = stateChange || nowUp - lastWindowOpenUp < APP_INFO_FRESH_MS
        )
    }

    /**
     * Layer a: the tap on the "Blocker" row of an app list (or search result) inside Settings. The click event
     * arrives while the App Info page is still being launched, so Home goes out before it is ever drawn.
     * Only taps inside Settings count; Blocker's own screens and PackageInstaller never reach this.
     */
    private fun guardBlockerRowClick(ev: AccessibilityEvent, pkg: String) {
        if (!(isSettingsPkg(pkg) || isInstallerPkg(pkg)) || pkg in neverBlock) return
        if (!shieldEngaged()) return
        if (isInstallOrUpdateDialog(ev, rootInActiveWindow)) return
        // Do not intercept clicks if user is granting install permission in "Install unknown apps"
        val winTitle = ev.text.joinToString(" ").lowercase()
        if ("install unknown" in winTitle || "unknown apps" in winTitle || "allow from this source" in winTitle) return
        val name = if (appLabel.isNotEmpty()) appLabel else "Blocker"
        val ver = appVersionName
        var hit = false
        for (t in ev.text) {
            val s = t?.toString().orEmpty()
            if (s.endsWith(".apk", ignoreCase = true) || s.contains(".apk", ignoreCase = true)) return
            if (isAppNameText(s, name, ver) || s.equals(name, ignoreCase = true) || s.equals("Blocker", ignoreCase = true)) {
                hit = true; break
            }
        }
        if (!hit) {
            val cd = ev.contentDescription?.toString().orEmpty()
            if (cd.endsWith(".apk", ignoreCase = true) || cd.contains(".apk", ignoreCase = true)) return
            hit = isAppNameText(cd, name, ver) || cd.equals(name, ignoreCase = true) || cd.equals("Blocker", ignoreCase = true)
        }
        if (!hit) {
            val src = ev.source
            if (src != null) {
                // Native fast lookup across row subtree
                val nodes = src.findAccessibilityNodeInfosByText(name) ?: src.findAccessibilityNodeInfosByText("Blocker")
                if (!nodes.isNullOrEmpty()) {
                    for (n in nodes) {
                        val nt = n.text?.toString().orEmpty()
                        val nd = n.contentDescription?.toString().orEmpty()
                        if (nt.contains(".apk", ignoreCase = true) || nd.contains(".apk", ignoreCase = true)) {
                            nodes.forEach { it.recycle() }
                            runCatching { src.recycle() }
                            return
                        }
                        if (nt.contains(name, ignoreCase = true) || nd.contains(name, ignoreCase = true) ||
                            nt.contains("Blocker", ignoreCase = true) || nd.contains("Blocker", ignoreCase = true)) {
                            hit = true
                        }
                        n.recycle()
                        if (hit) break
                    }
                }
                if (!hit) {
                    hit = rowShowsName(src, name, ver, 0, intArrayOf(30))
                }
                runCatching { src.recycle() }
            }
        }
        if (hit) kickFromAppInfo()
    }

    /** Looks for the app's exact name in a tapped row: the node itself and a few levels of children. */
    @Suppress("DEPRECATION")
    private fun rowShowsName(n: AccessibilityNodeInfo?, name: String, ver: String, depth: Int, budget: IntArray): Boolean {
        if (n == null || depth > 5 || budget[0]-- <= 0) return false
        val t = n.text?.toString()
        val d = n.contentDescription?.toString()
        if ((t != null && isAppNameText(t, name, ver)) || (d != null && isAppNameText(d, name, ver))) return true
        for (i in 0 until n.childCount) {
            val c = n.getChild(i)
            val done = rowShowsName(c, name, ver, depth + 1, budget)
            c?.recycle()
            if (done) return true
        }
        return false
    }

    /** Extends the scanner's active window (starting its thread / loop if needed). Cheap and thread-safe. */
    private fun armAppInfoScan(pkg: String, fresh: Boolean, holdMs: Long, immediate: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val h: Handler
        synchronized(appInfoLock) {
            appInfoCandidatePkg = pkg
            if (now + holdMs > appInfoHotUntil) appInfoHotUntil = now + holdMs
            if (fresh && now + APP_INFO_FRESH_MS > appInfoFreshUntil) appInfoFreshUntil = now + APP_INFO_FRESH_MS
            h = appInfoBg ?: Handler(HandlerThread("BlockerAppInfoGuard").also {
                it.start()
                appInfoThread = it
            }.looper).also { appInfoBg = it }
            if (!appInfoLoopRunning) {
                appInfoLoopRunning = true
                h.post(appInfoLoop)
            }
        }
        if (immediate) requestImmediateAppInfoScan(h, now)
    }

    /** Layer c: one scan at the FRONT of the scanner queue, so an event never waits for the next tick. */
    private fun requestImmediateAppInfoScan(h: Handler, now: Long) {
        if (appInfoNowPending || now - lastImmediateUp < APP_INFO_IMMEDIATE_GAP_MS) return
        lastImmediateUp = now
        appInfoNowPending = true
        h.postAtFrontOfQueue(appInfoNow)
    }

    /** One scan of the active window (scanner thread). */
    private fun scanAppInfoOnce(pkg: String, deep: Boolean) {
        if (pkg.isEmpty() || !isSettingsPkg(pkg)) return
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val rPkg = root.packageName?.toString().orEmpty()
        if (rPkg.isNotEmpty() && !isSettingsPkg(rPkg)) return
        if (!matchesBlockerAppInfo(root, deep)) return
        val activePkg = rPkg.ifEmpty { pkg }
        if (isActualSettingsPkg(activePkg)) {
            kickFromAppInfo()
        } else {
            autoCancelDialog(root)
            pollKick("Blocker cannot be uninstalled while Protection Mode is active.")
        }
        val now = SystemClock.uptimeMillis()
        synchronized(appInfoLock) {
            // keep watching briefly (at the relaxed cadence) in case HOME didn't take, then go idle
            appInfoHotUntil = now + 400L
            appInfoFreshUntil = 0L
        }
    }

    /**
     * The name + action test. Rarest lookup first, so ordinary screens leave after a single lookup.
     * [deep] additionally allows the bounded walk (used for post-open settle passes only).
     */
    private fun matchesBlockerAppInfo(root: AccessibilityNodeInfo, deep: Boolean): Boolean {
        val name = if (appLabel.isNotEmpty()) appLabel else "Blocker"
        val ver = appVersionName      // only used to recognise "Name  version" shown inside a single node
        if (runCatching { fastMatch(root, name, ver) || (deep && walkMatch(root, name, ver)) }.getOrDefault(false)) {
            return true
        }
        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(300))
        val t = sb.toString().lowercase()
        val isOurApp = name.lowercase() in t || "blocker" in t || packageName.lowercase() in t
        if (!isOurApp) return false
        val isUninstallDialog = ("do you want to uninstall" in t || "uninstall this app" in t) &&
            "force stop" !in t && "storage" !in t
        if (isUninstallDialog) return false
        val isUnknownApps = ("install unknown" in t || "unknown apps" in t || "allow from this source" in t) &&
            "force stop" !in t && "clear" !in t && "storage" !in t && "uninstall" !in t
        if (isUnknownApps) return false
        if (isInstallOrUpdateDialog(root = root)) return false
        return true
    }

    private fun fastMatch(root: AccessibilityNodeInfo, name: String, ver: String): Boolean {
        // name first: it is the rarest text, so ordinary screens leave after this single lookup
        if (!anyNodeMatches(root, name) { isAppNameText(it, name, ver) }) return false
        return APP_INFO_ACTION_TEXTS.any { a -> anyNodeMatches(root, a) { it.contains(a, ignoreCase = true) } }
    }

    /** One framework-side text search (a single IPC); every node it returns is verified and recycled. */
    @Suppress("DEPRECATION")
    private fun anyNodeMatches(root: AccessibilityNodeInfo, query: String, accept: (String) -> Boolean): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(query) ?: return false
        var hit = false
        for (n in nodes) {
            if (!hit) {
                val t = n.text?.toString()
                val d = n.contentDescription?.toString()
                hit = (t != null && accept(t)) || (d != null && accept(d))
            }
            n.recycle()
        }
        return hit
    }

    private fun walkMatch(root: AccessibilityNodeInfo, name: String, ver: String): Boolean {
        val found = intArrayOf(0)
        return walkAppInfo(root, 0, intArrayOf(APP_INFO_WALK_BUDGET), found, name, ver)
    }

    /** Depth-first, node-budgeted, allocation-light; stops the moment the name and an action label have both been seen. */
    @Suppress("DEPRECATION")
    private fun walkAppInfo(
        n: AccessibilityNodeInfo?, depth: Int, budget: IntArray, found: IntArray, name: String, ver: String
    ): Boolean {
        if (n == null || depth > APP_INFO_WALK_DEPTH || budget[0]-- <= 0) return false
        found[0] = found[0] or keywordBits(n.text, name, ver) or keywordBits(n.contentDescription, name, ver)
        if (found[0] == BITS_ALL) return true
        for (i in 0 until n.childCount) {
            val c = n.getChild(i)
            val done = walkAppInfo(c, depth + 1, budget, found, name, ver)
            c?.recycle()
            if (done) return true
        }
        return false
    }

    private fun keywordBits(cs: CharSequence?, name: String, ver: String): Int {
        if (cs == null || cs.isEmpty() || cs.length > APP_INFO_MAX_TEXT) return 0
        val s = cs.toString()
        var bits = 0
        if (APP_INFO_ACTION_TEXTS.any { s.contains(it, ignoreCase = true) }) bits = bits or BIT_ACTION
        if (isAppNameText(s, name, ver)) bits = bits or BIT_NAME
        return bits
    }

    /** Whole-number match: "1.0.1" is found in "Version 1.0.1" but not in "11.0.1", "1.0.10" or "1.0.1.4". */
    private fun containsVersion(text: String, ver: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(ver, from, ignoreCase = true)
            if (i < 0) return false
            val end = i + ver.length
            val before = if (i >= 1) text[i - 1] else ' '
            val before2 = if (i >= 2) text[i - 2] else ' '
            val after = if (end < text.length) text[end] else ' '
            val after2 = if (end + 1 < text.length) text[end + 1] else ' '
            val startOk = !before.isDigit() && !(before == '.' && before2.isDigit())
            val endOk = !after.isDigit() && !(after == '.' && after2.isDigit())
            if (startOk && endOk) return true
            from = i + 1
        }
    }

    /** Exact name, or name followed by newline/version/size (e.g. "Blocker\n56.56 MB"). Never matches "Ad Blocker". */
    private fun isAppNameText(text: String, name: String, ver: String): Boolean {
        val t = text.trim()
        if (t.endsWith(".apk", ignoreCase = true) || t.contains(".apk", ignoreCase = true)) return false
        if (t.equals(name, ignoreCase = true)) return true
        val firstLine = t.lines().firstOrNull()?.trim().orEmpty()
        if (firstLine.endsWith(".apk", ignoreCase = true) || firstLine.contains(".apk", ignoreCase = true)) return false
        if (firstLine.equals(name, ignoreCase = true)) return true
        if (t.length > name.length && t.startsWith(name, ignoreCase = true)) {
            val sep = t[name.length]
            if (sep == '\n' || sep == '\r' || sep == '·' || sep == '•' || sep == '-' || sep == '(' || sep == ':') {
                return true
            }
            if (sep == ' ') {
                val rem = t.substring(name.length).trim()
                if (rem.isEmpty() || containsVersion(rem, ver) ||
                    rem.matches(Regex("""^(v?\d+(\.\d+)*|[0-9.]+\s*(kb|mb|gb|b)|installed|app|package).*""", RegexOption.IGNORE_CASE))) {
                    return true
                }
            }
        }
        return false
    }

    private fun kickFromAppInfo() {
        val now = System.currentTimeMillis()
        if (now - lastAppInfoKick < 400L) return
        lastAppInfoKick = now
        viewingBlockerSettingsUntil = now + 30_000L             // arms the existing uninstall / clear-data dialog catchers

        runCatching { autoCancelDialog(rootInActiveWindow) }

        // Pause continuous polling & scanner thread during the kick transition to prevent flicker loop
        settingsPollActive = false
        main.removeCallbacks(settingsPollRunnable)
        synchronized(appInfoLock) {
            appInfoHotUntil = 0L
            appInfoFreshUntil = 0L
        }

        // Just like reels/shorts blocking: immediately exit back to the previous page
        performGlobalAction(GLOBAL_ACTION_BACK)

        // Safety check: if App Info is still displayed after a moment (e.g. if the first BACK only closed a dialog/sheet),
        // send a second BACK to ensure the user returns to the previous page
        main.postDelayed({
            val currentRoot = runCatching { rootInActiveWindow }.getOrNull()
            if (currentRoot != null) {
                val currentPkg = currentRoot.packageName?.toString().orEmpty()
                if (isActualSettingsPkg(currentPkg)) {
                    val sb = StringBuilder()
                    collectText(currentRoot, sb, 0, intArrayOf(100))
                    val curText = sb.toString().lowercase()
                    if ("blocker" in curText || packageName.lowercase() in curText) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            }

            // Resume settings poll once settled
            main.postDelayed({
                if (!settingsPollActive) startSettingsPoll()
            }, 500L)
        }, 120L)

        Log.i("BlockerGuard", "App Info screen blocked (pkg=$appInfoCandidatePkg)")
        BlockerStore.incr(this, "tamper")
        showOverlay(
            "\uD83D\uDD10 Tamper Protection",
            "Blocker's app info page is locked while Protection Mode is active.\n\n\u201cAnd fulfill your covenants. Indeed, covenants will be questioned.\u201d \u2014 Surah Al-Isra (17:34)",
            5000,
            "Fear Allah and remain steadfast",
            "Understood"
        )
    }

    // ---------- USB & Wireless debugging lock ----------
    //
    // While Protection Mode + the uninstall shield are on, USB and Wireless debugging must stay off. Two levels:
    //
    //  1. Works out of the box, no computer: Developer options screens are blocked (see the settings guards), and
    //     if USB or Wireless debugging is ever found switched on, Blocker opens Developer options itself, finds the
    //     "USB debugging" or "Wireless debugging" row, taps it off, and returns Home (a few seconds; at most 3 attempts per 10 minutes).
    //  2. Optional instant lock: one adb command, run once from any computer while USB debugging is on:
    //         adb shell pm grant com.blocker android.permission.WRITE_SECURE_SETTINGS
    //     After that USB and Wireless debugging are switched back off the instant anything turns them on.
    //
    // Both stand down while the shield is off or its confirmation window is open, so a confirmed shield change
    // lets you use adb again.

    private fun hasSecureSettingsPermission(): Boolean =
        checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun usbAdbIsOn(): Boolean =
        runCatching { Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)

    private fun wifiAdbIsOn(): Boolean =
        runCatching { Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1 }.getOrDefault(false)

    private fun adbIsOn(): Boolean = usbAdbIsOn() || wifiAdbIsOn()

    private fun enforceAdbOffThrottled() {
        val now = SystemClock.uptimeMillis()
        if (now - lastAdbEnforce < ADB_ENFORCE_GAP_MS) return
        lastAdbEnforce = now
        enforceAdbOff(notify = true)
    }

    private fun enforceAdbOff(notify: Boolean) {
        if (adbFixActive() && !adbIsOn()) finishAdbUiFix()     // the tap worked
        if (!shieldEngaged()) return
        if (!hasSecureSettingsPermission()) {
            if (!adbPermWarned) {
                adbPermWarned = true
                Log.i("BlockerGuard", "USB/Wireless debugging lock: using the Settings fallback (optional instant lock: adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS)")
            }
            if (adbIsOn()) startAdbUiFix()
            return
        }
        var flipped = false
        for (key in ADB_SETTING_KEYS) {
            val on = runCatching { Settings.Global.getInt(contentResolver, key, 0) == 1 }.getOrDefault(false)
            if (on && runCatching { Settings.Global.putInt(contentResolver, key, 0) }.getOrDefault(false)) flipped = true
        }
        if (!flipped) return
        Log.i("BlockerGuard", "USB/Wireless debugging switched back off")
        val now = SystemClock.uptimeMillis()
        if (notify && now - lastAdbNotice > 2_000L) {
            lastAdbNotice = now
            BlockerStore.incr(this, "tamper")
            showOverlay("🔐 Tamper Protection", "USB & Wireless debugging are locked while Protection Mode is active.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)", 5000, "Fear Allah and remain steadfast", "Understood")
        }
    }

    // ----- no-computer fallback: switch the row off through the Developer options screen -----

    private fun adbFixActive(): Boolean = SystemClock.uptimeMillis() < adbFixUntil

    private fun startAdbUiFix() {
        if (adbFixActive()) return
        val now = SystemClock.uptimeMillis()
        adbFixStarts.removeAll { now - it > 600_000L }
        if (adbFixStarts.size >= ADB_FIX_MAX_PER_10MIN) return
        adbFixStarts.add(now)
        adbFixUntil = now + ADB_FIX_WINDOW_MS      // set BEFORE the screen opens so our own guards ignore it
        adbFixScrolls = 0
        lastAdbFixClick = 0L
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) {
            adbFixUntil = 0L
            return
        }
        Log.i("BlockerGuard", "USB/Wireless debugging found on: switching it off through Developer options")
        main.removeCallbacks(adbFixTick)
        main.postDelayed(adbFixTick, 500L)
    }

    private fun finishAdbUiFix() {
        val wasActive = adbFixUntil != 0L
        adbFixUntil = 0L
        main.removeCallbacks(adbFixTick)
        if (!wasActive) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (!adbIsOn()) {
            BlockerStore.incr(this, "tamper")
            showOverlay("🔐 Tamper Protection", "USB & Wireless debugging were switched off. They stay locked while Protection Mode is active.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)", 5000, "Fear Allah and remain steadfast", "Understood")
        }
    }

    /** One tick: find the "USB debugging" or "Wireless debugging" row (scrolling if needed) and tap it. Success is detected by the setting observer. */
    private fun stepAdbUiFix() {
        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString() ?: return
        if (rootPkg == packageName || !(isSettingsPkg(rootPkg) || isInstallerPkg(rootPkg))) return   // still opening

        val targetLabels = mutableListOf<String>()
        if (usbAdbIsOn()) {
            targetLabels.add(ADB_ROW_LABEL)
        }
        if (wifiAdbIsOn()) {
            targetLabels.add(ADB_WIFI_ROW_LABEL)
            targetLabels.add("Use wireless debugging")
        }
        if (targetLabels.isEmpty()) {
            finishAdbUiFix()
            return
        }

        var matchedRow: AccessibilityNodeInfo? = null
        for (label in targetLabels) {
            val found = root.findAccessibilityNodeInfosByText(label)
                ?.firstOrNull { it.text?.toString()?.trim().equals(label, ignoreCase = true) }
            if (found != null) {
                matchedRow = found
                break
            }
        }

        if (matchedRow == null) {
            if (adbFixScrolls < ADB_FIX_MAX_SCROLLS) {
                val scroller = findScrollable(root, 0, intArrayOf(150))
                if (scroller != null && scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) adbFixScrolls++
            }
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastAdbFixClick < ADB_FIX_CLICK_GAP_MS) return    // give the switch time to flip before judging

        // First attempt: try finding a Switch widget in the same row container (up to 3 levels up)
        var switchNode: AccessibilityNodeInfo? = null
        var container: AccessibilityNodeInfo? = matchedRow
        var depth = 0
        while (container != null && depth++ < 3) {
            switchNode = findSwitchInNode(container)
            if (switchNode != null) break
            container = container.parent
        }

        if (switchNode != null && switchNode.isClickable) {
            lastAdbFixClick = now
            switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        // Fallback: click the clickable parent of the row or the switch's parent
        var target: AccessibilityNodeInfo? = switchNode ?: matchedRow
        var hops = 0
        while (target != null && !target.isClickable && hops++ < 6) target = target.parent
        if (target == null) return
        lastAdbFixClick = now
        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findSwitchInNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val cls = node.className?.toString() ?: ""
        if (node.isCheckable || cls.contains("Switch", ignoreCase = true) || cls.contains("CompoundButton", ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val s = findSwitchInNode(node.getChild(i))
            if (s != null) return s
        }
        return null
    }

    private fun findScrollable(n: AccessibilityNodeInfo?, depth: Int, budget: IntArray): AccessibilityNodeInfo? {
        if (n == null || depth > 20 || budget[0]-- <= 0) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val found = findScrollable(n.getChild(i), depth + 1, budget)
            if (found != null) return found
        }
        return null
    }

    private fun lockOutOfSettings(message: String = "Device Admin & app settings for Blocker are locked while Protection Mode is active.") {
        val now = System.currentTimeMillis()
        if (now - lastHit < 1000L) return
        lastHit = now
        val root = runCatching { rootInActiveWindow }.getOrNull()
        val rootPkg = root?.packageName?.toString().orEmpty()
        if (rootPkg.isNotEmpty() && isActualSettingsPkg(rootPkg)) {
            kickFromAppInfo()
            return
        }
        runCatching { autoCancelDialog(root) }
        kickToHome()
        BlockerStore.incr(this, "tamper")
        showOverlay("🔐 Tamper Protection", "$message\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)", 5000, "Fear Allah and remain steadfast", "Understood")
    }

    // ---------- Granular focus interception (Reels / Shorts / Search) ----------

    private fun checkGranularInterception(pkg: String, ev: AccessibilityEvent): Boolean {
        if (!BlockerStore.active(this) && !BlockerStore.focusActive(this)) return false
        val now = System.currentTimeMillis()
        if (now - (lastGranularHit[pkg] ?: 0L) < 700L) return true

        val root = rootInActiveWindow ?: ev.source ?: return false
        if (root.packageName?.toString() != pkg && !root.packageName?.toString().orEmpty().contains("facebook")) return false

        val evCls = ev.className?.toString().orEmpty().lowercase()
        val rootCls = root.className?.toString().orEmpty().lowercase()

        when (pkg) {
            "com.google.android.youtube" -> {
                if (!blockYtShorts) return false
                val isShortsTab = isTabSelectedById(root, "com.google.android.youtube:id/pivot_shorts") ||
                    scanSelectedTab(root, listOf("shorts"))
                val isShortsPlayer = hasAnyNodeId(root, YT_SHORTS_PLAYER_IDS) ||
                    evCls.contains("reelwatch") || rootCls.contains("reelwatch")
                if (isShortsTab || isShortsPlayer) {
                    exitSubFeature(pkg, "YouTube Shorts")
                    return true
                }
            }
            "com.instagram.android" -> {
                if (blockInstaReels) {
                    val isReelsTab = isTabSelectedById(root, "com.instagram.android:id/clips_tab") ||
                        scanSelectedTab(root, listOf("reels"))
                    val isReelsPlayer = hasAnyNodeId(root, INSTA_REELS_PLAYER_IDS) ||
                        evCls.contains("clipsviewer") || rootCls.contains("clipsviewer")
                    if (isReelsTab || isReelsPlayer) {
                        exitSubFeature(pkg, "Instagram Reels")
                        return true
                    }
                }
                if (blockInstaSearch) {
                    // Instagram search tab button exists in the bottom bar on all screens; ONLY intercept if actually selected!
                    val isSearchTab = isTabSelectedById(root, "com.instagram.android:id/search_tab") ||
                        scanSelectedTab(root, listOf("search and explore", "search & explore", "explore tab"))
                    val isSearchInput = isSearchInputFocused(root, INSTA_SEARCH_INPUT_IDS)
                    if (isSearchTab || isSearchInput) {
                        exitSubFeature(pkg, "Instagram Search")
                        return true
                    }
                }
            }
            "com.facebook.katana", "com.facebook.lite" -> {
                if (!blockFbReels) return false
                if (isFacebookVideoOrReels(root, ev)) {
                    exitSubFeature(pkg, "Facebook Video & Reels")
                    return true
                }
            }
        }
        return false
    }

    private fun isTabSelectedById(root: AccessibilityNodeInfo, tabId: String): Boolean {
        val list = root.findAccessibilityNodeInfosByViewId(tabId)
        if (list.isNullOrEmpty()) return false
        var selected = false
        for (node in list) {
            if (node.isSelected) selected = true
            node.recycle()
        }
        return selected
    }

    private fun isSearchInputFocused(root: AccessibilityNodeInfo, ids: List<String>): Boolean {
        for (id in ids) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) {
                var active = false
                for (n in list) {
                    if (n.isFocused || n.text?.isNotEmpty() == true) active = true
                    n.recycle()
                }
                if (active) return true
            }
        }
        return false
    }

    private fun exitSubFeature(pkg: String, featureName: String) {
        val now = System.currentTimeMillis()
        lastGranularHit[pkg] = now
        quietUntil[pkg] = now + 1500L
        performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({
            if (currentFgPkg == pkg) {
                val r = runCatching { rootInActiveWindow ?: null }.getOrNull()
                if (r != null) {
                    val stillInSubFeature = when (pkg) {
                        "com.facebook.katana", "com.facebook.lite" -> isFacebookVideoOrReels(r, null)
                        "com.google.android.youtube" -> hasAnyNodeId(r, YT_SHORTS_PLAYER_IDS) || isTabSelectedById(r, "com.google.android.youtube:id/pivot_shorts")
                        "com.instagram.android" -> hasAnyNodeId(r, INSTA_REELS_PLAYER_IDS) || isTabSelectedById(r, "com.instagram.android:id/clips_tab")
                        else -> false
                    }
                    if (stillInSubFeature) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            }
        }, 250L)
    }

    private fun isFacebookVideoOrReels(root: AccessibilityNodeInfo, ev: AccessibilityEvent?): Boolean {
        // 1. Check Reels & Video tabs in navigation bar (only when actually selected)
        val isVideoOrReelsTab = isTabSelectedById(root, "com.facebook.katana:id/reels_tab") ||
            isTabSelectedById(root, "com.facebook.katana:id/tab_reels") ||
            isTabSelectedById(root, "com.facebook.katana:id/fb_shorts_tab") ||
            isTabSelectedById(root, "com.facebook.katana:id/video_tab") ||
            isTabSelectedById(root, "com.facebook.katana:id/tab_video") ||
            isTabSelectedById(root, "com.facebook.katana:id/watch_tab") ||
            isTabSelectedById(root, "com.facebook.katana:id/tab_watch") ||
            isTabSelectedById(root, "com.facebook.katana:id/video_home_tab") ||
            scanSelectedTab(root, listOf(
                "reels, tab", "reels tab", "facebook reels, tab", "facebook reels tab",
                "watch, tab", "watch tab", "video, tab", "video tab", "videos, tab"
            ))
        if (isVideoOrReelsTab) return true

        // 2. Check if user is currently on the main Newsfeed / Home tab
        val isHomeTabSelected = isTabSelectedById(root, "com.facebook.katana:id/feed_tab") ||
            isTabSelectedById(root, "com.facebook.katana:id/tab_feed") ||
            isTabSelectedById(root, "com.facebook.katana:id/newsfeed_tab") ||
            isTabSelectedById(root, "com.facebook.katana:id/tab_newsfeed") ||
            isTabSelectedById(root, "com.facebook.katana:id/home_tab") ||
            isTabSelectedById(root, "com.facebook.katana:id/tab_home") ||
            scanSelectedTab(root, listOf("home, tab", "home tab", "news feed, tab", "news feed tab", "feed, tab", "feed tab"))

        val hasNewsfeedMarkers = hasAnyNodeId(root, listOf(
            "com.facebook.katana:id/feed_composer",
            "com.facebook.katana:id/feed_recycler_view",
            "com.facebook.katana:id/newsfeed_fragment",
            "com.facebook.katana:id/primary_feed",
            "com.facebook.lite:id/feed_list"
        ))

        // 3. Check for dedicated full-screen Reels viewer overlay / fragment
        val isDedicatedReelsViewer = hasAnyNodeId(root, FB_REELS_PLAYER_IDS)

        val evCls = ev?.className?.toString().orEmpty().lowercase()
        val rootCls = root.className?.toString().orEmpty().lowercase()
        val isReelsClass = evCls.contains("fbshorts") || rootCls.contains("fbshorts") ||
            evCls.contains("reelsviewer") || rootCls.contains("reelsviewer")

        // CRITICAL: If the user is on the main newsfeed/home screen and NOT in a dedicated full-screen reels viewer,
        // NEVER exit! Let the user browse their feed normally without interrupting for in-line posts or videos.
        if ((isHomeTabSelected || hasNewsfeedMarkers) && !isDedicatedReelsViewer && !isReelsClass) {
            return false
        }

        if (isDedicatedReelsViewer || isReelsClass) return true

        // 4. Fallback text inspection: only check if outside the main newsfeed
        if (!isHomeTabSelected && !hasNewsfeedMarkers) {
            val sb = StringBuilder()
            collectText(root, sb, 0, intArrayOf(250))
            val t = sb.toString().lowercase()

            val hasReelsOverlay = "remix this reel" in t || "use audio" in t ||
                "share reel" in t || "reels audio" in t ||
                (t.contains("reels") && (t.contains("remix") || t.contains("original audio")))
            val hasReelsChaining = "swipe up for more reels" in t || "swipe up for next reel" in t

            if (hasReelsOverlay || hasReelsChaining) return true
        }

        return false
    }

    private fun hasAnyNodeId(root: AccessibilityNodeInfo, ids: List<String>): Boolean {
        for (id in ids) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) {
                list.forEach { it.recycle() }
                return true
            }
        }
        return false
    }

    private fun isNodeOrTabSelected(root: AccessibilityNodeInfo, descs: List<String>): Boolean {
        return scanSelectedTab(root, descs, 0, intArrayOf(80))
    }

    private fun scanSelectedTab(n: AccessibilityNodeInfo?, descs: List<String>, depth: Int = 0, budget: IntArray = intArrayOf(80)): Boolean {
        if (n == null || depth > 15 || budget[0]-- <= 0) return false
        val cd = n.contentDescription?.toString()?.lowercase()
        val text = n.text?.toString()?.lowercase()
        if (n.isSelected || n.isFocused) {
            if (cd != null && descs.any { cd.contains(it) }) return true
            if (text != null && descs.any { text == it }) return true
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i)
            val found = scanSelectedTab(c, descs, depth + 1, budget)
            c?.recycle()
            if (found) return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun collectText(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, budget: IntArray) {
        if (n == null || depth > 30 || budget[0]-- <= 0 || sb.length > MAX_SCAN_CHARS) return
        if (!n.isPassword) {
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i)
            collectText(c, sb, depth + 1, budget)
            c?.recycle()
        }
    }

    private fun hit(kind: String, heading: String, msg: String, subheading: String? = null) {
        val now = System.currentTimeMillis()
        if (now - lastHit < 700) return
        lastHit = now
        BlockerStore.incr(this, kind)
        performGlobalAction(GLOBAL_ACTION_HOME)
        showOverlay(heading, msg, 5000, subheading, "Astaghfirullah")
    }

    // ---------- Popup ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private var scrim: View? = null

    private fun showOverlay(
        heading: String,
        body: String,
        ms: Long = 5000L,
        subheading: String? = null,
        buttonText: String = "Astaghfirullah"
    ) {
        val displayMs = ms.coerceAtLeast(5000L)
        main.post {
            if (overlay != null) return@post
            val svc = this@BlockerAccessibilityService
            val accent = 0xFFA78BFA.toInt()
            val accentDeep = 0xFF7C3AED.toInt()

            // dim scrim behind the card so it reads as a proper modal, matching the app's own dialogs
            val dim = View(svc).apply { setBackgroundColor(0xB3070512.toInt()) }
            val dimLp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(dim, dimLp); scrim = dim }

            val isFearAllah = heading == "Fear Allah"

            val badge = LinearLayout(svc).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (isFearAllah) 0x33DC2626.toInt() else 0x33A78BFA)
                    cornerRadius = dp(22).toFloat()
                    if (isFearAllah) setStroke(dp(1), 0x88EF4444.toInt())
                }
            }
            badge.addView(TextView(svc).apply {
                text = if (isFearAllah) "\uD83D\uDEE1\uFE0F" else "\uD83D\uDD12"
                textSize = 20f
                gravity = Gravity.CENTER
            })

            val headingRow = LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            headingRow.addView(badge, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) })
            headingRow.addView(TextView(svc).apply {
                text = heading
                setTextColor(if (isFearAllah) 0xFFFFFFFF.toInt() else 0xFFF6F3FF.toInt())
                textSize = if (isFearAllah) 22f else 19f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.02f
            })

            val card = LinearLayout(svc).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(22), dp(22), dp(20))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF221A3D.toInt(), 0xFF140D26.toInt())
                ).apply {
                    cornerRadius = dp(26).toFloat()
                    setStroke(dp(2), if (isFearAllah) 0x99C084FC.toInt() else 0x66C7AFFF.toInt())
                }
                elevation = dp(18).toFloat()
            }
            card.addView(headingRow)

            if (!subheading.isNullOrBlank()) {
                card.addView(TextView(svc).apply {
                    text = subheading
                    setTextColor(0xFFE9D5FF.toInt()) // Vibrant soft purple highlight
                    textSize = 15.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(0, dp(10), 0, dp(2))
                })
            }

            card.addView(TextView(svc).apply {
                text = body
                setTextColor(0xFFD1C8EC.toInt())
                textSize = 14.5f
                setPadding(0, if (!subheading.isNullOrBlank()) dp(6) else dp(12), 0, dp(18))
                setLineSpacing(dp(3).toFloat(), 1.05f)
            })

            val progressTrack = View(svc).apply { setBackgroundColor(0x26FFFFFF) }
            val progressFill = View(svc).apply {
                setBackgroundColor(accent)
                pivotX = 0f
            }
            val trackWrap = android.widget.FrameLayout(svc)
            trackWrap.addView(progressTrack, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))
            trackWrap.addView(progressFill, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))
            card.addView(trackWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply {
                bottomMargin = dp(16)
            })

            card.addView(Button(svc).apply {
                text = buttonText
                setAllCaps(false)
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(accentDeep, accent)
                ).apply { cornerRadius = dp(15).toFloat() }
                stateListAnimator = null
                setOnClickListener { removeOverlay() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

            val lp = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            card.alpha = 0f
            card.translationY = dp(16).toFloat()
            runCatching {
                (getSystemService(WINDOW_SERVICE) as WindowManager).addView(card, lp)
                overlay = card
                card.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                progressFill.animate().scaleX(0f).setDuration(displayMs).setInterpolator(LinearInterpolator()).start()
            }
            main.removeCallbacks(hideRunnable)
            main.postDelayed(hideRunnable, displayMs)
        }
    }

    private fun removeOverlay() {
        main.removeCallbacks(hideRunnable)
        overlay?.let { v ->
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        }
        overlay = null
        scrim?.let { v ->
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        }
        scrim = null
    }
}