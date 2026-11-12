package com.blocker

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.blocker.data.RuleSnapshotHolder

/**
 * Suppresses distracting notifications from blocked or non-whitelisted apps during
 * active Focus, Scheduled Focus, Study Mode, or Prayer Mode sessions.
 * Preserves ongoing calls, alarms, essential telecom/clock packages, and active session whitelists.
 */
class BlockerNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: BlockerNotificationListenerService? = null
            private set

        private val ESSENTIAL_EXEMPT_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.deskclock",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging"
        )

        fun isGranted(context: Context): Boolean = runCatching {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            enabledListeners.contains(context.packageName)
        }.getOrDefault(false)

        fun sweepActiveNotificationsIfRunning() {
            runCatching { instance?.sweepActiveNotifications() }
        }
    }

    override fun onListenerConnected() {
        try {
            super.onListenerConnected()
            instance = this
            sweepActiveNotifications()
        } catch (_: Throwable) {
            // Never crash notification listener
        }
    }

    override fun onListenerDisconnected() {
        try {
            if (instance === this) {
                instance = null
            }
            super.onListenerDisconnected()
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        try {
            if (instance === this) {
                instance = null
            }
            super.onDestroy()
        } catch (_: Throwable) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return
            if (shouldSuppressNotification(sbn)) {
                runCatching { cancelNotification(sbn.key) }
            }
        } catch (_: Throwable) {
            // Never crash notification listener
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            // No-op safe hook
        } catch (_: Throwable) {}
    }

    fun sweepActiveNotifications() {
        try {
            val current = runCatching { activeNotifications }.getOrNull() ?: return
            for (sbn in current) {
                if (sbn != null && shouldSuppressNotification(sbn)) {
                    runCatching { cancelNotification(sbn.key) }
                }
            }
        } catch (_: Throwable) {}
    }

    fun shouldSuppressNotification(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName ?: return false

        // Critical exemption 1: Blocker's own notifications
        if (pkg == packageName) return false

        // Critical exemption 2: Ongoing carrier phone calls, alarms, car/navigation & foreground system services
        val category = sbn.notification?.category
        if (category == Notification.CATEGORY_CALL ||
            category == Notification.CATEGORY_MISSED_CALL ||
            category == Notification.CATEGORY_ALARM ||
            category == Notification.CATEGORY_NAVIGATION ||
            category == Notification.CATEGORY_SERVICE ||
            category == "car"
        ) {
            return false
        }

        // Critical exemption 3: Essential system, telecom, dialer, clock/alarm & SMS packages
        if (pkg in ESSENTIAL_EXEMPT_PACKAGES) return false

        val snapshot = RuleSnapshotHolder.getFreshSnapshot(this)
        if (!snapshot.suppressNotificationsInFocus) return false

        // Permanently blocked apps while Master Protection Mode is ON
        if (snapshot.isProtectionActive && pkg in snapshot.blockedApps) {
            return true
        }

        val isManualFocusNow = snapshot.isManualFocusRunningNow()
        val isStudyNow = snapshot.isStudyModeRunningNow(this)
        val activeSchedule = snapshot.getActiveScheduleNow()
        val activePrayer = snapshot.getActivePrayerSlotNow()
        val isAnySessionActive = isManualFocusNow || isStudyNow || activeSchedule != null || activePrayer != null

        if (!isAnySessionActive) return false

        // During Study Mode full device lockdown, only essential exemptions above are allowed
        // If another Focus/Schedule/Prayer session isn't overriding whitelist rules, check session whitelists:
        if (!isStudyNow) {
            if (isManualFocusNow && pkg in snapshot.manualFocusAllowedPackages) {
                return false
            }
            if (activeSchedule != null && (pkg in activeSchedule.allowedPackages || pkg in activeSchedule.allowedApps)) {
                return false
            }
            if (activePrayer != null && pkg in snapshot.prayerAllowedPackages) {
                return false
            }
        } else {
            // Study lockdown active alongside another session: keep system/essential exempt above; suppress apps
            return true
        }

        return true
    }
}
