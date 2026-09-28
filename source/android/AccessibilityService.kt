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
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
            "com.oppo.launcher"
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
        // The AOSP list screen only ever means "browse admins to possibly deactivate one" \u2014 block outright.
        private val DEVICE_ADMIN_LIST_CLASSES = setOf(
            "com.android.settings.DeviceAdminSettings", "com.android.settings.Settings\$DeviceAdminSettingsActivity"
        )
        // This same activity is also how Blocker's own onboarding grants admin the first time, so it's only
        // blocked once admin is already active (i.e. this view can only be for managing/deactivating it).
        private const val DEVICE_ADMIN_DETAIL_CLASS = "com.android.settings.DeviceAdminAdd"
        // packages that must keep working during focus mode (calls, permission/share/file dialogs, Google services)
        private val FOCUS_SYSTEM_PKGS = setOf(
            "com.android.incallui", "com.android.server.telecom", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.android.intentresolver", "com.android.documentsui",
            "com.google.android.documentsui", "com.google.android.providers.media.module", "com.google.android.gms"
        )
        private val RELOAD_KEYS = setOf("domains", "keywords", "tlds", "whitelist", "apps", "scan_exempt", "focus_apps", "block_fb_reels", "block_insta_reels", "block_insta_search", "block_yt_shorts", "active", "shield")
        private const val SCAN_GAP_MS = 1000L
        private const val MAX_SCAN_CHARS = 20_000
        private const val REDIRECT_URL = "https://www.google.com"
    }

    private val main = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var lastHit = 0L
    private val lastScan = HashMap<String, Long>()
    private val quietUntil = HashMap<String, Long>() // after a redirect, wait briefly before re-scanning that browser
    private val hideRunnable = Runnable { removeOverlay() }

    // system UI and launchers are never scanned or blocked (blocking the launcher would trap the user)
    private var neverBlock: Set<String> = setOf("com.android.systemui", "android")

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

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key in RELOAD_KEYS) reload()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        neverBlock = neverBlock + launcherPackages()
        reload()
        BlockerStore.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        BlockerStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
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

    private fun reload() {
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
        if (pkg == packageName) return
        if (checkAdbDialog(pkg)) return
        if (pkg in neverBlock) return
        if (BlockerStore.focusActive(this) && focusBlocks(ev, pkg)) return
        if (!BlockerStore.active(this)) return

        when {
            pkg in apps -> {
                if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    hit("apps", "🚫 App blocked", "This app is on your block list.")
                }
                return
            }
            pkg in BROWSER_URL_IDS -> if (checkBrowser(pkg)) return
            pkg in SETTINGS_PKGS -> {
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
     * A whitelist phrase (single or multi-word, e.g. "sex education") shields any keyword inside it.
     */
    private fun wholeWordHit(t: String): String? {
        val safe = ArrayList<IntRange>()
        for (w in whitelist) {
            if (w.isEmpty()) continue
            var from = 0
            while (true) {
                val i = t.indexOf(w, from)
                if (i < 0) break
                safe.add(i until i + w.length)
                from = i + 1
            }
        }
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
        (getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager)
            .isAdminActive(android.content.ComponentName(this, BlockerDeviceAdminReceiver::class.java))
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

    private fun checkSettings(ev: AccessibilityEvent) {
        if (!BlockerStore.active(this) || !BlockerStore.shield(this) || BlockerStore.guardOpen(this, "shield")) return
        val root = rootInActiveWindow ?: return
        val cls = root.className?.toString().orEmpty()
        val evCls = ev.className?.toString().orEmpty()

        if (cls in DEVICE_ADMIN_LIST_CLASSES) {
            lockOutOfSettings()
            return
        }
        if (cls == DEVICE_ADMIN_DETAIL_CLASS) {
            // We recognize this exact screen, so decide from that alone rather than falling through to
            // the text check below: Blocker's own onboarding explanation ("Prevents Blocker from being
            // uninstalled") would otherwise match "blocker" + "uninstall" and block its own setup.
            if (isOurAdminActive()) lockOutOfSettings()
            return
        }

        // Developer options activity detection
        val isDevActivity = cls in DEV_OPTIONS_CLASSES ||
            evCls in DEV_OPTIONS_CLASSES ||
            cls.contains("DevelopmentSettings", ignoreCase = true) ||
            cls.contains("DeveloperOptions", ignoreCase = true) ||
            evCls.contains("DevelopmentSettings", ignoreCase = true) ||
            evCls.contains("DeveloperOptions", ignoreCase = true)
        if (isDevActivity) {
            lockOutOfSettings("Developer options are locked while Protection Mode is active.")
            return
        }

        val sb = StringBuilder()
        collectText(root, sb, 0, intArrayOf(400))
        // Strip Blocker's own device-admin explanation text before matching: it legitimately contains
        // "blocker" + "uninstalled" together, which would otherwise look identical to a real attempt.
        val t = sb.toString().lowercase().replace("prevents blocker from being uninstalled.", "")

        // Check for Developer Options screen content (title and characteristic developer options)
        val hasDevTitle = "developer options" in t || "development settings" in t
        val hasDevMarkers = "usb debugging" in t || "wireless debugging" in t || "revoke usb debugging" in t ||
            "oem unlocking" in t || "desktop backup password" in t || "stay awake" in t ||
            "running services" in t || "logger buffer" in t || "bug report" in t ||
            "use developer options" in t || "turn off developer options" in t
        val isDeveloperOptions = (hasDevTitle && hasDevMarkers) ||
            "revoke usb debugging" in t || "wireless debugging" in t

        if (isDeveloperOptions) {
            lockOutOfSettings("Developer options are locked while Protection Mode is active.")
            return
        }

        val direct = DIRECT_TERMS.any { it in t }
        val isOurAppScreen = "blocker" in t || packageName.lowercase() in t
        val hasRiskAction = APP_RISK_TERMS.any { it in t }
        if (direct || (isOurAppScreen && hasRiskAction)) lockOutOfSettings()
    }

    private fun lockOutOfSettings(message: String = "Device Admin & app settings for Blocker are locked while Protection Mode is active.") {
        val now = System.currentTimeMillis()
        if (now - lastHit < 700) return
        lastHit = now
        BlockerStore.incr(this, "tamper")
        if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
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
