package com.blocker

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityManager
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
import android.widget.FrameLayout
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

        private val EMERGENCY_CALL_PKGS = setOf(
            "com.android.server.telecom",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.android.phone",
            "com.samsung.android.dialer",
            "com.android.emergency"
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
            "com.oplus.securitypermission",
            "com.xui.xhide",
            "com.transsion.freezer",
            "com.vivo.permissionmanager",
            "com.vivo.safecenter",
            "com.iqoo.secure",
            "com.huawei.systemmanager",
            "com.hihonor.systemmanager"
        )
        private val HIDE_FREEZER_TERMS = listOf(
            "hide apps on home and apps screens",
            "hide apps", "hide app", "hidden apps", "hidden app",
            "hide app icons", "hidden app icons", "hidden icons", "hide icons",
            "hide applications", "hidden applications", "unhide apps", "unhide app",
            "app hiding", "app hide", "view hidden apps", "hidden space",
            "app freezer", "freezer", "freeze apps", "freeze app", "frozen apps",
            "add to freezer", "send to freezer", "to freezer",
            "xhide", "x-hide", "private space"
        )
        private val HIDE_FREEZER_CLASS_HINTS = listOf(
            "freezer", "xhide", "hideapp", "hiddenapp", "apphide", "apphidden",
            "hide_app", "hidden_app", "softhide", "freeze", "privatespace",
            "hiddenicon", "hideicon", "secretapp"
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
        private val FB_VIDEO_REELS_PLAYER_IDS = listOf(
            // Dedicated full-screen Facebook Katana Reels viewers
            "com.facebook.katana:id/fb_shorts_viewer_container",
            "com.facebook.katana:id/fb_shorts_viewer_view_pager",
            "com.facebook.katana:id/fb_shorts_unified_viewer",
            "com.facebook.katana:id/fb_shorts_viewer_fragment",
            "com.facebook.katana:id/reels_viewer_root",
            "com.facebook.katana:id/watch_and_go_reels_container",
            "com.facebook.katana:id/fb_shorts_player_fragment",
            // Channel Feed & Warion (scrollable video player launched from newsfeed)
            "com.facebook.katana:id/warion_root_container",
            "com.facebook.katana:id/warion_container",
            "com.facebook.katana:id/warion_video_view",
            "com.facebook.katana:id/channel_feed",
            "com.facebook.katana:id/channel_feed_fragment",
            "com.facebook.katana:id/channel_feed_view",
            "com.facebook.katana:id/channel_view",
            "com.facebook.katana:id/fullscreen_video",
            "com.facebook.katana:id/fullscreen_video_player",
            "com.facebook.katana:id/video_fullscreen_player",
            "com.facebook.katana:id/unified_video_viewer",
            "com.facebook.katana:id/playback_control_overlay",
            "com.facebook.katana:id/video_seek_bar",
            "com.facebook.katana:id/video_time_display",
            "com.facebook.katana:id/video_controls_container",
            // Dedicated Facebook Lite Reels & Video viewers
            "com.facebook.lite:id/reels_screen",
            "com.facebook.lite:id/reels_player",
            "com.facebook.lite:id/video_player_reels",
            "com.facebook.lite:id/watch_screen",
            "com.facebook.lite:id/video_player"
        )
        private val FB_NEWSFEED_IDS = listOf(
            "com.facebook.katana:id/feed_composer",
            "com.facebook.katana:id/feed_recycler_view",
            "com.facebook.katana:id/newsfeed_fragment",
            "com.facebook.katana:id/primary_feed",
            "com.facebook.katana:id/feed_list",
            "com.facebook.katana:id/root_search_bar",
            "com.facebook.katana:id/search_button",
            "com.facebook.katana:id/messages_jewel",
            "com.facebook.katana:id/notifications_jewel",
            "com.facebook.katana:id/bookmarks_tab",
            "com.facebook.katana:id/tab_bar",
            "com.facebook.lite:id/feed_list",
            "com.facebook.lite:id/composer"
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
        private val DATE_TIME_CLASSES = setOf(
            "com.android.settings.datetime.DateTimeSettings",
            "com.android.settings.DateTimeSettings",
            "com.android.settings.DateTimeSettingsSetupWizard",
            "com.android.settings.ZonePicker",
            "com.android.settings.Settings\$DateTimeSettingsActivity",
            "com.android.settings.Settings\$DateTimeSettingsSetupWizardActivity",
            "com.android.settings.Settings\$ZonePickerActivity",
            "com.samsung.android.settings.datetime.DateTimeSettings",
            "com.samsung.android.settings.DateTimeSettings",
            "com.android.settings.MiuiDateTimeSettings",
            "com.oplus.settings.feature.datetime.DateTimeSettings",
            "com.coloros.settings.feature.datetime.DateTimeSettings"
        )
        private val DATE_TIME_SCREEN_TITLES = setOf(
            "date & time", "date and time", "date & time settings",
            "automatic date & time", "automatic date and time", "change date", "change time"
        )
        private const val SCAN_GAP_MS = 1000L
        private const val MAX_SCAN_CHARS = 20_000
        private const val REDIRECT_URL = "https://www.google.com"

        // ── Blocker App Info guard ──
        // Static OS button labels, matched case-insensitively. Add another UI language here if you ever need one.
        // "Deactivate" covers the "Deactivate & uninstall" device-admin page that Uninstall leads to.
        private val APP_INFO_ACTION_TEXTS = listOf(
            "Uninstall", "Force stop", "Deactivate", "Disable", "Storage & cache", "Storage", "Clear storage", "Clear data", "Manage space"
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
    @Volatile private var lastHideKick = 0L
    @Volatile private var lastBlockerLongPressUp = 0L
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
    @Volatile private var currentSettingsScreenTitle = "" // main thread only: title of current settings screen
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
            if (isLegitimatePermissionScreen(node, nTxt)) {
                continue
            }
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
            val guardActive = shouldBlockDateTime() || (
                BlockerStore.active(this@BlockerAccessibilityService) &&
                BlockerStore.shield(this@BlockerAccessibilityService) &&
                !BlockerStore.guardOpen(this@BlockerAccessibilityService, "shield")
            )

            if (!guardActive) {
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
            try {
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
                        main.postDelayed(this, 250)
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
                main.postDelayed(this, 250)
            } finally {
                runCatching { root?.recycle() }
            }
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
        runCatching { autoCancelDialog(rootInActiveWindow) }
        kickToHome()
        if (now - lastPollKick < 1000L) return
        lastPollKick = now
        BlockerStore.incr(this, "tamper")
        showOverlay(getTamperHeading(), formatTamperCovenantBody(message), 5000, getTamperSubheading(), getUnderstoodButton())
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
        collectText(root, sb, 0, intArrayOf(2500))
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

        // Date & Time settings guard: strictly blocks system clock alteration
        if (shouldBlockDateTime() && isDateTimeSettingsScreen(root, t)) {
            pollKick("Date & Time settings are locked while protection is active to prevent clock tampering.")
            return
        }

        // Scheduled Power Off guard: strictly blocks scheduled shutdown configuration
        if (shouldBlockScheduledPower() && isScheduledPowerSettingsScreen(root, t, "", rootPkg)) {
            pollKick("Scheduled power off is locked while protection is active.")
            return
        }

        // Blocker-specific: check if screen is Blocker's App Info, Storage, or Settings
        val isOurApp = "blocker" in t || packageName.lowercase() in t

        // Blocker-specific: kick the instant ANY settings screen mentions our app with destructive actions,
        // EXCEPT legitimate permission grant screens (DND access, unknown apps, usage access, battery optimization).
        if (isOurApp) {
            if (!isLegitimatePermissionScreen(root, t) && !isInstallOrUpdateDialog(root = root)) {
                val hasDestructiveAction = "force stop" in t || "uninstall" in t || "disable" in t ||
                    "clear storage" in t || "clear data" in t || "manage space" in t || "storage & cache" in t ||
                    "delete app data" in t || "clear app data" in t
                if (hasDestructiveAction) {
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

    private val appBlockWatchdog = object : Runnable {
        override fun run() {
            if (BlockerStore.active(this@BlockerAccessibilityService)) {
                val root = runCatching { rootInActiveWindow }.getOrNull()
                val rootPkg = root?.packageName?.toString()
                val activePkg = if (!rootPkg.isNullOrEmpty()) rootPkg else currentFgPkg
                if (activePkg != null && activePkg in apps) {
                    hit(
                        "apps",
                        getFearAllahHeading(),
                        formatBlockedAppBody(activePkg),
                        getFearAllahSubheading(),
                        activePkg,
                        getAstaghfirullahButton()
                    )
                }
            }
            main.postDelayed(this, 350L)
        }
    }

    @Volatile private var blockFbReels = false
    @Volatile private var blockInstaReels = false
    @Volatile private var blockInstaSearch = false
    @Volatile private var blockYtShorts = false
    private val lastGranularHit = HashMap<String, Long>()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key != null && key in RELOAD_KEYS) reloadKey(key)
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
                    if (BlockerStore.studyActive(this@BlockerAccessibilityService)) {
                        main.postDelayed({ bringBlockerToForeground() }, 150)
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        BlockerForegroundService.start(this)
        DeviceOwnerHelper.enforceDisallowSafeBoot(this)
        loadAppIdentity()
        BlockerStore.seedDefaultApps(this)   // default app blocks work even before Blocker's own UI is ever opened
        neverBlock = neverBlock + launcherPackages() + cameraPackages()
        reload()
        runCatching {
            val info = serviceInfo
            if (info != null) {
                info.notificationTimeout = 0L
                info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                serviceInfo = info
            }
        }
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
        val binFile = java.io.File(filesDir, "blocklist.bin")
        if (!binFile.exists()) {
            try {
                assets.open("blocklist.bin").use { input ->
                    binFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Throwable) {}
        }
        if (binFile.exists()) {
            NativeBlockerBridge.load(binFile)
        } else {
            BlockerStore.syncBinaryBlocklist(this)
        }
        initPreInflatedOverlay()
        main.post(appLimitWatchdog)
        main.post(appBlockWatchdog)
    }

    override fun onDestroy() {
        main.removeCallbacks(appLimitWatchdog)
        main.removeCallbacks(appBlockWatchdog)
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
        preInflatedOverlay?.let { v ->
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        }
        preInflatedOverlay = null
        NativeBlockerBridge.unload()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private fun launcherPackages(): Set<String> = runCatching {
        packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun isLauncherPkg(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        if (pkg in launcherPackages()) return true
        val lower = pkg.lowercase()
        return lower.contains("launcher") || lower.contains("home") ||
            lower == "com.sec.android.app.launcher" ||
            lower == "com.miui.home" || lower == "com.mi.android.globallauncher" ||
            lower == "com.oppo.launcher" || lower == "com.oplus.launcher" ||
            lower == "com.oneplus.launcher" || lower == "net.oneplus.launcher" ||
            lower == "com.bbk.launcher2" || lower == "com.bbk.launcher" ||
            lower == "com.vivo.launcher" || lower == "com.vivo.upslide" ||
            lower == "com.transsion.hilauncher" || lower == "com.transsion.xoslauncher" ||
            lower == "com.transsion.desktop" ||
            lower == "com.huawei.android.launcher" || lower == "com.hihonor.android.launcher" ||
            lower == "com.google.android.apps.nexuslauncher" ||
            lower == "com.motorola.launcher3" || lower == "com.nothing.launcher" ||
            lower == "com.teslacoilsw.launcher" || lower == "ch.deletescape.lawnchair" ||
            lower == "app.lawnchair" || lower == "com.microsoft.launcher" ||
            lower == "bitpit.launcher" || lower.startsWith("ginlemon.flower")
    }

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
        val s3 = pm.queryIntentActivities(Intent(android.provider.Settings.ACTION_DATE_SETTINGS), 0)
            .mapNotNull { it.activityInfo?.packageName }
        (s1 + s2 + s3).toSet()
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

    private fun isLegitimatePermissionTitle(title: String): Boolean {
        if (title.isEmpty()) return false
        val t = title.lowercase()
        return "do not disturb" in t || "notification policy" in t || "zen" in t || "dnd" in t ||
            "device & app notifications" in t || "notification access" in t ||
            "battery" in t || "unrestricted" in t || "high power" in t || "background" in t ||
            "unknown apps" in t || "install unknown" in t || "allow from this source" in t ||
            "usage access" in t || "special app access" in t || "special access" in t || "special permissions" in t ||
            "display over other apps" in t || "appear on top" in t
    }

    private fun isLegitimatePermissionScreen(text: String): Boolean {
        return isLegitimatePermissionScreen(null, text)
    }

    private fun isLegitimatePermissionScreen(root: AccessibilityNodeInfo?, text: String): Boolean {
        val t = text.lowercase()
        // If there are explicit dangerous actions on screen, it is NEVER a safe permission screen
        val hasDestructiveAction = "force stop" in t || "clear storage" in t || "clear data" in t ||
            "delete app data" in t || "manage space" in t || "clear app data" in t ||
            "do you want to uninstall" in t || "uninstall this app" in t
        if (hasDestructiveAction) {
            return false
        }
        // Legitimate special app access / permission screens:
        // 1. Do Not Disturb / Notification Policy Access:
        val isDnd = "do not disturb" in t || "notification policy" in t || "zen" in t || "dnd" in t ||
            "device & app notifications" in t || "notification access" in t
        // 2. Battery Optimization / Background Service:
        val isBattery = "battery" in t || "battery optimization" in t || "optimize battery" in t ||
            "unrestricted" in t || "high power" in t || "always run in background" in t ||
            "background usage" in t || "background activity" in t || "run in background" in t ||
            "ignore battery" in t || "battery saver" in t || "power consumption" in t ||
            "background execution" in t || "optimize battery usage" in t || "let app always run" in t
        // 3. Install unknown apps:
        val isUnknownApps = "install unknown" in t || "unknown apps" in t || "allow from this source" in t || "install other apps" in t
        // 4. Usage access:
        val isUsage = "usage access" in t || "usage data access" in t || "apps with usage access" in t
        // 5. Special app access list / Overlay / Accessibility:
        val isSpecialAccess = "special app access" in t || "special access" in t || "special permissions" in t ||
            "display over other apps" in t || "appear on top" in t || "draw over other apps" in t ||
            "accessibility" in t

        return isDnd || isBattery || isUnknownApps || isUsage || isSpecialAccess
    }

    private fun shouldBlockDateTime(): Boolean {
        if (BlockerStore.studyActive(this)) return true
        if (BlockerStore.focusActive(this)) return true
        if (shieldEngaged()) return true
        return false
    }

    private fun isDateTimeSettingsScreen(root: AccessibilityNodeInfo?, text: String): Boolean {
        val t = text.lowercase()

        // If the screen contains parent menu items, it is a settings list (e.g. System, General Management, Main Settings), NOT the Date & Time subpage!
        val isParentMenu = "language" in t || "gesture" in t || "backup" in t || "reset" in t ||
            "keyboard" in t || "system update" in t || "about phone" in t || "battery" in t ||
            "display" in t || "sound" in t || "network & internet" in t || "connected devices" in t ||
            "wallpaper" in t || "apps & notifications" in t || "accessibility" in t
        if (isParentMenu) return false

        // 1. Standard automatic network date/time toggles across all Android versions/OEMs (only on actual Date & Time subscreen)
        if ("set time automatically" in t || "automatic date & time" in t || "automatic date and time" in t ||
            "use network-provided time" in t || "use network-provided time zone" in t || "automatic time zone" in t ||
            "set time zone automatically" in t || "set automatically" in t) {
            return true
        }

        // 2. Date & time setting actions/controls
        if (("set date" in t || "change date" in t) && ("set time" in t || "change time" in t)) {
            return true
        }
        if (("time zone" in t || "select time zone" in t) && ("set date" in t || "set time" in t || "automatic" in t || "24-hour" in t || "24 hour" in t)) {
            return true
        }
        if (("24-hour" in t || "24 hour" in t) && ("date" in t || "time" in t) &&
            ("automatic" in t || "network" in t || "format" in t || "clock" in t || "zone" in t)) {
            return true
        }

        // 3. Exact title on a subscreen without other setting sections
        val isDateTimeTitle = t.startsWith("date & time") || t.startsWith("date and time") ||
            t == "date & time" || t == "date and time" ||
            "date & time settings" in t || "date and time settings" in t
        if (isDateTimeTitle && ("time" in t || "date" in t || "clock" in t || "zone" in t)) {
            if ("set" in t || "automatic" in t || "network" in t || "zone" in t || "format" in t || "hour" in t || "am" in t || "pm" in t) {
                return true
            }
        }

        // 4. Fast native node lookup on the view tree for actual controls (never match generic list titles)
        if (root != null) {
            val terms = listOf(
                "Set time automatically", "Automatic date & time", "Automatic date and time",
                "Use network-provided time", "Use network-provided time zone",
                "Set time zone automatically", "Automatic time zone", "Select time zone",
                "Set date", "Set time", "Use 24-hour format"
            )
            for (term in terms) {
                val nodes = root.findAccessibilityNodeInfosByText(term)
                if (!nodes.isNullOrEmpty()) {
                    nodes.forEach { runCatching { it.recycle() } }
                    return true
                }
            }
            val cls = root.className?.toString().orEmpty()
            if (cls.contains("DatePicker", ignoreCase = true) || cls.contains("TimePicker", ignoreCase = true)) {
                return true
            }
        }

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
            lower.contains("xhide") ||
            lower.contains("freezer") ||
            lower.contains("appmanager") ||
            lower.contains("storagemanager") ||
            lower.contains("securitycom") ||
            lower.contains("scorpio") ||
            lower.contains("ossettingsext") ||
            lower.contains("permissioncontroller") ||
            lower.contains("permissionmanager") ||
            lower.contains("securitypermission") ||
            lower.contains("managedprovisioning") ||
            lower.contains("engineermode") ||
            lower.contains("systemmanager") ||
            lower.contains("devicepolicy")
    }

    private fun isActualSettingsPkg(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (lower.contains("installer") || lower.contains("permission") || lower.contains("launcher") ||
            lower.contains("systemui") || lower.contains("phonemaster") || lower.contains("phonemanager") ||
            lower.contains("xhide") || lower.contains("freezer")) return false
        return pkg in discoveredSettingsPkgs || lower.contains("settings") || lower.contains("ossettingsext") || lower.contains("scorpio")
    }

    private fun reloadKey(key: String?) {
        if (key == null) {
            reload()
            return
        }
        when (key) {
            "domains" -> domains = BlockerStore.set(this, "domains")
            "keywords" -> keywords = BlockerStore.set(this, "keywords") - RETIRED_KEYWORDS
            "tlds" -> tlds = BlockerStore.set(this, "tlds")
            "whitelist" -> whitelist = BlockerStore.set(this, "whitelist")
            "apps" -> apps = BlockerStore.set(this, "apps")
            "scan_exempt" -> exempt = BlockerStore.set(this, "scan_exempt")
            "focus_apps" -> focusAllowed = BlockerStore.focusApps(this) + neverBlock + FOCUS_SYSTEM_PKGS + imePackages() + packageName
            "block_fb_reels" -> blockFbReels = BlockerStore.granularToggle(this, "block_fb_reels")
            "block_insta_reels" -> blockInstaReels = BlockerStore.granularToggle(this, "block_insta_reels")
            "block_insta_search" -> blockInstaSearch = BlockerStore.granularToggle(this, "block_insta_search")
            "block_yt_shorts" -> blockYtShorts = BlockerStore.granularToggle(this, "block_yt_shorts")
            "app_limits" -> appLimits = BlockerStore.appLimits(this)
            "active", "shield", "admin_disable_requested" -> { /* state flags don't invalidate package sets */ }
            else -> {
                if (key.startsWith("sec_") || key.startsWith("tamper") || key.startsWith("admin_") ||
                    key.startsWith("today_usage_") || key.startsWith("open_") || key.startsWith("pending_") ||
                    key.startsWith("study_") || key.startsWith("stat_") || key == "state_integrity_hmac") {
                    return
                }
                reload()
            }
        }
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
        val nowElapsed = SystemClock.elapsedRealtime()
        val cached = todayUsageCache[pkg]
        val base = if (cached != null && (nowElapsed - cached.first in 0L..4000L)) {
            cached.second
        } else {
            val fresh = BlockerStore.getTodayUsageMillis(this, pkg)
            todayUsageCache[pkg] = Pair(nowElapsed, fresh)
            fresh
        }
        val nowWall = System.currentTimeMillis()
        val activeSession = if (isInteractive && currentFgPkg == pkg && fgStartTime > 0L) {
            (nowWall - fgStartTime).coerceAtLeast(0L)
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
                getFearAllahHeading(),
                formatAppUsageLimitBody(pkg, limitStr),
                getFearAllahSubheading(),
                pkg,
                getAstaghfirullahButton()
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

            // --- 0MS TOP-PRIORITY INTERCEPT: Launcher press-and-hold & shortcut protection ---
            if (guardLauncherInstant(ev, pkg)) return

            // INSTANT PRIORITY INTERCEPT: Blocked apps are kicked out immediately before any other checks
            if (BlockerStore.active(this) && pkg in apps) {
                hit(
                    "apps",
                    getFearAllahHeading(),
                    formatBlockedAppBody(pkg),
                    getFearAllahSubheading(),
                    pkg,
                    getAstaghfirullahButton()
                )
                return
            }

            // --- 0MS ZERO-LATENCY BROWSER GATEKEEPER: Instant check before throttles, usage, or settings ---
            if (BlockerStore.active(this) && pkg in BROWSER_URL_IDS) {
                if (checkBrowserFast(pkg, ev)) return
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
        if (checkPowerMenuDialog(ev, pkg)) return
        if (checkScheduledPowerOff(ev, pkg)) return

        // Proactive Date & Time intercept: runs before neverBlock or active checks to catch OEM clock/settings launchers
        if (shouldBlockDateTime() && (isSettingsPkg(pkg) || isClockPkg(pkg) || pkg == "android")) {
            val rootNow = rootInActiveWindow ?: ev.source
            if (!isInstallOrUpdateDialog(ev, rootNow)) {
                val winTitle = ev.text.joinToString(" ").trim().lowercase()
                if (!isLegitimatePermissionScreen(rootNow, winTitle)) {
                    startSettingsPoll()
                    val evCls = ev.className?.toString().orEmpty()
                    val rootCls = runCatching { rootNow?.className?.toString().orEmpty() }.getOrDefault(evCls)
                    if (isDateTimeSettingsScreen(rootNow, winTitle) ||
                        evCls in DATE_TIME_CLASSES || rootCls in DATE_TIME_CLASSES ||
                        evCls.contains("DateTimeSettings", ignoreCase = true) || rootCls.contains("DateTimeSettings", ignoreCase = true) ||
                        evCls.contains("ZonePicker", ignoreCase = true) || rootCls.contains("ZonePicker", ignoreCase = true) ||
                        evCls.contains("DatePicker", ignoreCase = true) || rootCls.contains("DatePicker", ignoreCase = true) ||
                        evCls.contains("TimePicker", ignoreCase = true) || rootCls.contains("TimePicker", ignoreCase = true)) {
                        kickToHome()
                        BlockerStore.incr(this, "tamper")
                        showOverlay(getTamperHeading(), formatDateTimeLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
                        return
                    }
                }
            }
        }
        if (BlockerStore.studyActive(this) && studyBlocks(ev, pkg)) return

        // Proactive App Hiding & Freezer Guard: Protects against hiding or freezing Blocker from native launchers, XHide, Freezer, or settings
        if (guardAppHidingAndFreezer(ev, pkg)) return

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
                    if (!isLegitimatePermissionScreen(rootForScan, txt) && !isInstallOrUpdateDialog(ev, rootForScan)) {
                        val hasDestructiveAction = "force stop" in txt || "uninstall" in txt || "disable" in txt ||
                            "clear storage" in txt || "clear data" in txt || "manage space" in txt || "storage & cache" in txt
                        if (hasDestructiveAction) {
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
                }
            } else if (titleMatchesBlocker && !isInstallOrUpdateDialog(ev, null)) {
                if (!isLegitimatePermissionScreen(null, winTitle)) {
                    val hasDestructiveAction = "force stop" in winTitle || "uninstall" in winTitle || "disable" in winTitle
                    if (hasDestructiveAction) {
                        viewingBlockerSettingsUntil = now + 30_000L
                        if (isActualSettingsPkg(pkg)) {
                            kickFromAppInfo()
                        } else {
                            lockOutOfSettings("Blocker app settings and storage are locked while Protection Mode is active.")
                        }
                        return
                    }
                }
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

            // ── 6. Date & Time settings screen: prevent clock advance bypass ─────────
            if (shouldBlockDateTime()) {
                val rootNow = rootInActiveWindow ?: ev.source
                if (isDateTimeSettingsScreen(rootNow, winTitle) ||
                    evCls in DATE_TIME_CLASSES || rootCls in DATE_TIME_CLASSES ||
                    evCls.contains("DateTimeSettings", ignoreCase = true) || rootCls.contains("DateTimeSettings", ignoreCase = true) ||
                    evCls.contains("ZonePicker", ignoreCase = true) || rootCls.contains("ZonePicker", ignoreCase = true) ||
                    evCls.contains("DatePicker", ignoreCase = true) || rootCls.contains("DatePicker", ignoreCase = true) ||
                    evCls.contains("TimePicker", ignoreCase = true) || rootCls.contains("TimePicker", ignoreCase = true)) {
                    kickToHome()
                    BlockerStore.incr(this, "tamper")
                    showOverlay(getTamperHeading(), formatDateTimeLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
                    return
                }
            }

            // ── 7. Scheduled Power Off settings screen: prevent scheduled shutdown bypass ───
            if (shouldBlockScheduledPower()) {
                val rootNow = rootInActiveWindow ?: ev.source
                if (isScheduledPowerSettingsScreen(rootNow, winTitle, evCls, pkg)) {
                    kickToHome()
                    BlockerStore.incr(this, "tamper")
                    showOverlay(getTamperHeading(), formatScheduledPowerLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
                    return
                }
            }
        }

        when {
            pkg in apps -> {
                hit(
                    "apps",
                    getFearAllahHeading(),
                    formatBlockedAppBody(pkg),
                    getFearAllahSubheading(),
                    pkg,
                    getAstaghfirullahButton()
                )
                return
            }
            pkg in BROWSER_URL_IDS -> if (checkBrowserFast(pkg, ev)) return
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

    fun bringBlockerToForeground() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    // ---------- Study mode (Aggressive Pomodoro Phone Lockout) ----------

    private var lastStudyRedirect = 0L
    private var lastShadeDismiss = 0L

    private fun collapseNotificationShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { performGlobalAction(15) } // GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
        }
        runCatching {
            val sbservice = getSystemService("statusbar")
            val statusbarManager = Class.forName("android.app.StatusBarManager")
            val collapse = statusbarManager.getMethod("collapsePanels")
            collapse.invoke(sbservice)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            runCatching {
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            }
        }
    }

    private fun studyBlocks(ev: AccessibilityEvent, pkg: String): Boolean {
        // 1. If inside Blocker itself, never block! Let the user view and use the Pomodoro timer uninterrupted.
        if (pkg == packageName) return false

        // 2. Emergency phone calls and soft keyboards must always work.
        if (pkg in EMERGENCY_CALL_PKGS) return false
        if (pkg in imePackages()) return false
        if (pkg == "android") return false

        val now = System.currentTimeMillis()

        // 3. SystemUI handling (Notification shade, Quick Settings tiles for Wi-Fi / Mobile Data):
        // Automatically collapse the notification panel so Wi-Fi, Mobile Data, and alerts are shielded.
        // Navigation bar touches are preserved so in-app buttons continue to function.
        if (pkg == "com.android.systemui") {
            val cls = ev.className?.toString().orEmpty()
            val isNavBar = cls.contains("NavigationBar", ignoreCase = true) || cls.contains("Navigation", ignoreCase = true)
            if (!isNavBar) {
                if (now - lastShadeDismiss >= 400L) {
                    lastShadeDismiss = now
                    collapseNotificationShade()
                }
            }
            return false
        }

        // 4. Home launcher:
        // Redirect smoothly back to Blocker's Pomodoro timer without looping.
        if (pkg in launcherPackages()) {
            if (now - lastStudyRedirect >= 800L) {
                lastStudyRedirect = now
                bringBlockerToForeground()
            }
            return true
        }

        // 5. Unauthorized apps, Settings, Browsers, and Games:
        // Immediately kick off the screen back to Home, bring Blocker back to front, and show study lockout dialog.
        if (now - lastStudyRedirect >= 600L) {
            lastStudyRedirect = now
            BlockerStore.incr(this, "study")
            performGlobalAction(GLOBAL_ACTION_HOME)
            main.postDelayed({ bringBlockerToForeground() }, 180)
            val isBreak = BlockerStore.studyIsBreak(this)
            val cur = BlockerStore.studyCurrentRound(this)
            val tot = BlockerStore.studyTotalRounds(this)
            val phase = if (isBreak) (if (isBangla()) "বিরতির সময়" else "Break Time") else (if (isBangla()) "রাউন্ড $cur / $tot" else "Round $cur of $tot")
            showOverlay(
                if (isBangla()) "স্টাডি মোড সক্রিয়" else "Study Mode Active",
                formatStudyModeBody(phase, studyLeftText()),
                4000,
                if (isBangla()) "আপনার অধ্যয়নে মনোযোগী থাকুন" else "Stay dedicated to your session",
                if (isBangla()) "পড়াশোনায় ফিরুন" else "Back to Study"
            )
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME)
            main.postDelayed({ bringBlockerToForeground() }, 180)
        }
        return true
    }

    private fun studyLeftText(): String {
        val ms = BlockerStore.studyRemainingMs(this)
        val m = ms.coerceAtLeast(0L) / 60_000L + 1
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    // ---------- Focus mode ----------

    /** During focus mode everything outside the essential apps / system helpers is kicked to the home screen. */
    private fun focusBlocks(ev: AccessibilityEvent, pkg: String): Boolean {
        if (pkg in focusAllowed || pkg in BlockerStore.scheduleAllowedApps(this)) return false
        val trusted = ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            rootInActiveWindow?.packageName?.toString() == pkg
        if (!trusted) return false
        hit(
            "focus",
            if (isBangla()) "ফোকাস মোড সক্রিয়" else "Focus Mode Active",
            formatFocusModeBody(leftText()),
            if (isBangla()) "কল্যাণকর কাজে সময় ব্যয় করুন" else "Guard your time for what benefits you",
            pkg,
            getUnderstoodButton()
        )
        return true
    }

    private fun leftText(): String {
        val ms = BlockerStore.focusRemainingMs(this)
        val m = ms.coerceAtLeast(0L) / 60_000L + 1
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    // ---------- URL bar ----------

    private var lastCheckedUrlHash = 0L
    private val reusableCharBuffer = CharArray(2048)

    private fun checkBrowserFast(pkg: String, ev: AccessibilityEvent): Boolean {
        if (System.currentTimeMillis() < (quietUntil[pkg] ?: 0L)) return true
        val targetIds = BROWSER_URL_IDS[pkg] ?: return false

        // 1. Ultra-fast path: Check ev.source directly to bypass rootInActiveWindow Binder IPC
        val src = ev.source
        if (src != null) {
            try {
                val srcId = src.viewIdResourceName
                if (srcId != null && srcId in targetIds) {
                    val text = src.text
                    if (text != null && text.isNotEmpty()) {
                        if (checkUrlText(text, pkg)) return true
                    }
                } else {
                    for (id in targetIds) {
                        val subNodes = src.findAccessibilityNodeInfosByViewId(id)
                        if (!subNodes.isNullOrEmpty()) {
                            var matched = false
                            val t = subNodes[0].text
                            if (t != null && t.isNotEmpty()) {
                                matched = checkUrlText(t, pkg)
                            }
                            for (n in subNodes) recycleNode(n)
                            if (matched) return true
                        }
                    }
                }
            } finally {
                recycleNode(src)
            }
        }

        // 2. Fallback: inspect root window if event source didn't hold the address bar
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != pkg) {
            recycleNode(root)
            return false
        }
        try {
            for (id in targetIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    var matched = false
                    val target = nodes[0]
                    val text = target.text
                    if (text != null && text.isNotEmpty()) {
                        matched = checkUrlText(text, pkg)
                    }
                    for (n in nodes) recycleNode(n)
                    if (matched) return true
                }
            }
        } finally {
            recycleNode(root)
        }
        return false
    }

    private fun checkUrlText(text: CharSequence, pkg: String): Boolean {
        val len = minOf(text.length, reusableCharBuffer.size)
        for (i in 0 until len) {
            reusableCharBuffer[i] = text[i]
        }
        // STAGE 3: Deduplicate identical events via FNV-1a 64-bit hash
        val currentHash = NativeBlockerBridge.computeFnv1a(reusableCharBuffer, len)
        if (currentHash == lastCheckedUrlHash) {
            return false
        }
        lastCheckedUrlHash = currentHash

        var rule: String? = null
        // C++ Zero-Heap mmap engine check (sub-microsecond)
        if (NativeBlockerBridge.isEngineReady()) {
            if (NativeBlockerBridge.matchUrlFast(reusableCharBuffer, len)) {
                rule = "Harmful URL"
            }
        }
        if (rule == null) {
            val str = text.toString()
            if (str.isNotBlank()) rule = matchAddress(str)
        }

        if (rule != null) {
            redirectBrowser(
                pkg,
                "sites",
                getFearAllahHeading(),
                formatBlockedUrlBody(rule),
                getFearAllahSubheading()
            )
            return true
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
        val decoded = runCatching { java.net.URLDecoder.decode(t, "UTF-8") }.getOrDefault(t)
        val h = decoded.removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':').removePrefix("www.")
        return h.takeIf { it.contains('.') && !it.contains(' ') }
    }

    // ---------- Screen content monitor ----------

    private fun scanScreen(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) {
            recycleNode(root)
            return
        }
        val now = System.currentTimeMillis()
        if (now < (quietUntil[pkg] ?: 0L)) {
            recycleNode(root)
            return
        }
        if (now - (lastScan[pkg] ?: 0L) < SCAN_GAP_MS) {
            recycleNode(root)
            return
        }
        lastScan[pkg] = now

        try {
            val sb = StringBuilder()
            collectText(root, sb, 0, intArrayOf(200))
            if (sb.isEmpty()) return

            if (NativeBlockerBridge.isEngineReady()) {
                val len = minOf(sb.length, reusableCharBuffer.size)
                for (i in 0 until len) {
                    reusableCharBuffer[i] = sb[i]
                }
                if (NativeBlockerBridge.matchTextFast(reusableCharBuffer, len)) {
                    screenHit(pkg, "Harmful Content")
                    return
                }
            }

            val kw = wholeWordHit(sb.toString().lowercase()) ?: return
            screenHit(pkg, kw)
        } finally {
            recycleNode(root)
        }
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
        quietUntil[pkg] = System.currentTimeMillis() + 600L
        if (pkg in BROWSER_URL_IDS) {
            BlockerStore.incr(this, "screen")
            showOverlay(
                getFearAllahHeading(),
                formatScreenHitBrowserBody(),
                5000,
                getFearAllahSubheading(),
                getAstaghfirullahButton()
            )
            performGlobalAction(GLOBAL_ACTION_BACK)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 80)
            return
        }
        BlockerStore.incr(this, "screen")
        showOverlay(
            getFearAllahHeading(),
            formatScreenHitOtherAppBody(pkg),
            5000,
            getFearAllahSubheading(),
            getAstaghfirullahButton()
        )
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Immediately ejects the blocked browser tab and displays the opaque protection shield in 0ms. */
    private fun redirectBrowser(pkg: String, kind: String, heading: String, body: String, subheading: String? = null) {
        lastCheckedUrlHash = 0L
        quietUntil[pkg] = System.currentTimeMillis() + 600L
        BlockerStore.incr(this, kind)
        showOverlay(heading, body, 5000, subheading, getAstaghfirullahButton())
        performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 80)
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
            showOverlay(getTamperHeading(), formatAdbLockedBody(), 5000, getTamperSubheading(), getUnderstoodButton())
            return true
        }
        return false
    }

    private var lastPowerMenuHit = 0L

    private fun checkPowerMenuDialog(ev: AccessibilityEvent, pkg: String): Boolean {
        if (!BlockerStore.preventPowerOff(this)) return false
        if (BlockerStore.guardOpen(this, "power_menu")) return false
        if (pkg == packageName) return false

        val pkgLower = pkg.lowercase()
        // Must originate from OS / System UI or dedicated power/globalactions system components
        val isSystemPkg = pkg == "android" || pkg == "com.android.systemui" ||
            pkgLower.contains("globalactions") || pkgLower.contains("power") ||
            pkgLower.contains("systemui") || pkgLower.contains("transsion")
        if (!isSystemPkg) return false

        // Determine dialog node to inspect. NEVER inspect our own app or non-system nodes.
        val dialogNode = when {
            ev.source != null && ev.source?.packageName?.toString()?.lowercase() != packageName.lowercase() -> ev.source
            rootInActiveWindow != null && rootInActiveWindow?.packageName?.toString()?.lowercase() != packageName.lowercase() -> rootInActiveWindow
            else -> null
        } ?: return false

        val nodePkg = dialogNode.packageName?.toString().orEmpty().lowercase()
        if (nodePkg == packageName.lowercase()) return false

        val isSystemNode = nodePkg == "android" || nodePkg == "com.android.systemui" ||
            nodePkg.contains("globalactions") || nodePkg.contains("systemui") ||
            nodePkg.contains("transsion") || nodePkg.contains("power")
        if (!isSystemNode) return false

        val evCls = ev.className?.toString().orEmpty()
        val isGlobalActionsCls = evCls.contains("GlobalActions", ignoreCase = true) ||
            evCls.contains("PowerDialog", ignoreCase = true) ||
            evCls.contains("ShutdownActivity", ignoreCase = true) ||
            evCls.contains("RestartActivity", ignoreCase = true)

        val sb = StringBuilder()
        collectText(dialogNode, sb, 0, intArrayOf(100))
        val text = sb.toString().lowercase()

        // Absolute safeguard: if this node mentions Blocker or our own settings toggles, do NOT block
        if ("blocker" in text || "prevent power off" in text || packageName.lowercase() in text) return false

        val hasPowerOff = "power off" in text || "shut down" in text || "power down" in text || "turn off" in text
        val hasRestart = "restart" in text || "reboot" in text
        val hasEmergencyOrLockdown = "emergency" in text || "lockdown" in text || "safe mode" in text

        // A power menu contains power actions (e.g. power off AND restart, or power action + emergency/lockdown)
        val isPowerMenu = isGlobalActionsCls ||
            (hasPowerOff && hasRestart) ||
            ((hasPowerOff || hasRestart) && hasEmergencyOrLockdown)

        if (isPowerMenu) {
            val now = System.currentTimeMillis()
            if (now - lastPowerMenuHit < 600L) return true
            lastPowerMenuHit = now

            // Immediately dismiss power dialog via back action, collapse panels, and kick
            performGlobalAction(GLOBAL_ACTION_BACK)
            collapseNotificationShade()
            kickToHome()
            BlockerStore.incr(this, "tamper")

            showOverlay(
                if (isBangla()) "🔐 পাওয়ার অফ লক করা" else "🔐 Power Off Locked",
                formatPowerOffLockedBody(),
                4000,
                getProtectionActiveSubheading(),
                getUnderstoodButton()
            )
            return true
        }
        return false
    }

    private var lastScheduledPowerHit = 0L

    private fun shouldBlockScheduledPower(): Boolean {
        if (BlockerStore.preventPowerOff(this)) return true
        if (BlockerStore.shield(this) && !BlockerStore.guardOpen(this, "shield")) return true
        if (BlockerStore.active(this)) return true
        return false
    }

    private fun clickCancelOrDismiss(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val cancelLabels = listOf("cancel", "dismiss", "stop", "postpone", "later", "abort", "close")
        for (label in cancelLabels) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByText(label) }.getOrNull() ?: continue
            for (node in nodes) {
                if (node == null) continue
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                val parent = node.parent
                if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isScheduledPowerSettingsScreen(root: AccessibilityNodeInfo?, text: String, evCls: String, pkg: String): Boolean {
        var t = text.lowercase()
        if (t.isEmpty() && root != null) {
            val sb = StringBuilder()
            collectText(root, sb, 0, intArrayOf(100))
            t = sb.toString().lowercase()
        }
        val cls = evCls.lowercase()
        val pkgLower = pkg.lowercase()

        // 1. MediaTek or OEM scheduled power package
        if (pkgLower == "com.mediatek.schpwronoff" || pkgLower.contains("schpwronoff")) return true

        // 2. Class names across Android skins
        if (cls.contains("schedulepower") || cls.contains("schpwronoff") ||
            cls.contains("scheduledpower") || cls.contains("autopoweronoff") ||
            cls.contains("poweronoffschedule")) {
            return true
        }

        // Parent menu exclusion (so we don't accidentally block the top-level System or Settings menu)
        val isParentMenu = "language" in t || "gesture" in t || "backup" in t || "reset" in t ||
            "keyboard" in t || "system update" in t || "about phone" in t || "battery" in t ||
            "display" in t || "sound" in t || "network & internet" in t || "wallpaper" in t ||
            "accessibility" in t || "apps & notifications" in t
        if (isParentMenu) return false

        // 3. Screen content phrases indicating the scheduled power subpage
        if ("scheduled power" in t || "schedule power" in t || "auto power on/off" in t ||
            "auto power off" in t || "power on/off schedule" in t || "automatic power on/off" in t ||
            "scheduled shutdown" in t || "schedule shutdown" in t) {
            return true
        }

        // 4. Samsung Auto restart at set times subpage
        if ("auto restart at set times" in t || ("auto restart" in t && ("restart when needed" in t || "schedule" in t))) {
            return true
        }

        return false
    }

    private fun checkScheduledPowerOff(ev: AccessibilityEvent, pkg: String): Boolean {
        if (!shouldBlockScheduledPower()) return false
        if (pkg == packageName) return false

        val pkgLower = pkg.lowercase()
        val evCls = runCatching { ev.className?.toString().orEmpty().lowercase() }.getOrDefault("")
        val isMtkSch = pkgLower == "com.mediatek.schpwronoff" || pkgLower.contains("schpwronoff")

        val root = ev.source ?: rootInActiveWindow
        val rootPkg = runCatching { root?.packageName?.toString().orEmpty().lowercase() }.getOrDefault("")
        if (rootPkg == packageName.lowercase()) return false

        val sb = StringBuilder()
        if (root != null) {
            collectText(root, sb, 0, intArrayOf(200))
        }
        val text = sb.toString().lowercase()

        // Never trigger on Blocker settings
        if ("blocker" in text || "prevent power off" in text || packageName.lowercase() in text) return false

        // A. Countdown / Scheduled Shutdown Dialog: when a scheduled shutdown alarm actually fires!
        val isShutdownAlert = (isMtkSch || isSettingsPkg(pkgLower) || pkgLower == "android" || pkgLower == "com.android.systemui") &&
            (evCls.contains("shutdown") || evCls.contains("alert") || evCls.contains("dialog")) &&
            ("shut down" in text || "shutdown" in text || "power off" in text || "scheduled power" in text || "will shut down" in text || "will power off" in text)

        if (isShutdownAlert || (isMtkSch && (evCls.contains("shutdown") || "cancel" in text))) {
            val now = System.currentTimeMillis()
            if (now - lastScheduledPowerHit < 800L) return true
            lastScheduledPowerHit = now

            // Immediately abort the scheduled shutdown by pressing Cancel
            clickCancelOrDismiss(root)

            // Dismiss dialog via back action, collapse shade, kick to home
            performGlobalAction(GLOBAL_ACTION_BACK)
            collapseNotificationShade()
            kickToHome()
            BlockerStore.incr(this, "tamper")

            showOverlay(
                if (isBangla()) "🔐 পাওয়ার অফ লক করা" else "🔐 Power Off Locked",
                if (isBangla()) "আপনার সুরক্ষার জন্য স্বয়ংক্রিয় পাওয়ার অফ বাতিল করা হয়েছে।\n\nঅবিচল থাকুন।" else "Scheduled power off was cancelled for your protection.\n\nRemain steadfast.",
                5000,
                getProtectionActiveSubheading(),
                getUnderstoodButton()
            )
            return true
        }

        // B. Scheduled Power On/Off settings screen: prevent user from scheduling a shutdown
        if (isScheduledPowerSettingsScreen(root, text, evCls, pkgLower)) {
            val now = System.currentTimeMillis()
            if (now - lastScheduledPowerHit < 800L) return true
            lastScheduledPowerHit = now

            kickToHome()
            BlockerStore.incr(this, "tamper")

            showOverlay(
                getTamperHeading(),
                formatScheduledPowerLockedBody(),
                5000,
                getProtectionActiveSubheading(),
                getUnderstoodButton()
            )
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
        val isAppInfoScreen = isOurApp && !isLegitimatePermissionScreen(root, t) && (
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
        val shieldOn = shieldEngaged()
        val dtOn = shouldBlockDateTime()
        val schOn = shouldBlockScheduledPower()
        if (!shieldOn && !dtOn && !schOn) return
        val root = rootInActiveWindow ?: ev.source ?: return
        val cls = root.className?.toString().orEmpty()
        val evCls = ev.className?.toString().orEmpty()
        val evText = ev.text.joinToString(" ").lowercase()

        if (shieldOn) {
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

        if (shieldOn) {
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
        }

        // Date & Time settings guard: strictly blocks system clock alteration
        if (dtOn && isDateTimeSettingsScreen(root, fullText)) {
            kickToHome()
            BlockerStore.incr(this, "tamper")
            showOverlay(getTamperHeading(), formatDateTimeLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
            return
        }

        // Scheduled Power Off guard: strictly blocks scheduled shutdown configuration
        if (schOn && isScheduledPowerSettingsScreen(root, fullText, evCls, root.packageName?.toString().orEmpty())) {
            kickToHome()
            BlockerStore.incr(this, "tamper")
            showOverlay(getTamperHeading(), formatScheduledPowerLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
            return
        }

        if (!shieldOn) return

        // 3. App Storage Clearing & App Info Protection for Blocker (even if Device Admin was removed)
        val storageRiskTerms = listOf(
            "clear storage", "clear data", "manage space", "clear cache", "delete app data", "clear all data",
            "delete data", "clear app data", "all of this app's data will be deleted", "all this app's data will be deleted"
        )
        val isStorageRisk = storageRiskTerms.any { it in fullText }
        val isOurApp = "blocker" in fullText || packageName.lowercase() in fullText
        val hasRiskAction = APP_RISK_TERMS.any { it in fullText } || isStorageRisk

        // If currently viewing Blocker's settings or app details
        if (isOurApp && !isLegitimatePermissionScreen(root, fullText) && !isInstallOrUpdateDialog(ev, root)) {
            val hasDestructiveAction = "force stop" in fullText || "uninstall" in fullText || "disable" in fullText ||
                "clear storage" in fullText || "clear data" in fullText || "manage space" in fullText || "storage & cache" in fullText
            if (hasDestructiveAction) {
                viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
                lockOutOfSettings("Blocker app settings and storage are locked while Protection Mode is active.")
                return
            }
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
        if (pkg == "com.android.systemui" || pkg == "android") return
        val nowUp = SystemClock.uptimeMillis()
        val settingsLike = (isSettingsPkg(pkg) || isInstallerPkg(pkg)) && pkg !in neverBlock
        val watched = pkg == appInfoWatchPkg && nowUp < appInfoWatchUntil
        if (!settingsLike && !watched) return

        if (isInstallOrUpdateDialog(ev, null)) return
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (guardDateTimeRowClick(ev, pkg)) return
            if (guardScheduledPowerRowClick(ev, pkg)) return
            if (guardDestructiveAppInfoClick(ev, pkg)) return
            guardBlockerRowClick(ev, pkg)
            return
        }
        val stateChange = ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (stateChange && isSettingsPkg(pkg)) {
            val winTitle = ev.text.joinToString(" ").trim().lowercase()
            if (winTitle.isNotEmpty() && !titleIsAppName(ev)) {
                currentSettingsScreenTitle = winTitle
            }
        }
        if (!isSettingsPkg(pkg) && pkg != "android") {
            currentSettingsScreenTitle = ""
        }
        if (!stateChange && ev.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val titleMatches = titleIsAppName(ev)
        val announced = stateChange && (looksLikeAppInfoClass(ev.className) || titleMatches)
        if (announced) {
            appInfoWatchPkg = pkg
            appInfoWatchUntil = nowUp + 5_000L
        }
        if (settingsLike) enforceAdbOffThrottled()
        if (!shieldEngaged()) return

        if (stateChange) {
            lastWindowOpenUp = nowUp
            val cls = ev.className?.toString()?.lowercase().orEmpty()
            if (settingsLike && DEVICE_ADMIN_CLASS_HINTS.any { it in cls } && ("deviceadminadd" !in cls || isOurAdminActive())) {
                pollKick("Device Admin settings are locked while Protection Mode is active.")
                return
            }
            if (titleMatches && !isInstallOrUpdateDialog(ev, rootInActiveWindow)) {
                if (!isLegitimatePermissionTitle(currentSettingsScreenTitle)) {
                    if (isActualSettingsPkg(pkg)) {
                        kickFromAppInfo()
                    } else {
                        autoCancelDialog(rootInActiveWindow)
                        pollKick("Blocker cannot be uninstalled while Protection Mode is active.")
                    }
                    return
                }
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
                if (!isLegitimatePermissionScreen(root, t) && !isInstallOrUpdateDialog(ev, root)) {
                    val hasDestructiveAction = "force stop" in t || "uninstall" in t || "disable" in t ||
                        "clear storage" in t || "clear data" in t || "manage space" in t || "storage & cache" in t
                    if (hasDestructiveAction) {
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
        if (isInstallOrUpdateDialog(ev, null)) return
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
        if (!hit) return

        // Blocker row was clicked inside Settings!
        // If user is inside a legitimate permission grant screen (e.g. Do Not Disturb access, Battery optimization), allow it:
        if (isLegitimatePermissionTitle(currentSettingsScreenTitle)) return

        kickFromAppInfo()
    }

    /**
     * Intercepts taps on "Date & time" or "Date and time" rows inside Settings before the page ever opens.
     */
    private fun guardDateTimeRowClick(ev: AccessibilityEvent, pkg: String): Boolean {
        if (!isSettingsPkg(pkg) && !isClockPkg(pkg)) return false
        if (!shouldBlockDateTime()) return false
        if (isInstallOrUpdateDialog(ev, null)) return false

        val text = ev.text.joinToString(" ").lowercase()
        val cd = ev.contentDescription?.toString()?.lowercase().orEmpty()
        val combined = "$text $cd".trim()
        if (isLegitimatePermissionScreen(combined)) return false

        val isHit = combined == "date & time" || combined == "date and time" ||
            combined.contains("date & time") || combined.contains("date and time") ||
            combined.contains("change date & time") || combined.contains("set time automatically") ||
            combined.contains("automatic date & time") || combined.contains("automatic date and time") ||
            (combined.contains("set date") && combined.contains("set time"))
        if (isHit) {
            kickToHome()
            BlockerStore.incr(this, "tamper")
            showOverlay(getTamperHeading(), formatDateTimeLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
            return true
        }
        val src = ev.source
        if (src != null) {
            val sb = StringBuilder()
            collectText(src, sb, 0, intArrayOf(50))
            val srcText = sb.toString().lowercase()
            if (isLegitimatePermissionScreen(srcText)) {
                runCatching { src.recycle() }
                return false
            }
            if (isDateTimeSettingsScreen(src, srcText)) {
                runCatching { src.recycle() }
                kickToHome()
                BlockerStore.incr(this, "tamper")
                showOverlay(getTamperHeading(), formatDateTimeLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
                return true
            }

            val terms = listOf(
                "Date & time", "Date and time", "Set time automatically",
                "Automatic date & time", "Automatic date and time", "Use network-provided time"
            )
            for (term in terms) {
                val nodes = src.findAccessibilityNodeInfosByText(term)
                if (!nodes.isNullOrEmpty()) {
                    nodes.forEach { runCatching { it.recycle() } }
                    runCatching { src.recycle() }
                    kickToHome()
                    BlockerStore.incr(this, "tamper")
                    showOverlay(getTamperHeading(), formatDateTimeLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
                    return true
                }
            }
            runCatching { src.recycle() }
        }
        return false
    }

    /**
     * Intercepts taps on "Scheduled power on/off" rows inside Settings before the page ever opens.
     */
    private fun guardScheduledPowerRowClick(ev: AccessibilityEvent, pkg: String): Boolean {
        if (!isSettingsPkg(pkg) && pkg != "com.mediatek.schpwronoff") return false
        if (!shouldBlockScheduledPower()) return false
        if (isInstallOrUpdateDialog(ev, null)) return false

        val text = ev.text.joinToString(" ").lowercase()
        val cd = ev.contentDescription?.toString()?.lowercase().orEmpty()
        val combined = "$text $cd".trim()
        if (isLegitimatePermissionScreen(combined)) return false

        val isHit = combined.contains("scheduled power") ||
            combined.contains("schedule power") ||
            combined.contains("auto power on/off") ||
            combined.contains("auto power off") ||
            combined.contains("auto restart") ||
            combined.contains("power on/off schedule") ||
            combined.contains("scheduled shutdown") ||
            combined.contains("schedule shutdown")

        if (isHit) {
            kickToHome()
            BlockerStore.incr(this, "tamper")
            showOverlay(getTamperHeading(), formatScheduledPowerLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
            return true
        }

        val src = ev.source
        if (src != null) {
            val sb = StringBuilder()
            collectText(src, sb, 0, intArrayOf(50))
            val srcText = sb.toString().lowercase()
            if (isScheduledPowerSettingsScreen(src, srcText, "", pkg)) {
                kickToHome()
                BlockerStore.incr(this, "tamper")
                showOverlay(getTamperHeading(), formatScheduledPowerLockedBody(), 5000, getProtectionActiveSubheading(), getUnderstoodButton())
                return true
            }
        }
        return false
    }

    private fun isClockPkg(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("deskclock") || lower.contains("clockpackage") || lower.contains("clock")
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

    // ---------- App Hiding & Freezer Guard ----------
    //
    // Prevents hiding or freezing Blocker via native OEM launchers, XHide (com.xui.xhide),
    // App Freezer (com.transsion.freezer), or launcher desktop popups/gestures.
    // Freezing or hiding suspends the package and kills the accessibility service, so it is strictly blocked.

    private fun isTargetBlocker(ev: AccessibilityEvent, name: String): Boolean {
        val ver = appVersionName

        fun matchesBlockerText(raw: String): Boolean {
            val s = raw.trim()
            if (s.isEmpty() || s.contains(".apk", ignoreCase = true)) return false
            if (s.equals(name, ignoreCase = true) || s.equals("Blocker", ignoreCase = true)) return true
            if (isAppNameText(s, name, ver) || isAppNameText(s, "Blocker", ver)) return true
            val lower = s.lowercase()
            for (prefix in listOf(name.lowercase(), "blocker")) {
                if (lower.startsWith(prefix)) {
                    if (lower.length == prefix.length) return true
                    val sep = lower[prefix.length]
                    if (sep in listOf(' ', ',', ':', '-', '·', '•', '\n', '\r', '(', '.')) {
                        return true
                    }
                }
            }
            return false
        }

        for (t in ev.text) {
            val s = t?.toString().orEmpty()
            if (matchesBlockerText(s)) return true
        }
        val cd = ev.contentDescription?.toString().orEmpty()
        if (matchesBlockerText(cd)) return true

        val src = ev.source ?: return false
        try {
            // Direct row / node check
            if (rowShowsName(src, name, ver, 0, intArrayOf(25))) return true

            // Ascend to parent item view within the same row/card (never ascend into list/recycler container)
            var curr: AccessibilityNodeInfo? = src.parent
            var depth = 0
            while (curr != null && depth < 2) {
                val node = curr
                val cls = node.className?.toString().orEmpty().lowercase()
                // Stop if we hit a list/recycler container that holds multiple rows
                if (node.isScrollable || cls.contains("recycler") || cls.contains("listview") || cls.contains("gridview")) {
                    runCatching { node.recycle() }
                    curr = null
                    break
                }
                val budget = intArrayOf(25)
                if (rowShowsName(node, name, ver, 0, budget)) {
                    runCatching { node.recycle() }
                    curr = null
                    return true
                }
                val next = node.parent
                runCatching { node.recycle() }
                curr = next
                depth++
            }
            curr?.let { runCatching { it.recycle() } }
        } catch (_: Throwable) {
        } finally {
            runCatching { src.recycle() }
        }
        return false
    }

    /** Returns true if the screen is a launcher settings / customization menu (e.g. Desktop Settings, Home Screen Settings). */
    private fun isLauncherSettingsMenu(cls: String, title: String, text: String): Boolean {
        val t = text.lowercase()
        val tit = title.lowercase()
        val c = cls.lowercase()

        val isSettingsTitle = tit.contains("desktop setting") || tit.contains("desktop settings") ||
            tit.contains("home screen setting") || tit.contains("home screen settings") ||
            tit.contains("launcher setting") || tit.contains("launcher settings") ||
            tit.contains("home settings") || tit.contains("home setting") ||
            tit.contains("preferences")

        val hasLauncherControls =
            ("desktop grid" in t || "home screen grid" in t || "app screen grid" in t || "icon size" in t ||
             "page effect" in t || "page effects" in t || "gestures" in t || "gesture" in t ||
             "folder style" in t || "folder arrangement" in t || "auto arrange" in t ||
             "switch style" in t || "appearance" in t || "dock" in t || "transition effect" in t ||
             "columns" in t || "rows" in t)

        return (isSettingsTitle || c.contains("desktopsettings") || c.contains("launchersettings") || c.contains("homescreensettings")) &&
            (hasLauncherControls || (!tit.contains("hide") && !tit.contains("freez")))
    }

    /** Returns true if the screen is dedicated to hiding or freezing apps. */
    private fun isHideOrFreezerScreen(pkg: String, cls: String, title: String, text: String): Boolean {
        // 1. Dedicated Freezer package: 100% purpose is freezing apps
        if (pkg == "com.transsion.freezer" || (pkg.contains("freezer") && !isLauncherPkg(pkg))) {
            return true
        }

        // 2. Dedicated XHide package: 100% purpose is hiding apps/files
        if (pkg == "com.xui.xhide" || pkg.contains(".xhide")) {
            return true
        }

        // 3. If this is a launcher settings customization menu (desktop grid, icon size, etc.), NEVER treat as hide screen!
        if (isLauncherSettingsMenu(cls, title, text)) {
            return false
        }

        val tit = title.lowercase()
        val c = cls.lowercase()
        val t = text.lowercase()

        // 4. Class matches known hide/freezer activity patterns
        if (HIDE_FREEZER_CLASS_HINTS.any { it in c }) {
            return true
        }

        // 5. Title matches hide/freezer terms exactly or starts/ends with them
        val titleMatches = HIDE_FREEZER_TERMS.any { term ->
            tit == term || tit.startsWith(term) || tit.endsWith(term) || tit.contains("hide app") || tit.contains("hidden app") || tit.contains("xhide") || tit.contains("freezer")
        }
        if (titleMatches) {
            return true
        }

        // 6. In launcher or settings, detect hide/freeze picker subscreen
        val isLauncher = isLauncherPkg(pkg)
        val isSettings = isSettingsPkg(pkg)
        if (isLauncher || isSettings) {
            val hasHideTitle = tit.contains("hide") || tit.contains("hidden") || tit.contains("freez") || tit.contains("private space")
            if (hasHideTitle) {
                return true
            }

            // In picker/vault opened from launcher or security manager
            if ("hidden apps" in t || "hide apps" in t || "xhide" in t || "app freezer" in t) {
                val isPicker = ("select" in t || "add" in t || "done" in t || "confirm" in t || "checkbox" in t) &&
                    ("hide" in t || "hidden" in t || "freeze" in t || "freezer" in t)
                if (isPicker && !isLauncherSettingsMenu(cls, title, text)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * 0MS ZERO-LATENCY LAUNCHER INTERCEPT:
     * Directly intercepts long-press on Blocker icon and destructive shortcuts (App info, Uninstall, Delete)
     * on launchers in under a millisecond, using pure in-memory string matching without synchronous Binder IPC.
     */
    private fun guardLauncherInstant(ev: AccessibilityEvent, pkg: String): Boolean {
        val eventType = ev.eventType
        if (eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return false
        }

        if (!isLauncherPkg(pkg) && pkg !in launcherPackages() && pkg != "com.android.systemui" && pkg != "android") {
            return false
        }

        if (!shieldEngaged()) return false

        val nowUp = SystemClock.uptimeMillis()
        val name = if (appLabel.isNotEmpty()) appLabel else "Blocker"
        val nameLower = name.lowercase()

        fun matchesBlockerFast(cs: CharSequence?): Boolean {
            if (cs.isNullOrBlank()) return false
            val s = cs.toString().trim()
            if (s.contains(".apk", ignoreCase = true)) return false
            val lower = s.lowercase()
            if (lower == nameLower || lower == "blocker") return true
            if (lower.startsWith(nameLower) || lower.startsWith("blocker")) {
                val prefixLen = if (lower.startsWith(nameLower)) nameLower.length else 7
                if (lower.length == prefixLen) return true
                val sep = lower[prefixLen]
                if (sep in listOf(' ', ',', ':', '-', '·', '•', '\n', '\r', '(', '.')) {
                    return true
                }
            }
            return false
        }

        // Case A: Long-press on Launcher icon (TYPE_VIEW_LONG_CLICKED) -> Dismiss immediately in 0ms!
        if (eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            var hit = false
            for (t in ev.text) {
                if (matchesBlockerFast(t)) { hit = true; break }
            }
            if (!hit && matchesBlockerFast(ev.contentDescription)) {
                hit = true
            }
            if (!hit) {
                val src = runCatching { ev.source }.getOrNull()
                if (src != null) {
                    try {
                        hit = matchesBlockerFast(src.text) || matchesBlockerFast(src.contentDescription)
                    } finally {
                        runCatching { src.recycle() }
                    }
                }
            }

            if (hit) {
                lastBlockerLongPressUp = nowUp
                appInfoWatchUntil = nowUp + 15_000L
                viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L

                // 0ms DISMISS: Execute back & home actions immediately
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                main.postDelayed({ runCatching { performGlobalAction(GLOBAL_ACTION_BACK) } }, 25L)
                main.postDelayed({ runCatching { performGlobalAction(GLOBAL_ACTION_HOME) } }, 50L)

                val nowWall = System.currentTimeMillis()
                if (nowWall - lastHideKick > 1000L) {
                    lastHideKick = nowWall
                    BlockerStore.incr(this, "tamper")
                    main.post {
                        showOverlay(
                            if (isBangla()) "🔐 আনইনস্টল সুরক্ষা" else "🔐 Uninstall Protected",
                            formatLauncherPressHoldBody(),
                            5000,
                            getProtectionActiveSubheading(),
                            getUnderstoodButton()
                        )
                    }
                }
                return true
            }
        }

        // Case B: Destructive shortcut clicked on launcher (App info, Uninstall, Delete, Remove)
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val recentlyLongPressed = (nowUp - lastBlockerLongPressUp < 8000L)
            fun isDestructiveWord(cs: CharSequence?): Boolean {
                if (cs.isNullOrBlank()) return false
                val lower = cs.toString().lowercase()
                return lower.contains("app info") || lower.contains("app details") ||
                    lower.contains("application info") || lower.contains("application details") ||
                    lower.contains("uninstall") || lower.contains("delete") ||
                    lower.contains("remove") || lower.contains("disable") ||
                    lower.contains("freeze") || lower.contains("hide")
            }

            var isDestructiveShortcut = false
            for (t in ev.text) {
                if (isDestructiveWord(t)) { isDestructiveShortcut = true; break }
            }
            if (!isDestructiveShortcut && isDestructiveWord(ev.contentDescription)) {
                isDestructiveShortcut = true
            }

            if (isDestructiveShortcut) {
                var isTarget = recentlyLongPressed
                if (!isTarget) {
                    val root = runCatching { rootInActiveWindow ?: ev.source }.getOrNull()
                    if (root != null) {
                        try {
                            val sb = StringBuilder()
                            collectText(root, sb, 0, intArrayOf(20))
                            val winText = sb.toString().lowercase()
                            if (winText.contains("blocker") || winText.contains(nameLower)) {
                                isTarget = true
                            }
                        } finally {
                            runCatching { root.recycle() }
                        }
                    }
                }

                if (isTarget) {
                    lastBlockerLongPressUp = nowUp
                    appInfoWatchUntil = nowUp + 15_000L
                    viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L

                    performGlobalAction(GLOBAL_ACTION_BACK)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    main.postDelayed({ runCatching { performGlobalAction(GLOBAL_ACTION_BACK) } }, 25L)

                    val nowWall = System.currentTimeMillis()
                    if (nowWall - lastHideKick > 1000L) {
                        lastHideKick = nowWall
                        BlockerStore.incr(this, "tamper")
                        main.post {
                            showOverlay(
                                if (isBangla()) "🔐 আনইনস্টল সুরক্ষা" else "🔐 Uninstall Protected",
                                if (isBangla()) "সুরক্ষা মোড সক্রিয় থাকা অবস্থায় ব্লকারের অ্যাপ ইনফো ও আনইনস্টল শর্টকাট লক করা রয়েছে।\n\n“এবং অঙ্গীকার পূর্ণ করো। নিশ্চয়ই অঙ্গীকার সম্পর্কে জবাবদিহি করতে হবে।” — সূরা আল-ইসরা (১৭:৩৪)" else "Blocker's app info and uninstallation shortcuts are locked while Protection Mode is active.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)",
                                5000,
                                getProtectionActiveSubheading(),
                                getUnderstoodButton()
                            )
                        }
                    }
                    return true
                }
            }
        }

        // Case C: Launcher popup window state changed after long press
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val recentlyLongPressed = (nowUp - lastBlockerLongPressUp < 4000L)
            if (recentlyLongPressed) {
                val cls = ev.className?.toString()?.lowercase().orEmpty()
                if (cls.contains("popup") || cls.contains("shortcut") || cls.contains("action") || cls.contains("menu")) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return true
                }
            }
        }

        return false
    }

    /**
     * Intercepts clicking destructive buttons (Uninstall, Force stop, Disable, Clear storage, Deactivate)
     * inside Settings App Info in 0ms!
     */
    private fun guardDestructiveAppInfoClick(ev: AccessibilityEvent, pkg: String): Boolean {
        if (!shieldEngaged()) return false
        val clickText = (ev.text.joinToString(" ") + " " + (ev.contentDescription ?: "")).trim().lowercase()
        val isDestructive = clickText.contains("uninstall") || clickText.contains("force stop") ||
            clickText.contains("disable") || clickText.contains("clear storage") ||
            clickText.contains("clear data") || clickText.contains("manage space") ||
            clickText.contains("deactivate")
        if (!isDestructive) return false

        val now = System.currentTimeMillis()
        val recentlyActive = now < viewingBlockerSettingsUntil
        var isOurApp = recentlyActive || (appInfoCandidatePkg == pkg && appInfoHotUntil > SystemClock.uptimeMillis())
        if (!isOurApp) {
            val root = runCatching { rootInActiveWindow ?: ev.source }.getOrNull()
            if (root != null) {
                try {
                    val sb = StringBuilder()
                    collectText(root, sb, 0, intArrayOf(30))
                    val txt = sb.toString().lowercase()
                    if (txt.contains("blocker") || txt.contains(packageName.lowercase())) {
                        isOurApp = true
                    }
                } finally {
                    runCatching { root.recycle() }
                }
            }
        }

        if (isOurApp) {
            viewingBlockerSettingsUntil = now + 30_000L
            runCatching { autoCancelDialog(rootInActiveWindow) }
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            kickToHome()
            BlockerStore.incr(this, "tamper")
            main.post {
                showOverlay(
                    getTamperHeading(),
                    if (isBangla()) "সুরক্ষা মোড সক্রিয় থাকা অবস্থায় ব্লকার আনইনস্টল বা পরিবর্তন করা যাবে না।" else "Blocker cannot be uninstalled or modified while Protection Mode is active.",
                    5000,
                    getProtectionActiveSubheading(),
                    getUnderstoodButton()
                )
            }
            return true
        }
        return false
    }

    private fun guardAppHidingAndFreezer(ev: AccessibilityEvent, pkg: String): Boolean {
        if (!shieldEngaged() && !BlockerStore.active(this)) return false

        val now = System.currentTimeMillis()
        val isLauncher = isLauncherPkg(pkg)
        val isHidePkg = pkg == "com.xui.xhide" || pkg == "com.transsion.freezer" || pkg.contains(".xhide") || pkg.contains(".freezer")
        val isRelevantPkg = isLauncher || isHidePkg || isSettingsPkg(pkg)

        if (!isRelevantPkg) return false

        val name = if (appLabel.isNotEmpty()) appLabel else "Blocker"
        val evCls = ev.className?.toString().orEmpty().lowercase()

        // 1. Desktop / Drawer long-press intercept on Blocker icon:
        // Blocks press-and-hold ONLY on Blocker, preventing the launcher context menu (App info / Uninstall / Drag) from appearing
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED && isLauncher) {
            if (shieldEngaged() && isTargetBlocker(ev, name)) {
                val nowUp = SystemClock.uptimeMillis()
                lastBlockerLongPressUp = nowUp
                appInfoWatchUntil = nowUp + 15_000L
                viewingBlockerSettingsUntil = now + 30_000L

                // Unconditionally dismiss long press and any popup immediately
                performGlobalAction(GLOBAL_ACTION_BACK)
                main.postDelayed({ runCatching { performGlobalAction(GLOBAL_ACTION_BACK) } }, 60L)
                kickToHome()

                if (now - lastHideKick > 1000L) {
                    lastHideKick = now
                    BlockerStore.incr(this, "tamper")
                    showOverlay(
                        if (isBangla()) "🔐 আনইনস্টল সুরক্ষা" else "🔐 Uninstall Protected",
                        formatLauncherPressHoldBody(),
                        5000,
                        getProtectionActiveSubheading(),
                        getUnderstoodButton()
                    )
                }
                return true
            }
        }

        // 2. Click events:
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val clickText = (ev.text.joinToString(" ") + " " + (ev.contentDescription ?: "")).trim().lowercase()

            // 2A. Clicking "Hide apps", "Hidden apps", "Freezer", "XHide" menu items:
            val isHideMenuItem = (clickText == "hide apps" || clickText == "hide app" ||
                clickText == "hidden apps" || clickText == "hidden app" ||
                clickText == "xhide" || clickText == "app freezer" ||
                clickText == "freezer" || clickText == "freeze apps" ||
                clickText == "hide apps on home and apps screens")
            if (isHideMenuItem && (isLauncher || isSettingsPkg(pkg))) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                if (now - lastHideKick > 1000L) {
                    lastHideKick = now
                    BlockerStore.incr(this, "tamper")
                    showOverlay(
                        getTamperHeading(),
                        formatAppHidingBody(),
                        5000,
                        getProtectionActiveSubheading(),
                        getUnderstoodButton()
                    )
                }
                return true
            }

            // 2B. Destructive popup actions (App info / Uninstall / Delete / Remove / Hide / Freeze / Disable) on Blocker:
            val isDestructiveAction = clickText.contains("app info") || clickText.contains("app details") ||
                clickText.contains("application info") || clickText.contains("application details") ||
                clickText.contains("uninstall") || clickText.contains("delete") ||
                clickText.contains("remove") || clickText.contains("disable") ||
                clickText.contains("hide") || clickText.contains("freeze") ||
                clickText.contains("freezer")

            if (isDestructiveAction && isLauncher && shieldEngaged()) {
                val nowUp = SystemClock.uptimeMillis()
                val recentlyLongPressed = (nowUp - lastBlockerLongPressUp < 6000L)
                val isTarget = isTargetBlocker(ev, name)
                var popupIsBlocker = false
                if (!isTarget && !recentlyLongPressed) {
                    val root = rootInActiveWindow ?: ev.source
                    if (root != null) {
                        val sb = StringBuilder()
                        collectText(root, sb, 0, intArrayOf(30))
                        val headText = sb.toString().lowercase()
                        if (isAppNameText(headText, name, appVersionName) || headText.contains("blocker")) {
                            popupIsBlocker = true
                        }
                    }
                }
                if (isTarget || recentlyLongPressed || popupIsBlocker) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    kickToHome()
                    if (now - lastHideKick > 1000L) {
                        lastHideKick = now
                        BlockerStore.incr(this, "tamper")
                        showOverlay(
                            if (isBangla()) "🔐 আনইনস্টল সুরক্ষা" else "🔐 Uninstall Protected",
                            if (isBangla()) "সুরক্ষা মোড সক্রিয় থাকা অবস্থায় ব্লকারের অ্যাপ ইনফো ও আনইনস্টল শর্টকাট লক করা রয়েছে।\n\n“এবং অঙ্গীকার পূর্ণ করো। নিশ্চয়ই অঙ্গীকার সম্পর্কে জবাবদিহি করতে হবে।” — সূরা আল-ইসরা (১৭:৩৪)" else "Blocker's app info and uninstallation shortcuts are locked while Protection Mode is active.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)",
                            5000,
                            getProtectionActiveSubheading(),
                            getUnderstoodButton()
                        )
                    }
                    return true
                }
            }
        }

        // 3. Screen / Window intercept: Detects entering the actual Hide Apps, Hidden Apps, Freezer, or XHide screen
        if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            ev.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val root = rootInActiveWindow ?: ev.source
            val sb = StringBuilder()
            if (root != null) collectText(root, sb, 0, intArrayOf(80))
            val winText = sb.toString().lowercase()
            val winTitle = ev.text.joinToString(" ").trim().lowercase()

            if (isHideOrFreezerScreen(pkg, evCls, winTitle, winText)) {
                if (now - lastHideKick > 500L) {
                    lastHideKick = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    main.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 120L)
                    BlockerStore.incr(this, "tamper")
                    showOverlay(
                        getTamperHeading(),
                        if (isBangla()) "ব্লকার নিষ্ক্রিয় করা রোধ করতে অ্যাপ হাইড ও ফ্রিজিং সেটিংস লক করা রয়েছে।\n\n“এবং অঙ্গীকার পূর্ণ করো। নিশ্চয়ই অঙ্গীকার সম্পর্কে জবাবদিহি করতে হবে।” — সূরা আল-ইসরা (১৭:৩৪)" else "App hiding and freezing is locked while Protection Mode is active to prevent disabling Blocker.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)",
                        5000,
                        getTamperSubheading(),
                        getUnderstoodButton()
                    )
                }
                return true
            }
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
        if (!shieldEngaged() && !BlockerStore.studyActive(this) && !BlockerStore.focusActive(this)) return
        if (pkg.isEmpty() || !isSettingsPkg(pkg)) return

        main.post {
            val root = runCatching { rootInActiveWindow }.getOrNull() ?: return@post
            try {
                val rPkg = root.packageName?.toString().orEmpty()
                if (rPkg.isNotEmpty() && !isSettingsPkg(rPkg)) return@post
                if (!matchesBlockerAppInfo(root, deep)) return@post
                val activePkg = rPkg.ifEmpty { pkg }
                if (isActualSettingsPkg(activePkg)) {
                    kickFromAppInfo()
                } else {
                    autoCancelDialog(root)
                    pollKick("Blocker cannot be uninstalled while Protection Mode is active.")
                }
                val now = SystemClock.uptimeMillis()
                synchronized(appInfoLock) {
                    appInfoHotUntil = now + 400L
                    appInfoFreshUntil = 0L
                }
            } finally {
                runCatching { root.recycle() }
            }
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
        if (isLegitimatePermissionScreen(null, t)) return false

        val isOurApp = name.lowercase() in t || "blocker" in t || packageName.lowercase() in t
        if (!isOurApp) return false
        val isUninstallDialog = ("do you want to uninstall" in t || "uninstall this app" in t) &&
            "force stop" !in t && "storage" !in t
        if (isUninstallDialog) return false
        val isUnknownApps = ("install unknown" in t || "unknown apps" in t || "allow from this source" in t) &&
            "force stop" !in t && "clear" !in t && "storage" !in t && "uninstall" !in t
        if (isUnknownApps) return false
        if (isInstallOrUpdateDialog(root = root)) return false

        val hasDestructiveAction = "force stop" in t || "uninstall" in t || "disable" in t ||
            "clear storage" in t || "clear data" in t || "manage space" in t || "storage & cache" in t
        if (!hasDestructiveAction) return false

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
            if (sep == '\n' || sep == '\r' || sep == '·' || sep == '•' || sep == '-' || sep == '(' || sep == ':' || sep == ',' || sep == '.') {
                return true
            }
            if (sep == ' ') {
                val rem = t.substring(name.length).trim()
                if (rem.isEmpty() || containsVersion(rem, ver) ||
                    rem.matches(Regex("""^(v?\d+(\.\d+)*|[0-9.]+\s*(kb|mb|gb|b)|installed|app|package|double).*""", RegexOption.IGNORE_CASE))) {
                    return true
                }
            }
        }
        return false
    }

    private fun kickFromAppInfo() {
        val now = System.currentTimeMillis()
        viewingBlockerSettingsUntil = now + 30_000L             // arms the existing uninstall / clear-data dialog catchers
        runCatching { autoCancelDialog(rootInActiveWindow) }
        performGlobalAction(GLOBAL_ACTION_BACK)
        kickToHome()

        if (now - lastAppInfoKick < 400L) return
        lastAppInfoKick = now

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
            getTamperHeading(),
            if (isBangla()) "সুরক্ষা মোড সক্রিয় থাকা অবস্থায় ব্লকারের অ্যাপ ইনফো পেজ লক করা থাকে।\n\n“এবং অঙ্গীকার পূর্ণ করো। নিশ্চয়ই অঙ্গীকার সম্পর্কে জবাবদিহি করতে হবে।” — সূরা আল-ইসরা (১৭:৩৪)" else "Blocker's app info page is locked while Protection Mode is active.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)",
            5000,
            getTamperSubheading(),
            getUnderstoodButton()
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
            showOverlay(getTamperHeading(), formatTamperCovenantBody("USB & Wireless debugging are locked while Protection Mode is active."), 5000, getTamperSubheading(), getUnderstoodButton())
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
            showOverlay(getTamperHeading(), formatTamperCovenantBody("USB & Wireless debugging were switched off. They stay locked while Protection Mode is active."), 5000, getTamperSubheading(), getUnderstoodButton())
        }
    }

    /** One tick: find the "USB debugging" or "Wireless debugging" row (scrolling if needed) and tap it. Success is detected by the setting observer. */
    private fun stepAdbUiFix() {
        val root = rootInActiveWindow ?: return
        try {
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
                val nodes = root.findAccessibilityNodeInfosByText(label)
                val found = nodes?.firstOrNull { it.text?.toString()?.trim().equals(label, ignoreCase = true) }
                if (nodes != null) {
                    for (n in nodes) {
                        if (n != found) runCatching { n.recycle() }
                    }
                }
                if (found != null) {
                    matchedRow = found
                    break
                }
            }

            if (matchedRow == null) {
                if (adbFixScrolls < ADB_FIX_MAX_SCROLLS) {
                    val scroller = findScrollable(root, 0, intArrayOf(150))
                    if (scroller != null) {
                        try {
                            if (scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) adbFixScrolls++
                        } finally {
                            runCatching { scroller.recycle() }
                        }
                    }
                }
                return
            }

            try {
                val now = SystemClock.uptimeMillis()
                if (now - lastAdbFixClick < ADB_FIX_CLICK_GAP_MS) return    // give the switch time to flip before judging

                // First attempt: try finding a Switch widget in the same row container (up to 3 levels up)
                var switchNode: AccessibilityNodeInfo? = null
                var container: AccessibilityNodeInfo? = matchedRow
                var depth = 0
                while (container != null && depth++ < 3) {
                    switchNode = findSwitchInNode(container)
                    if (switchNode != null) break
                    val parent = container.parent
                    if (container != matchedRow) runCatching { container?.recycle() }
                    container = parent
                }
                if (container != null && container != matchedRow) runCatching { container.recycle() }

                if (switchNode != null && switchNode.isClickable) {
                    lastAdbFixClick = now
                    switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    runCatching { switchNode.recycle() }
                    return
                }

                // Fallback: click the clickable parent of the row or the switch's parent
                var target: AccessibilityNodeInfo? = switchNode ?: matchedRow
                var hops = 0
                while (target != null && !target.isClickable && hops++ < 6) {
                    val parent = target.parent
                    if (target != switchNode && target != matchedRow) runCatching { target?.recycle() }
                    target = parent
                }
                if (target == null) return
                lastAdbFixClick = now
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (target != switchNode && target != matchedRow) runCatching { target.recycle() }
                if (switchNode != null && switchNode != target) runCatching { switchNode.recycle() }
            } finally {
                runCatching { matchedRow.recycle() }
            }
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun findSwitchInNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val cls = node.className?.toString() ?: ""
        if (node.isCheckable || cls.contains("Switch", ignoreCase = true) || cls.contains("CompoundButton", ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val s = findSwitchInNode(child)
            if (s != null) return s
            runCatching { child.recycle() }
        }
        return null
    }

    private fun findScrollable(n: AccessibilityNodeInfo?, depth: Int, budget: IntArray): AccessibilityNodeInfo? {
        if (n == null || depth > 16 || budget[0]-- <= 0) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val child = runCatching { n.getChild(i) }.getOrNull() ?: continue
            val found = findScrollable(child, depth + 1, budget)
            if (found != null) return found
            runCatching { child.recycle() }
        }
        return null
    }

    private fun lockOutOfSettings(message: String = "Device Admin & app settings for Blocker are locked while Protection Mode is active.") {
        val now = System.currentTimeMillis()
        if (now - lastHit < 1000L) return
        lastHit = now
        val root = runCatching { rootInActiveWindow }.getOrNull()
        try {
            val rootPkg = root?.packageName?.toString().orEmpty()
            if (rootPkg.isNotEmpty() && isActualSettingsPkg(rootPkg)) {
                kickFromAppInfo()
                return
            }
            runCatching { autoCancelDialog(root) }
            kickToHome()
            BlockerStore.incr(this, "tamper")
            showOverlay(getTamperHeading(), formatTamperCovenantBody(message), 5000, getTamperSubheading(), getUnderstoodButton())
        } finally {
            runCatching { root?.recycle() }
        }
    }

    // ---------- Granular focus interception (Reels / Shorts / Search) ----------

    private fun checkGranularInterception(pkg: String, ev: AccessibilityEvent): Boolean {
        if (!BlockerStore.active(this) && !BlockerStore.focusActive(this)) return false
        val now = System.currentTimeMillis()
        if (now - (lastGranularHit[pkg] ?: 0L) < 700L) return true

        val root = rootInActiveWindow ?: return false
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
                    exitSubFeature(pkg, "Facebook Reels")
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
                    try {
                        val stillInSubFeature = when (pkg) {
                            "com.facebook.katana", "com.facebook.lite" -> isFacebookVideoOrReels(r, null)
                            "com.google.android.youtube" -> hasAnyNodeId(r, YT_SHORTS_PLAYER_IDS) || isTabSelectedById(r, "com.google.android.youtube:id/pivot_shorts")
                            "com.instagram.android" -> hasAnyNodeId(r, INSTA_REELS_PLAYER_IDS) || isTabSelectedById(r, "com.instagram.android:id/clips_tab")
                            else -> false
                        }
                        if (stillInSubFeature) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                    } finally {
                        runCatching { r.recycle() }
                    }
                }
            }
        }, 350L)
    }

    private fun isFacebookVideoOrReels(root: AccessibilityNodeInfo, ev: AccessibilityEvent?): Boolean {
        // 1. Check dedicated Reels or Video tab in navigation bar (only when actually selected)
        val isReelsOrVideoTab = isTabSelectedById(root, "com.facebook.katana:id/reels_tab") ||
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
        if (isReelsOrVideoTab) return true

        // 2. Check full-screen / immersive Video or Reels player IDs
        val isDedicatedVideoViewer = hasAnyNodeId(root, FB_VIDEO_REELS_PLAYER_IDS)

        // 3. Check Window / Event / View class names
        val evCls = ev?.className?.toString().orEmpty().lowercase()
        val rootCls = root.className?.toString().orEmpty().lowercase()
        val isVideoClass = evCls.contains("channelfeed") || rootCls.contains("channelfeed") ||
            evCls.contains("warion") || rootCls.contains("warion") ||
            evCls.contains("fullscreenvideo") || rootCls.contains("fullscreenvideo") ||
            evCls.contains("fbshorts") || rootCls.contains("fbshorts") ||
            evCls.contains("reelsviewer") || rootCls.contains("reelsviewer") ||
            evCls.contains("reelwatch") || rootCls.contains("reelwatch") ||
            evCls.contains("unifiedvideoviewer") || rootCls.contains("unifiedvideoviewer")

        // 4. Content / Text inspection of active window
        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(250))
        val t = sb.toString().lowercase()

        // Distinguish active newsfeed header vs video player:
        val hasNewsfeedHeader = ("what's on your mind" in t || "what’s on your mind" in t) ||
            ("stories" in t && ("create story" in t || "add to story" in t || "your story" in t)) ||
            ("search on facebook" in t || "search facebook" in t)

        // If the user is visibly on the main newsfeed header/composer and NOT in a dedicated video viewer,
        // NEVER exit! Let the user browse their feed normally without interrupting.
        if (hasNewsfeedHeader && !isDedicatedVideoViewer && !isVideoClass) {
            return false
        }

        if (isDedicatedVideoViewer || isVideoClass) return true

        // 5. Fallback UI inspection for full-screen video / reels viewer (Litho / custom TalkBack nodes):
        // Matches the immersive video player shown in full-screen video & reels
        val hasVideoViewerUI = ("add a comment" in t && (
            "replay 5" in t || "rewind 5" in t || "5 seconds" in t || "seek" in t ||
            "follow for more" in t || " · origi" in t || "original audio" in t || "original sound" in t ||
            "play video" in t || "pause video" in t || "share" in t
        )) ||
        ("swipe up for next" in t || "swipe up for more" in t || "watch more videos" in t || "watch more reels" in t) ||
        ("remix this reel" in t || "remix reel" in t || "reels audio" in t || "share this reel" in t)

        return hasVideoViewerUI
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
        val isSelected = n.isSelected || cd?.contains("selected") == true || text?.contains("selected") == true
        if (isSelected) {
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
        if (n == null || depth > 10 || budget[0]-- <= 0 || sb.length >= MAX_SCAN_CHARS) return
        if (!n.isPassword) {
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
        }
        val count = n.childCount
        for (i in 0 until count) {
            if (budget[0] <= 0 || sb.length >= MAX_SCAN_CHARS) break
            val c = n.getChild(i) ?: continue
            collectText(c, sb, depth + 1, budget)
            recycleNode(c)
        }
    }

    // ---------- Bilingual Popup Messaging (English / বাংলা) ----------

    private fun isBangla(): Boolean = BlockerStore.popupLanguage(this) == "bn"

    private fun getFearAllahHeading(): String =
        if (isBangla()) "আল্লাহকে ভয় করুন" else "Fear Allah"

    private fun getFearAllahSubheading(): String =
        if (isBangla()) "আপনার আখিরাত ধ্বংস করবেন না" else "Do not destroy your Akhirah"

    private fun getTamperHeading(): String =
        if (isBangla()) "🔐 নিরাপত্তা সুরক্ষা" else "🔐 Tamper Protection"

    private fun getTamperSubheading(): String =
        if (isBangla()) "আল্লাহকে ভয় করুন এবং অবিচল থাকুন" else "Fear Allah and remain steadfast"

    private fun getProtectionActiveSubheading(): String =
        if (isBangla()) "সুরক্ষা সক্রিয় রয়েছে" else "Protection Active"

    private fun getUnderstoodButton(): String =
        if (isBangla()) "বুঝেছি" else "Understood"

    private fun getAstaghfirullahButton(): String =
        if (isBangla()) "আস্তাগফিরুল্লাহ" else "Astaghfirullah"

    private fun formatBlockedAppBody(pkg: String): String {
        val app = labelOf(pkg)
        return if (isBangla()) {
            "$app আপনার ব্লক তালিকায় রয়েছে। নিজের সময় ও দ্বীনকে রক্ষা করুন।\n\n“নিশ্চয়ই কান, চোখ এবং অন্তর — এদের প্রত্যেকটি সম্পর্কেই তোমাদেরকে জবাবদিহি করতে হবে।”\n— সূরা আল-ইসরা (১৭:৩৬)"
        } else {
            "$app is on your block list. Guard your time and deen.\n\n“Indeed, the hearing, the sight and the heart — about all of these you will be questioned.”\n— Surah Al-Isra (17:36)"
        }
    }

    private fun formatBlockedUrlBody(rule: String): String {
        return if (isBangla()) {
            "আপনার লজ্জা ও পবিত্রতা রক্ষার জন্য “$rule” ব্লক করা হয়েছে।\n\n“সে কি জানে না যে, নিশ্চয়ই আল্লাহ সবকিছু দেখেন?”\n— সূরা আল-আলাক (৯৬:১৪)"
        } else {
            "“$rule” was blocked to safeguard your modesty and purity.\n\n“Does he not know that Allah sees?”\n— Surah Al-Alaq (96:14)"
        }
    }

    private fun formatScreenHitBrowserBody(): String {
        return if (isBangla()) {
            "অনুপযুক্ত কনটেন্ট শনাক্ত হওয়ায় এটি বন্ধ করা হয়েছে।\n\n“মুমিন পুরুষদের বলুন, তারা যেন তাদের দৃষ্টি সংযত রাখে এবং নিজেদের লজ্জাস্থানের হেফাজত করে। এটি তাদের জন্য অধিকতর পবিত্র। নিশ্চয়ই তারা যা করে আল্লাহ সে বিষয়ে সম্যক অবগত।”\n— সূরা আন-নূর (২৪:৩০)"
        } else {
            "Inappropriate content was detected and closed.\n\n“Tell the believing men to lower their gaze and guard their modesty. That is purer for them. Indeed, Allah is aware of what they do.”\n— Surah An-Nur (24:30)"
        }
    }

    private fun formatScreenHitOtherAppBody(pkg: String): String {
        val app = labelOf(pkg)
        return if (isBangla()) {
            "$app অ্যাপে অনুপযুক্ত কনটেন্ট প্রদর্শিত হওয়ায় এটি বন্ধ করা হয়েছে।\n\n“নিশ্চয়ই আল্লাহ তোমাদের ওপর সর্বদা সতর্ক দৃষ্টি রাখেন।”\n— সূরা আন-নিসা (৪:১)"
        } else {
            "Inappropriate content appeared in $app, so it was closed.\n\n“Indeed, Allah is ever, over you, an Observer.”\n— Surah An-Nisa (4:1)"
        }
    }

    private fun formatTamperCovenantBody(baseMsg: String): String {
        val bnMsg = if (baseMsg.contains("Device Admin", ignoreCase = true)) {
            "ব্লকারের ডিভাইস অ্যাডমিন ও অ্যাপ সেটিংস সুরক্ষা মোড সক্রিয় থাকা অবস্থায় লক করা থাকে।"
        } else if (baseMsg.contains("USB", ignoreCase = true)) {
            "সুরক্ষা মোড সক্রিয় থাকা অবস্থায় ইউএসবি ও ওয়্যারলেস ডিবাগিং লক করা থাকে।"
        } else {
            baseMsg
        }
        return if (isBangla()) {
            "$bnMsg\n\n“এবং অঙ্গীকার পূর্ণ করো। নিশ্চয়ই অঙ্গীকার সম্পর্কে জবাবদিহি করতে হবে।” — সূরা আল-ইসরা (১৭:৩৪)"
        } else {
            "$baseMsg\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)"
        }
    }

    private fun formatDateTimeLockedBody(): String {
        return if (isBangla()) {
            "ঘড়ির সময় পরিবর্তন করে সুরক্ষা ফাঁকি দেওয়া রোধ করতে তারিখ ও সময় সেটিংস লক করা রয়েছে।"
        } else {
            "Date & Time settings are locked while protection is active to prevent clock tampering."
        }
    }

    private fun formatScheduledPowerLockedBody(): String {
        return if (isBangla()) {
            "সুরক্ষা সক্রিয় থাকা অবস্থায় স্বয়ংক্রিয় পাওয়ার অফ (নির্ধারিত বন্ধ) বন্ধ রাখা হয়েছে।"
        } else {
            "Scheduled power off is locked while protection is active."
        }
    }

    private fun formatAdbLockedBody(): String {
        return if (isBangla()) {
            "সুরক্ষা মোড সক্রিয় থাকা অবস্থায় ডিবাগিং অনুমোদন লক করা রয়েছে।\n\n“এবং অঙ্গীকার পূর্ণ করো। নিশ্চয়ই অঙ্গীকার সম্পর্কে জবাবদিহি করতে হবে।” — সূরা আল-ইসরা (১৭:৩৪)"
        } else {
            "Debugging authorization is locked while Protection Mode is active.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)"
        }
    }

    private fun formatLauncherPressHoldBody(): String {
        return if (isBangla()) {
            "সুরক্ষা মোড সক্রিয় থাকা অবস্থায় আনইনস্টল রোধ করতে ব্লকারের উপর চেপে ধরা (প্রেস অ্যান্ড হোল্ড) নিষ্ক্রিয় করা রয়েছে।\n\n“এবং অঙ্গীকার পূর্ণ করো। নিশ্চয়ই অঙ্গীকার সম্পর্কে জবাবদিহি করতে হবে।” — সূরা আল-ইসরা (১৭:৩৪)"
        } else {
            "Press & hold is disabled for Blocker to prevent unauthorized uninstallation while Protection Mode is active.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)"
        }
    }

    private fun formatAppHidingBody(): String {
        return if (isBangla()) {
            "ব্লকার নিষ্ক্রিয় করা রোধ করতে অ্যাপ হাইড ও ফ্রিজার সেটিংস লক করা রয়েছে।\n\n“এবং অঙ্গীকার পূর্ণ করো। নিশ্চয়ই অঙ্গীকার সম্পর্কে জবাবদিহি করতে হবে।” — সূরা আল-ইসরা (১৭:৩৪)"
        } else {
            "App hiding is locked while Protection Mode is active to prevent disabling Blocker.\n\n“And fulfill your covenants. Indeed, covenants will be questioned.” — Surah Al-Isra (17:34)"
        }
    }

    private fun formatPowerOffLockedBody(): String {
        return if (isBangla()) {
            "আপনার সুরক্ষার জন্য পাওয়ার অফ এবং রিস্টার্ট লক করা রয়েছে।\n\nএকটু ভাবুন এবং অবিচল থাকুন।"
        } else {
            "Powering off and restarting are locked for your protection.\n\nTake a breather and remain steadfast."
        }
    }

    private fun formatStudyModeBody(phase: String, remainingText: String): String {
        return if (isBangla()) {
            "আপনার পোমোডোরো স্টাডি সেশনের জন্য ফোন লকডাউন সক্রিয় রয়েছে ($phase • $remainingText)।\n\nসেশন শেষ না হওয়া পর্যন্ত অন্য সব অ্যাপ, সেটিংস এবং নোটিফিকেশন বন্ধ থাকবে।\n\n“এমন দুটি নিয়ামত রয়েছে, যে দুটিতে অধিকাংশ মানুষ ক্ষতিগ্রস্ত: সুস্থতা এবং অবসর সময়।”\n— সহীহ আল-বুখারী (৬৪১২)"
        } else {
            "Full phone lockdown is active for your Pomodoro study session ($phase • $remainingText).\n\nAll other apps, settings, quick toggles, and notifications are blocked until you finish.\n\n“The two blessings which many people lose are: health and free time for doing good.”\n— Sahih al-Bukhari"
        }
    }

    private fun formatAppUsageLimitBody(pkg: String, limitStr: String): String {
        val app = labelOf(pkg)
        return if (isBangla()) {
            "$app আপনার দৈনিক ব্যবহারের সীমা $limitStr এ পৌঁছেছে। নিজের সময় ও দ্বীনকে রক্ষা করুন।\n\n“পাঁচটি বিষয়ের পূর্বে পাঁচটি বিষয়ের সদ্ব্যবহার করো: বার্ধক্যের পূর্বে যৌবনের, অসুস্থতার পূর্বে সুস্থতার, দারিদ্র্যের পূর্বে সচ্ছলতার, ব্যস্ততার পূর্বে অবসর সময়ের এবং মৃত্যুর পূর্বে জীবনের।”\n— সহীহ হাদিস (আল-হাকিম)"
        } else {
            "$app has reached your daily usage limit of $limitStr. Guard your time and deen.\n\n“Take advantage of five before five: your youth before your old age, your health before your sickness, your wealth before your poverty, your free time before your busyness, and your life before your death.”\n— Hadith"
        }
    }

    private fun formatFocusModeBody(remainingText: String): String {
        return if (isBangla()) {
            "শুধুমাত্র প্রয়োজনীয় অ্যাপ ব্যবহারের সুযোগ রয়েছে (বাকি সময় $remainingText)।\n\n“পাঁচটি বিষয়ের পূর্বে পাঁচটি বিষয়ের সদ্ব্যবহার করো: বার্ধক্যের পূর্বে যৌবনের, অসুস্থতার পূর্বে সুস্থতার, দারিদ্র্যের পূর্বে সচ্ছলতার, ব্যস্ততার পূর্বে অবসর সময়ের এবং মৃত্যুর পূর্বে জীবনের।”\n— সহীহ হাদিস (আল-হাকিম)"
        } else {
            "Only your essential apps are available ($remainingText remaining).\n\n“Take advantage of five before five: your youth before your old age, your health before your sickness, your wealth before your poverty, your free time before your busyness, and your life before your death.”\n— Hadith"
        }
    }

    private fun hit(
        kind: String,
        heading: String = getFearAllahHeading(),
        msg: String = "",
        subheading: String? = getFearAllahSubheading(),
        targetPkg: String? = null,
        btnText: String? = null
    ) {
        val now = System.currentTimeMillis()

        // 1. Unconditionally kick out of the blocked app (NEVER skip or debounce the kick!)
        performGlobalAction(GLOBAL_ACTION_BACK)
        kickToHome()

        // 2. Terminate background processes of the blocked app so it does not linger in memory/recents
        val killPkg = targetPkg ?: currentFgPkg
        if (!killPkg.isNullOrEmpty() && killPkg != packageName && killPkg !in neverBlock) {
            main.postDelayed({
                runCatching {
                    (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.killBackgroundProcesses(killPkg)
                }
            }, 150L)
        }

        // 3. Debounce visual overlay and stats increment so rapid hits don't flicker or inflate stats
        if (now - lastHit >= 700L) {
            lastHit = now
            BlockerStore.incr(this, kind)
            val btn = btnText ?: getAstaghfirullahButton()
            showOverlay(heading, msg, 5000, subheading, btn)
        }
    }

    // ---------- Popup ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun recycleNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT < 33) {
            runCatching { node.recycle() }
        }
    }

    private var preInflatedOverlay: FrameLayout? = null
    private var preInflatedOverlayLp: WindowManager.LayoutParams? = null
    private var preInflatedBadgeText: TextView? = null
    private var preInflatedBadgeLayout: LinearLayout? = null
    private var preInflatedHeading: TextView? = null
    private var preInflatedSubheading: TextView? = null
    private var preInflatedBody: TextView? = null
    private var preInflatedButton: Button? = null
    private var preInflatedProgressFill: View? = null
    private var preInflatedCard: LinearLayout? = null
    private var isOverlayAttached = false
    private var isOverlayVisible = false

    private fun initPreInflatedOverlay() {
        if (preInflatedOverlay != null) return
        val svc = this
        val accent = 0xFFA78BFA.toInt()
        val accentDeep = 0xFF7C3AED.toInt()

        val container = FrameLayout(svc).apply {
            setBackgroundColor(0xFF070512.toInt())
            setOnClickListener { removeOverlay() }
        }

        val badge = LinearLayout(svc).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0x33DC2626.toInt())
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0x88EF4444.toInt())
            }
        }
        val badgeTv = TextView(svc).apply {
            text = "🛡️"
            textSize = 20f
            gravity = Gravity.CENTER
        }
        badge.addView(badgeTv)

        val headingTv = TextView(svc).apply {
            text = "Fear Allah"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
        }

        val headingRow = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headingRow.addView(badge, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) })
        headingRow.addView(headingTv)

        val subheadingTv = TextView(svc).apply {
            text = ""
            setTextColor(0xFFE9D5FF.toInt())
            textSize = 15.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(2))
            visibility = View.GONE
        }

        val bodyTv = TextView(svc).apply {
            text = ""
            setTextColor(0xFFD1C8EC.toInt())
            textSize = 14.5f
            setPadding(0, dp(12), 0, dp(18))
            setLineSpacing(dp(3).toFloat(), 1.05f)
        }

        val progressTrack = View(svc).apply { setBackgroundColor(0x26FFFFFF) }
        val progressFill = View(svc).apply {
            setBackgroundColor(accent)
            pivotX = 0f
        }
        val trackWrap = FrameLayout(svc)
        trackWrap.addView(progressTrack, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))
        trackWrap.addView(progressFill, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))

        val btn = Button(svc).apply {
            text = "Astaghfirullah"
            setAllCaps(false)
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(accentDeep, accent)
            ).apply { cornerRadius = dp(15).toFloat() }
            stateListAnimator = null
            setOnClickListener { removeOverlay() }
        }

        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF221A3D.toInt(), 0xFF140D26.toInt())
            ).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(2), 0x99C084FC.toInt())
            }
            elevation = dp(18).toFloat()
            isClickable = true
        }

        card.addView(headingRow)
        card.addView(subheadingTv)
        card.addView(bodyTv)
        card.addView(trackWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply {
            bottomMargin = dp(16)
        })
        card.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        val cardLp = FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        container.addView(card, cardLp)

        val rootLp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        container.visibility = View.GONE
        runCatching {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(container, rootLp)
            isOverlayAttached = true
        }

        preInflatedOverlay = container
        preInflatedOverlayLp = rootLp
        preInflatedBadgeText = badgeTv
        preInflatedBadgeLayout = badge
        preInflatedHeading = headingTv
        preInflatedSubheading = subheadingTv
        preInflatedBody = bodyTv
        preInflatedButton = btn
        preInflatedProgressFill = progressFill
        preInflatedCard = card
    }

    private fun showOverlay(
        heading: String,
        body: String,
        ms: Long = 5000L,
        subheading: String? = null,
        buttonText: String = "Astaghfirullah"
    ) {
        val displayMs = ms.coerceAtLeast(5000L)
        val action = Runnable {
            val container = preInflatedOverlay ?: run {
                initPreInflatedOverlay()
                preInflatedOverlay ?: return@Runnable
            }
            val lp = preInflatedOverlayLp ?: return@Runnable
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val isFearAllah = heading == "Fear Allah" || heading == "আল্লাহকে ভয় করুন"

            preInflatedBadgeText?.text = if (isFearAllah) "🛡️" else "🔒"
            preInflatedBadgeLayout?.background = GradientDrawable().apply {
                setColor(if (isFearAllah) 0x33DC2626.toInt() else 0x33A78BFA)
                cornerRadius = dp(22).toFloat()
                if (isFearAllah) setStroke(dp(1), 0x88EF4444.toInt())
            }

            preInflatedHeading?.apply {
                text = heading
                setTextColor(if (isFearAllah) 0xFFFFFFFF.toInt() else 0xFFF6F3FF.toInt())
                textSize = if (isFearAllah) 21f else 18.5f
            }

            if (!subheading.isNullOrBlank()) {
                preInflatedSubheading?.apply {
                    text = subheading
                    visibility = View.VISIBLE
                }
            } else {
                preInflatedSubheading?.visibility = View.GONE
            }

            preInflatedBody?.text = body
            val resolvedButtonText = when {
                isBangla() && (buttonText == "Astaghfirullah" || buttonText == "আস্তাগফিরুল্লাহ") -> "আস্তাগফিরুল্লাহ"
                isBangla() && (buttonText == "Understood" || buttonText == "বুঝেছি") -> "বুঝেছি"
                isBangla() && (buttonText == "Back to Study" || buttonText == "পড়াশোনায় ফিরুন") -> "পড়াশোনায় ফিরুন"
                else -> buttonText
            }
            preInflatedButton?.text = resolvedButtonText

            preInflatedCard?.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF221A3D.toInt(), 0xFF140D26.toInt())
            ).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(2), if (isFearAllah) 0x99C084FC.toInt() else 0x66C7AFFF.toInt())
            }

            lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            runCatching { wm.updateViewLayout(container, lp) }

            container.visibility = View.VISIBLE
            isOverlayVisible = true

            preInflatedProgressFill?.apply {
                scaleX = 1f
                animate().scaleX(0f).setDuration(displayMs).setInterpolator(LinearInterpolator()).start()
            }

            main.removeCallbacks(hideRunnable)
            main.postDelayed(hideRunnable, displayMs)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            main.post(action)
        }
    }

    private fun removeOverlay() {
        main.removeCallbacks(hideRunnable)
        val container = preInflatedOverlay ?: return
        val lp = preInflatedOverlayLp ?: return
        if (!isOverlayVisible && container.visibility == View.GONE) return

        container.visibility = View.GONE
        isOverlayVisible = false

        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        runCatching {
            (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(container, lp)
        }
    }
}