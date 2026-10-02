package com.blocker

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
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
            "developer options", "development settings",
            "usb debugging", "oem unlocking"
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
            "com.facebook.katana:id/unified_video_viewer",
            "com.facebook.katana:id/feed_short_form_video_container",
            "com.facebook.katana:id/fb_shorts_player_fragment",
            "com.facebook.katana:id/warion_root_container",
            "com.facebook.katana:id/rich_video_player",
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
        private val RELOAD_KEYS = setOf("domains", "keywords", "tlds", "whitelist", "apps", "scan_exempt", "focus_apps", "block_fb_reels", "block_insta_reels", "block_insta_search", "block_yt_shorts", "active", "shield", "admin_disable_requested")
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

        // ── Blocker App Info guard (strict 3-keyword match) ──
        // Static OS button labels, matched case-insensitively. Add another UI language here if you ever need one.
        private val APP_INFO_ACTION_TEXTS = listOf("Uninstall", "Force stop")
        // Activity / fragment class-name fragments that announce "this window is an App Info screen"
        private val APP_INFO_CLASS_HINTS = listOf("appinfo", "installedapp", "appdetail", "applicationdetail", "applicationsdetail")
        private const val APP_INFO_FAST_TICK_MS = 35L      // scan cadence right after a window opens
        private const val APP_INFO_SLOW_TICK_MS = 100L     // scan cadence while merely sitting in Settings
        private const val APP_INFO_FRESH_MS = 700L         // how long after an opening the fast cadence lasts
        private const val APP_INFO_HOLD_OPEN_MS = 2_500L   // keep scanning this long after a window opens
        private const val APP_INFO_HOLD_ACTIVE_MS = 600L   // ...and this long after any other Settings activity
        private const val APP_INFO_DEEP_EVERY = 6          // every Nth scan also allows the fallback walk
        private const val APP_INFO_KICK_GAP_MS = 500L      // min gap between popup/stat updates
        private const val APP_INFO_WALK_BUDGET = 200       // max nodes the fallback walk will ever visit
        private const val APP_INFO_WALK_DEPTH = 25
        private const val APP_INFO_MAX_TEXT = 120          // longer strings can't be a name / version / button label
        private const val BIT_VERSION = 1
        private const val BIT_ACTION = 2
        private const val BIT_NAME = 4
        private const val BITS_ALL = BIT_VERSION or BIT_ACTION or BIT_NAME

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
    private var lastPollKick = 0L
    private var outsideSettingsCount = 0

    // App Info guard state. Scanning runs on its own thread so it never queues behind the main-thread
    // handlers (which can spend 100+ ms walking Settings trees); everything shared is @Volatile or locked.
    @Volatile private var appLabel: String = ""          // what Settings shows as the app's name
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

    fun kickToHomeAndCancel() {
        main.post {
            runCatching { autoCancelDialog(rootInActiveWindow) }
            kickToHome()
        }
    }

    private fun autoCancelDialog(root: android.view.accessibility.AccessibilityNodeInfo?) {
        if (root == null) return
        val cancelBtn = root.findAccessibilityNodeInfosByViewId("android:id/button2").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Cancel").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("cancel").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("CANCEL").firstOrNull()
        cancelBtn?.let {
            it.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            it.recycle()
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
        runCatching { autoCancelDialog(rootInActiveWindow) }
        kickToHome()
        val now = System.currentTimeMillis()
        if (now - lastPollKick < 500) return
        lastPollKick = now
        BlockerStore.incr(this, "tamper")
        showOverlay("🔐 Tamper Protection", message, 5000)
    }

    /**
     * Called every ~150 ms while the foreground window belongs to a settings package.
     * Scans the root node tree directly — no event timing dependency.
     */
    private fun performSettingsScreenGuard(root: android.view.accessibility.AccessibilityNodeInfo) {
        val rootPkg = root.packageName?.toString().orEmpty()
        if (rootPkg == packageName) return
        if (rootPkg.isNotEmpty() && !isSettingsPkg(rootPkg) && !isInstallerPkg(rootPkg) && rootPkg != "android") return

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
        val hasDevMarker = "usb debugging" in t || "wireless debugging" in t || "oem unlocking" in t ||
            "logger buffer" in t || "stay awake" in t || "desktop backup password" in t
        if (hasDevTitle || hasDevMarker) {
            pollKick("Developer options are locked while Protection Mode is active.")
            return
        }

        // Blocker-specific: kick the instant ANY settings screen mentions our app.
        val isOurApp = "blocker" in t || packageName.lowercase() in t

        // ── Uninstall attempt in settings / package manager ──
        // Also fires without "blocker" text present, as long as we recently saw Blocker-specific
        // content (viewingBlockerSettingsUntil) — the generic system uninstall confirmation doesn't
        // always repeat the app name where our text scan can see it.
        val nowForUninstall = System.currentTimeMillis()
        val hasUninstallRisk = ("uninstall" in t || "do you want to uninstall" in t) &&
            (isOurApp || nowForUninstall < viewingBlockerSettingsUntil)
        if (hasUninstallRisk) {
            viewingBlockerSettingsUntil = nowForUninstall + 30_000L
            autoCancelDialog(root)
            pollKick("Blocker cannot be uninstalled while Protection Mode is active.")
            return
        }

        // Blocker-specific: kick the instant ANY settings screen mentions our app.
        // This covers App Info, Storage, Force Stop, Uninstall — all in one sweep.
        if (isOurApp) {
            viewingBlockerSettingsUntil = System.currentTimeMillis() + 30_000L
            autoCancelDialog(root)
            pollKick("Blocker app settings and storage are locked while Protection Mode is active.")
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
                pollKick("App storage for Blocker is locked while Protection Mode is active.")
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        loadAppIdentity()
        neverBlock = neverBlock + launcherPackages() + cameraPackages()
        reload()
        BlockerStore.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        BlockerStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
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
        return lower.contains("packageinstaller") || lower.contains("packagemanager")
    }

    private fun isSettingsPkg(pkg: String): Boolean {
        if (pkg in SETTINGS_PKGS || pkg in discoveredSettingsPkgs) return true
        val lower = pkg.lowercase()
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

    private fun reload() {
        discoveredSettingsPkgs = settingsPackages()
        neverBlock = setOf("com.android.systemui", "android") + launcherPackages() + cameraPackages()
        domains = BlockerStore.set(this, "domains")
        keywords = BlockerStore.set(this, "keywords")
        tlds = BlockerStore.set(this, "tlds")
        whitelist = BlockerStore.set(this, "whitelist")
        apps = BlockerStore.set(this, "apps")
        exempt = BlockerStore.set(this, "scan_exempt")
        focusAllowed = BlockerStore.focusApps(this) + neverBlock + FOCUS_SYSTEM_PKGS + imePackages() + packageName
        blockFbReels = BlockerStore.granularToggle(this, "block_fb_reels")
        blockInstaReels = BlockerStore.granularToggle(this, "block_insta_reels")
        blockInstaSearch = BlockerStore.granularToggle(this, "block_insta_search")
        blockYtShorts = BlockerStore.granularToggle(this, "block_yt_shorts")
    }

    private fun imePackages(): Set<String> = runCatching {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val ev = e ?: return
        val pkg = ev.packageName?.toString() ?: return
        if (pkg == packageName) {
            viewingBlockerSettingsUntil = 0L
            settingsPollActive = false
            outsideSettingsCount = 0
            main.removeCallbacks(settingsPollRunnable)
            return
        }
        if (checkAdbDialog(pkg)) return
        // Strict App Info guard runs BEFORE the neverBlock bail-out: some OEMs host app-info pages inside
        // packages that also answer the HOME intent, which neverBlock would otherwise skip entirely.
        guardBlockerAppInfo(ev, pkg)
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
            //    Titles like "App info", "Application info", app name on detail screen
            val isAppInfoTitle = "app info" in winTitle || "application info" in winTitle ||
                "application details" in winTitle || "app details" in winTitle
            val isAppInfoCls   = evCls in APP_INFO_CLASSES || rootCls in APP_INFO_CLASSES ||
                evCls.contains("AppInfo", ignoreCase = true) || rootCls.contains("AppInfo", ignoreCase = true) ||
                evCls.contains("InstalledApp", ignoreCase = true) || rootCls.contains("InstalledApp", ignoreCase = true)

            if (isAppInfoTitle || isAppInfoCls) {
                // Scan the window content (limited budget) for Blocker's name/package
                val root = rootInActiveWindow
                if (root != null) {
                    val sb = StringBuilder()
                    collectText(root, sb, 0, intArrayOf(200))
                    val txt = sb.toString().lowercase()
                    if ("blocker" in txt || packageName.lowercase() in txt) {
                        viewingBlockerSettingsUntil = now + 30_000L
                        lockOutOfSettings("Blocker app settings are locked while Protection Mode is active.")
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
        }

        when {
            pkg in apps -> {
                if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    hit("apps", "🚫 App blocked", "This app is on your block list.")
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
        if (pkg !in exempt) scanScreen(pkg)
    }

    // ---------- Focus mode ----------

    /** During focus mode everything outside the essential apps / system helpers is kicked to the home screen. */
    private fun focusBlocks(ev: AccessibilityEvent, pkg: String): Boolean {
        if (pkg in focusAllowed) return false
        val trusted = ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            rootInActiveWindow?.packageName?.toString() == pkg
        if (!trusted) return false
        val now = System.currentTimeMillis()
        if (now - lastHit >= 700) {
            lastHit = now
            BlockerStore.incr(this, "focus")
            performGlobalAction(GLOBAL_ACTION_HOME)
            showOverlay("🎯 Focus mode", "Only your essential apps are available.\n${leftText()} left.", 3000)
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
                redirectBrowser(pkg, "sites", "🚫 Site blocked", "\"$rule\" is on your block list, so Blocker redirected this tab.")
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
     * Supports both single-word and multi-word keywords (e.g. "usb debugging", "oem unlocking").
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

        // 1. Check multi-word keywords first (e.g. "usb debugging", "oem unlocking")
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
            showOverlay("🛡️ Page redirected", "\"$kw\" appeared on this page, so Blocker redirected it.", 5000)
            return
        }
        BlockerStore.incr(this, "screen")
        performGlobalAction(GLOBAL_ACTION_HOME)
        showOverlay("🛡️ App closed", "\"$kw\" appeared on screen in ${labelOf(pkg)}, so it was closed.", 5000)
    }

    /** Sends the current tab straight to google.com. Browsers are never locked or held open. */
    private fun redirectBrowser(pkg: String, kind: String, heading: String, body: String) {
        quietUntil[pkg] = System.currentTimeMillis() + 2000
        BlockerStore.incr(this, kind)
        if (Build.VERSION.SDK_INT < 30 || !goToGoogle(pkg)) performGlobalAction(GLOBAL_ACTION_BACK)
        showOverlay(heading, body, 5000)
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
        if (pkg != "com.android.systemui" && pkg != "android") return false
        val root = rootInActiveWindow ?: return false
        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(100))
        val t = sb.toString().lowercase()
        if ("allow usb debugging" in t || ("usb debugging" in t && ("fingerprint" in t || "rsa" in t || "always allow" in t))) {
            val now = System.currentTimeMillis()
            if (now - lastHit < 700) return true
            lastHit = now
            BlockerStore.incr(this, "tamper")
            val cancelBtn = root.findAccessibilityNodeInfosByViewId("android:id/button2").firstOrNull()
                ?: root.findAccessibilityNodeInfosByText("Cancel").firstOrNull()
            if (cancelBtn != null) {
                cancelBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cancelBtn.recycle()
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            showOverlay("🔐 Tamper Protection", "USB debugging authorization is locked while Protection Mode is active.", 5000)
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

        // 4. App Info screen for Blocker (Open / Uninstall / Force stop / Storage)
        val isAppInfoScreen = isOurApp && (
            ("force stop" in t && ("uninstall" in t || "open" in t || "storage" in t)) ||
            ("notifications" in t && "permissions" in t && "storage" in t) ||
            ("mobile data" in t && "battery" in t)
        )
        if (isAppInfoScreen) {
            viewingBlockerSettingsUntil = now + 8_000L
            autoCancelDialog(root)
            lockOutOfSettings("Blocker app settings are locked while Protection Mode is active.")
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

        val hasUniqueDevMarker = "usb debugging" in fullText ||
            "revoke usb debugging" in fullText ||
            "wireless debugging" in fullText ||
            "oem unlocking" in fullText ||
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
        if (isOurApp && (hasRiskAction || "storage" in fullText || "app info" in fullText || "installed" in fullText || "version" in fullText)) {
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

    // ---------- Blocker App Info guard (strict 3-keyword match) ----------
    //
    // Closes the Settings > Apps > App management > App list > Blocker route (the page with Open / Uninstall /
    // Force stop). The page is recognised only when ALL THREE are on screen at the same moment:
    //   1. the app's name     - resolved at runtime from the app's own label (= @string/app_name)
    //   2. the app's version  - resolved at runtime from PackageInfo.versionName (what Settings itself prints)
    //   3. an action label    - "Uninstall" or "Force stop" (static OS strings)
    // Name + version + action never occur together on any other screen, including other apps' App Info pages.
    //
    // Latency model: the accessibility callback only ARMS a scanner (a few field writes, no IPC). The scanner
    // runs on its own HandlerThread, so it is never stuck behind the heavier main-thread Settings handlers, and
    // polls every 35 ms for the first 700 ms after a window opens (100 ms afterwards) instead of waiting for
    // the next event. Each poll is ONE targeted findAccessibilityNodeInfosByText lookup for the usual non-match
    // (3-4 worst case). A bounded early-exit walk is kept as a fallback for screens whose framework doesn't
    // answer text lookups (e.g. Compose) and only runs on every 6th poll.

    private fun shieldEngaged(): Boolean =
        BlockerStore.active(this) && BlockerStore.shield(this) && !BlockerStore.guardOpen(this, "shield")

    @Suppress("DEPRECATION")
    private fun loadAppIdentity() {
        appLabel = runCatching { applicationInfo.loadLabel(packageManager).toString().trim() }.getOrDefault("")
        appVersionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty().trim()
        }.getOrDefault("")
    }

    private fun looksLikeAppInfoClass(cls: CharSequence?): Boolean {
        val c = cls?.toString()?.lowercase() ?: return false
        return APP_INFO_CLASS_HINTS.any { it in c }
    }

    /** Window title (event text) equal to the app's name - the title bar of Blocker's own App Info page. */
    private fun titleIsAppName(ev: AccessibilityEvent): Boolean {
        val label = appLabel
        if (label.isEmpty()) return false
        for (t in ev.text) if (t != null && t.toString().trim().equals(label, ignoreCase = true)) return true
        return false
    }

    /** Accessibility-callback side: IPC-free gates, then arm the scanner. Never blocks, never scans. */
    private fun guardBlockerAppInfo(ev: AccessibilityEvent, pkg: String) {
        val stateChange = ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!stateChange && ev.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if (pkg == "com.android.systemui" || pkg == "android") return

        val nowUp = SystemClock.uptimeMillis()
        val announced = stateChange && (looksLikeAppInfoClass(ev.className) || titleIsAppName(ev))
        if (announced) {
            appInfoWatchPkg = pkg                      // e.g. an OEM hosting App Info inside a non-Settings package
            appInfoWatchUntil = nowUp + 5_000L
        }
        val settingsLike = (isSettingsPkg(pkg) || isInstallerPkg(pkg)) && pkg !in neverBlock
        val watched = pkg == appInfoWatchPkg && nowUp < appInfoWatchUntil
        if (!settingsLike && !watched) return
        if (!shieldEngaged()) return

        if (settingsLike) startSettingsPoll()
        armAppInfoScan(pkg, fresh = stateChange, holdMs = if (stateChange) APP_INFO_HOLD_OPEN_MS else APP_INFO_HOLD_ACTIVE_MS)
    }

    /** Extends the scanner's active window (starting its thread / loop if needed). Cheap and thread-safe. */
    private fun armAppInfoScan(pkg: String, fresh: Boolean, holdMs: Long) {
        val now = SystemClock.uptimeMillis()
        synchronized(appInfoLock) {
            appInfoCandidatePkg = pkg
            if (now + holdMs > appInfoHotUntil) appInfoHotUntil = now + holdMs
            if (fresh && now + APP_INFO_FRESH_MS > appInfoFreshUntil) appInfoFreshUntil = now + APP_INFO_FRESH_MS
            if (appInfoLoopRunning) return
            val h = appInfoBg ?: Handler(HandlerThread("BlockerAppInfoGuard").also {
                it.start()
                appInfoThread = it
            }.looper).also { appInfoBg = it }
            appInfoLoopRunning = true
            h.post(appInfoLoop)
        }
    }

    /** One scan of the active window (scanner thread). */
    private fun scanAppInfoOnce(pkg: String, deep: Boolean) {
        if (pkg.isEmpty()) return
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        if (root.packageName?.toString() != pkg) return
        if (!matchesBlockerAppInfo(root, deep)) return
        kickFromAppInfo()
        val now = SystemClock.uptimeMillis()
        synchronized(appInfoLock) {
            // keep watching briefly (at the relaxed cadence) in case HOME didn't take, then go idle
            appInfoHotUntil = now + 400L
            appInfoFreshUntil = 0L
        }
    }

    /**
     * The 3-keyword test. Cheapest/rarest lookup first, so ordinary screens leave after a single lookup.
     * [deep] additionally allows the bounded walk (used for post-open settle passes only).
     */
    private fun matchesBlockerAppInfo(root: AccessibilityNodeInfo, deep: Boolean): Boolean {
        val name = appLabel
        if (name.isEmpty()) return false
        val ver = appVersionName      // empty only if PackageManager failed; then name + action must suffice
        return runCatching { fastMatch(root, name, ver) || (deep && walkMatch(root, name, ver)) }.getOrDefault(false)
    }

    private fun fastMatch(root: AccessibilityNodeInfo, name: String, ver: String): Boolean {
        if (ver.isNotEmpty() && !anyNodeMatches(root, ver) { containsVersion(it, ver) }) return false
        if (!APP_INFO_ACTION_TEXTS.any { a -> anyNodeMatches(root, a) { it.contains(a, ignoreCase = true) } }) return false
        return anyNodeMatches(root, name) { isAppNameText(it, name, ver) }
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
        val found = intArrayOf(if (ver.isEmpty()) BIT_VERSION else 0)
        return walkAppInfo(root, 0, intArrayOf(APP_INFO_WALK_BUDGET), found, name, ver)
    }

    /** Depth-first, node-budgeted, allocation-light; stops the moment all three keywords have been seen. */
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
        if (ver.isNotEmpty() && containsVersion(s, ver)) bits = bits or BIT_VERSION
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

    /** Exact name (so "Ad Blocker" never matches), or "Name<sep>...version..." when a skin puts both in one node. */
    private fun isAppNameText(text: String, name: String, ver: String): Boolean {
        val t = text.trim()
        if (t.equals(name, ignoreCase = true)) return true
        return ver.isNotEmpty() && t.length > name.length && t.startsWith(name, ignoreCase = true) &&
            !t[name.length].isLetterOrDigit() && containsVersion(t, ver)
    }

    private fun kickFromAppInfo() {
        performGlobalAction(GLOBAL_ACTION_HOME)                 // instant: nothing runs before it
        val now = System.currentTimeMillis()
        viewingBlockerSettingsUntil = now + 30_000L             // arms the existing uninstall / clear-data dialog catchers
        if (now - lastAppInfoKick < APP_INFO_KICK_GAP_MS) return
        lastAppInfoKick = now
        Log.i("BlockerGuard", "App Info screen blocked (pkg=$appInfoCandidatePkg)")
        BlockerStore.incr(this, "tamper")
        showOverlay("🔐 Tamper Protection", "Blocker's app info page is locked while Protection Mode is active.", 5000)
    }

    private fun lockOutOfSettings(message: String = "Device Admin & app settings for Blocker are locked while Protection Mode is active.") {
        runCatching { autoCancelDialog(rootInActiveWindow) }
        kickToHome()
        val now = System.currentTimeMillis()
        if (now - lastHit < 500) return
        lastHit = now
        BlockerStore.incr(this, "tamper")
        showOverlay("🔐 Tamper Protection", message, 5000)
    }

    // ---------- Granular focus interception (Reels / Shorts / Search) ----------

    private fun checkGranularInterception(pkg: String, ev: AccessibilityEvent): Boolean {
        if (!BlockerStore.active(this) && !BlockerStore.focusActive(this)) return false
        val now = System.currentTimeMillis()
        if (now - (lastGranularHit[pkg] ?: 0L) < 700L) return true

        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != pkg) return false

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
                // 1. Check Reels tab in navigation bar (only when selected)
                val isReelsTab = isTabSelectedById(root, "com.facebook.katana:id/reels_tab") ||
                    scanSelectedTab(root, listOf("reels", "facebook reels"))
                // 2. Check full-screen Reels viewer (e.g. clicked from newsfeed)
                val isReelsPlayer = hasAnyNodeId(root, FB_REELS_PLAYER_IDS) ||
                    evCls.contains("fbshorts") || evCls.contains("reelsviewer") ||
                    rootCls.contains("fbshorts") || rootCls.contains("reelsviewer")
                // 3. Fallback text inspection for Facebook Reels playback overlay markers
                val isReelsOverlay = if (!isReelsTab && !isReelsPlayer) {
                    val sb = StringBuilder()
                    collectText(root, sb, 0, intArrayOf(100))
                    val t = sb.toString().lowercase()
                    t.contains("remix this reel") || t.contains("use audio") ||
                        t.contains("share reel") || (t.contains("reels") && (t.contains("remix") || t.contains("original audio") || t.contains("reels audio")))
                } else false

                if (isReelsTab || isReelsPlayer || isReelsOverlay) {
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

    private fun hit(kind: String, heading: String, msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastHit < 700) return
        lastHit = now
        BlockerStore.incr(this, kind)
        performGlobalAction(GLOBAL_ACTION_HOME)
        showOverlay(heading, msg, 3000)
    }

    // ---------- Popup ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private var scrim: View? = null

    private fun showOverlay(heading: String, body: String, ms: Long) {
        main.post {
            if (overlay != null) return@post
            val svc = this@BlockerAccessibilityService
            val accent = 0xFFA78BFA.toInt()
            val accentDeep = 0xFF7C3AED.toInt()

            // dim scrim behind the card so it reads as a proper modal, matching the app's own dialogs
            val dim = View(svc).apply { setBackgroundColor(0x8A0B0A16.toInt()) }
            val dimLp = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(dim, dimLp); scrim = dim }

            val badge = LinearLayout(svc).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(0x33A78BFA)
                    cornerRadius = dp(22).toFloat()
                }
            }
            badge.addView(TextView(svc).apply {
                text = "\uD83D\uDEE1\uFE0F" // shield emoji, used as a lightweight icon badge
                textSize = 20f
                gravity = Gravity.CENTER
            })

            val headingRow = LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            headingRow.addView(badge, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) })
            headingRow.addView(TextView(svc).apply {
                text = heading
                setTextColor(0xFFF6F3FF.toInt())
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
            })

            val card = LinearLayout(svc).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(22), dp(22), dp(20))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF221A3D.toInt(), 0xFF160F2B.toInt())
                ).apply {
                    cornerRadius = dp(26).toFloat()
                    setStroke(dp(1), 0x66C7AFFF)
                }
                elevation = dp(16).toFloat()
            }
            card.addView(headingRow)
            card.addView(TextView(svc).apply {
                text = body
                setTextColor(0xFFC3BCDE.toInt())
                textSize = 15f
                setPadding(0, dp(12), 0, dp(18))
                setLineSpacing(dp(2).toFloat(), 1f)
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
                text = "Got it"
                setAllCaps(false)
                setTextColor(Color.WHITE)
                textSize = 15.5f
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
                progressFill.animate().scaleX(0f).setDuration(ms).setInterpolator(LinearInterpolator()).start()
            }
            main.removeCallbacks(hideRunnable)
            main.postDelayed(hideRunnable, ms)
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