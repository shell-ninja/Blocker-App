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
    private var lastEnforcedPackage: String? = null
    @Volatile
    private var lastEnforcedElapsedMs = 0L

    fun getDefaultLauncher(context: Context): String? {
        return SystemExemptions.getHomePackages(context).firstOrNull()
    }

    fun getImePackages(context: Context): Set<String> {
        return SystemExemptions.getImePackages(context)
    }

    /**
     * Exact system whitelist: delegates to the bulletproof single source of truth in SystemExemptions.
     */
    fun isStrictCoreSystemExempt(context: Context, pkg: String): Boolean {
        return SystemExemptions.isNeverBlockPackage(context, pkg)
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

        // If event package is valid application, and not system ui or exempt, check it first
        if (!evPkg.isNullOrEmpty() && evPkg != "com.android.systemui" && !SystemExemptions.isNeverBlockPackage(service, evPkg)) {
            return evPkg
        }

        // Check rootInActiveWindow
        val rootPkg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (!rootPkg.isNullOrEmpty() && rootPkg != "com.android.systemui" && !SystemExemptions.isNeverBlockPackage(service, rootPkg)) {
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
                    if (!p.isNullOrEmpty() && p != "com.android.systemui" && !SystemExemptions.isNeverBlockPackage(service, p)) {
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
        // Line 1 Guard: Never block exempt packages
        val evPkg = event?.packageName?.toString()
        if (SystemExemptions.isNeverBlockPackage(service, evPkg)) {
            return false
        }

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

        // Grace period check (1,200ms after last HOME kick)
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - SystemExemptions.lastFocusHomeKickElapsedMs < 1200L) {
            return false
        }

        // Check visible windows: if ANY window is exempt launcher/system, return ALLOW immediately
        val wins = runCatching { service.windows }.getOrNull()
        if (!wins.isNullOrEmpty()) {
            for (w in wins) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val root = runCatching { w.root }.getOrNull()
                    val p = root?.packageName?.toString()
                    runCatching {
                        if (Build.VERSION.SDK_INT < 33) root?.recycle()
                    }
                    if (SystemExemptions.isNeverBlockPackage(service, p)) {
                        return false // Launcher is active or visible
                    }
                }
            }
        }

        val rootPkg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (SystemExemptions.isNeverBlockPackage(service, rootPkg)) {
            return false
        }

        val foregroundPkg = resolveForegroundPackage(service, event) ?: return false
        if (SystemExemptions.isNeverBlockPackage(service, foregroundPkg)) {
            return false
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
            if (isManualFocus) addAll(snapshot.manualFocusAllowedPackages.map { it.trim().lowercase(java.util.Locale.ROOT) })
            if (activeSchedule != null) {
                addAll(activeSchedule.allowedApps.map { it.trim().lowercase(java.util.Locale.ROOT) })
                addAll(activeSchedule.allowedPackages.map { it.trim().lowercase(java.util.Locale.ROOT) })
            }
            if (activePrayer != null) addAll(snapshot.prayerAllowedPackages.map { it.trim().lowercase(java.util.Locale.ROOT) })
        }

        if (foregroundPkg.trim().lowercase(java.util.Locale.ROOT) in allowedUserPackages) {
            return false
        }

        // CRITICAL FINAL SINK GATE: Inside the actual function that shows the Focus block overlay
        val isExempt = SystemExemptions.isNeverBlockPackage(service, foregroundPkg)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("FOCUS_GUARD", "Checking pkg=$foregroundPkg exempt=$isExempt")
        }
        if (isExempt) {
            return false // Architecturally impossible to block any Home launcher
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

        // Set grace period before calling GLOBAL_ACTION_HOME
        SystemExemptions.lastFocusHomeKickElapsedMs = SystemClock.elapsedRealtime()

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
        if (SystemExemptions.isNeverBlockPackage(context, topPkg)) return false

        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - SystemExemptions.lastFocusHomeKickElapsedMs < 1200L) {
            return false
        }

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
            if (isManualFocus) addAll(snapshot.manualFocusAllowedPackages.map { it.trim().lowercase(java.util.Locale.ROOT) })
            if (activeSchedule != null) {
                addAll(activeSchedule.allowedApps.map { it.trim().lowercase(java.util.Locale.ROOT) })
                addAll(activeSchedule.allowedPackages.map { it.trim().lowercase(java.util.Locale.ROOT) })
            }
            if (activePrayer != null) addAll(snapshot.prayerAllowedPackages.map { it.trim().lowercase(java.util.Locale.ROOT) })
        }

        if (topPkg.trim().lowercase(java.util.Locale.ROOT) in allowedUserPackages) {
            return false
        }

        // CRITICAL FINAL SINK GATE
        val isExempt = SystemExemptions.isNeverBlockPackage(context, topPkg)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("FOCUS_GUARD", "Checking pkg=$topPkg exempt=$isExempt")
        }
        if (isExempt) {
            return false
        }

        SystemExemptions.lastFocusHomeKickElapsedMs = SystemClock.elapsedRealtime()

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
