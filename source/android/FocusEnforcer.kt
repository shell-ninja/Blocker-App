package com.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.blocker.data.RuleSnapshotHolder
import java.time.LocalDateTime

object FocusEnforcer {

    @Volatile
    private var cachedDefaultLauncherPackage: String? = null
    @Volatile
    private var lastLauncherResolveMs = 0L

    @Volatile
    private var cachedImePackages: Set<String> = emptySet()
    @Volatile
    private var lastImeResolveMs = 0L

    @Volatile
    private var lastEnforcedPackage: String? = null
    @Volatile
    private var lastEnforcedElapsedMs = 0L

    private val DIALER_PACKAGES = setOf(
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.phone"
    )

    private val PERMISSION_CONTROLLER_PACKAGES = setOf(
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    fun getDefaultLauncher(context: Context): String? {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedDefaultLauncherPackage
        if (cached != null && now - lastLauncherResolveMs < 10_000L) {
            return cached
        }
        val resolved = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        if (resolved != null) {
            cachedDefaultLauncherPackage = resolved
            lastLauncherResolveMs = now
        }
        return resolved ?: cached
    }

    fun getImePackages(context: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (cachedImePackages.isNotEmpty() && now - lastImeResolveMs < 15_000L) {
            return cachedImePackages
        }
        val imes = runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
        cachedImePackages = imes
        lastImeResolveMs = now
        return imes
    }

    /**
     * Exact system whitelist: NEVER use contains("android")!
     * Built-in system exemptions during active Focus Mode ONLY:
     * - Blocker's own package (pkg == context.packageName)
     * - Exact System UI & OS framework: pkg == "com.android.systemui" or pkg == "android"
     * - Default Home Screen Launcher package
     * - Enabled Keyboard / IME packages
     * - Active Phone Call / Dialer UI
     * - System Permission Controller ONLY when PermissionFlowTracker.isLaunchingPermissionIntent == true
     */
    fun isStrictCoreSystemExempt(context: Context, pkg: String): Boolean {
        if (pkg.isEmpty()) return true
        if (pkg == context.packageName) return true
        if (pkg == "com.android.systemui" || pkg == "android") return true

        val launcher = getDefaultLauncher(context)
        if (launcher != null && pkg == launcher) return true

        val imes = getImePackages(context)
        if (pkg in imes) return true

        if (pkg in DIALER_PACKAGES) return true

        if (PermissionFlowTracker.isLaunchingPermissionIntent) {
            if (pkg in PERMISSION_CONTROLLER_PACKAGES) return true
            if (pkg == "com.android.settings") return true
        }

        return false
    }

    fun getAppLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /**
     * Resolves the true foreground application package.
     */
    fun resolveForegroundPackage(
        service: AccessibilityService,
        event: AccessibilityEvent?
    ): String? {
        val evPkg = event?.packageName?.toString()
        val imes = getImePackages(service)

        // If event package is valid application, and not system ui or keyboard, check it first
        if (!evPkg.isNullOrEmpty() && evPkg != "com.android.systemui" && evPkg !in imes) {
            return evPkg
        }

        // Check rootInActiveWindow
        val rootPkg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (!rootPkg.isNullOrEmpty() && rootPkg != "com.android.systemui" && rootPkg !in imes) {
            return rootPkg
        }

        // Inspect service.windows for visible TYPE_APPLICATION window
        val wins = runCatching { service.windows }.getOrNull()
        if (!wins.isNullOrEmpty()) {
            for (w in wins) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val root = runCatching { w.root }.getOrNull()
                    val p = root?.packageName?.toString()
                    runCatching {
                        if (Build.VERSION.SDK_INT < 33) root?.recycle()
                    }
                    if (!p.isNullOrEmpty() && p != "com.android.systemui" && p !in imes) {
                        return p
                    }
                }
            }
        }

        return rootPkg ?: evPkg
    }

    /**
     * Evaluates active Focus Mode and enforces blocking on EVERY Event.
     * Returns true if blocked (0ms GLOBAL_ACTION_HOME + blackout overlay).
     */
    fun evaluateAndEnforce(
        service: AccessibilityService,
        event: AccessibilityEvent?
    ): Boolean {
        // Evaluate on state changed, content changed, clicked, scrolled
        if (event != null) {
            val type = event.eventType
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_CLICKED &&
                type != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                return false
            }
        }

        val nowEpochMs = System.currentTimeMillis()
        val nowDateTime = LocalDateTime.now()
        val snapshot = RuleSnapshotHolder.getFreshSnapshot(service)

        val isManualFocus = snapshot.isManualFocusRunningNow(nowEpochMs)
        val activeSchedule = snapshot.schedules.firstOrNull { it.enabled && it.isRunningAt(nowDateTime) }
        val hasJumuah = snapshot.prayerSlots.any { it.id == "JUMUAH" && it.isEnabled }
        val activePrayer = snapshot.prayerSlots.firstOrNull { it.isRunningAt(nowDateTime, hasJumuah) }

        val isAnyFocusActive = isManualFocus || activeSchedule != null || activePrayer != null
        if (!isAnyFocusActive) return false

        val allowedUserPackages: Set<String> = buildSet {
            if (isManualFocus) addAll(snapshot.manualFocusAllowedPackages)
            if (activeSchedule != null) {
                addAll(activeSchedule.allowedApps)
                addAll(activeSchedule.allowedPackages)
            }
            if (activePrayer != null) addAll(snapshot.prayerAllowedPackages)
        }

        val foregroundPkg = resolveForegroundPackage(service, event) ?: return false

        if (isStrictCoreSystemExempt(service, foregroundPkg)) {
            return false
        }

        if (foregroundPkg in allowedUserPackages) {
            return false
        }

        // Unauthorized app detected during active Focus Mode!
        val elapsed = SystemClock.elapsedRealtime()
        val isDuplicate = (foregroundPkg == lastEnforcedPackage && elapsed - lastEnforcedElapsedMs < 250L)
        lastEnforcedPackage = foregroundPkg
        lastEnforcedElapsedMs = elapsed

        val appLabel = getAppLabel(service, foregroundPkg)
        val reason = "Blocked by Focus Mode: $appLabel"

        // 1. Immediately show BlackoutShieldOverlay (0ms) + BilingualReminderOverlay
        TamperShieldManager.showInstantShield(service)
        OverlayHudManager.show(
            service,
            if (BlockerStore.popupLanguage(service) == "bn") "ফোকাস মোড দ্বারা ব্লক করা হয়েছে: $appLabel" else reason,
            5,
            BlockReason(BlockCategory.STUDY_LOCKDOWN, reason)
        )

        // 2. Immediately call GLOBAL_ACTION_HOME (0ms exit)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        // 3. Increment today's blocked content counter on the Home 7-day graph
        if (!isDuplicate) {
            BlockerStore.incr(service, "blocked_apps")
            BlockerStore.incr(service, "app_block")
        }

        return true
    }

    /**
     * Watchdog evaluation for BlockerForegroundService (every 250-350ms).
     */
    fun inspectWatchdogForeground(context: Context, topPkg: String?): Boolean {
        if (topPkg.isNullOrEmpty()) return false
        if (topPkg == context.packageName) return false

        val nowEpochMs = System.currentTimeMillis()
        val nowDateTime = LocalDateTime.now()
        val snapshot = RuleSnapshotHolder.getFreshSnapshot(context)

        val isManualFocus = snapshot.isManualFocusRunningNow(nowEpochMs)
        val activeSchedule = snapshot.schedules.firstOrNull { it.enabled && it.isRunningAt(nowDateTime) }
        val hasJumuah = snapshot.prayerSlots.any { it.id == "JUMUAH" && it.isEnabled }
        val activePrayer = snapshot.prayerSlots.firstOrNull { it.isRunningAt(nowDateTime, hasJumuah) }

        val isAnyFocusActive = isManualFocus || activeSchedule != null || activePrayer != null
        if (!isAnyFocusActive) return false

        val allowedUserPackages: Set<String> = buildSet {
            if (isManualFocus) addAll(snapshot.manualFocusAllowedPackages)
            if (activeSchedule != null) {
                addAll(activeSchedule.allowedApps)
                addAll(activeSchedule.allowedPackages)
            }
            if (activePrayer != null) addAll(snapshot.prayerAllowedPackages)
        }

        if (isStrictCoreSystemExempt(context, topPkg)) {
            return false
        }

        if (topPkg in allowedUserPackages) {
            return false
        }

        // Kick straight to home
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        runCatching { context.startActivity(homeIntent) }

        val appLabel = getAppLabel(context, topPkg)
        val isBn = BlockerStore.popupLanguage(context) == "bn"
        val reason = "Blocked by Focus Mode: $appLabel"

        if (context is AccessibilityService) {
            OverlayHudManager.show(
                context,
                if (isBn) "ফোকাস মোড দ্বারা ব্লক করা হয়েছে: $appLabel" else reason,
                4,
                BlockReason(BlockCategory.STUDY_LOCKDOWN, reason)
            )
        } else if (context is BlockerForegroundService) {
            context.showTamperAlert(
                title = if (isBn) "ফোকাস মোড সক্রিয়" else "Focus Mode Active",
                message = if (isBn) "ফোকাস মোড চলাকালীন $appLabel বন্ধ রাখা হয়েছে।" else reason
            )
        }

        BlockerStore.incr(context, "blocked_apps")
        BlockerStore.incr(context, "app_block")
        return true
    }
}
