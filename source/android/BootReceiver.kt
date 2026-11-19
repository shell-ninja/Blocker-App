package com.blocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * BroadcastReceiver to prime Blocker's background protection immediately upon
 * device boot or package update without requiring the user to open the app.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val NOTIFICATION_ID_A11Y_RESTORE = 1003
        const val REQUEST_CODE_A11Y_PAUSE_EXPIRED = 8888
        const val ACTION_ACCESSIBILITY_PAUSE_EXPIRED = "com.blocker.ACTION_ACCESSIBILITY_PAUSE_EXPIRED"
        const val ACTION_A11Y_WAIT_EXPIRED = "com.blocker.ACTION_A11Y_WAIT_EXPIRED"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == ACTION_A11Y_WAIT_EXPIRED || action == ACTION_ACCESSIBILITY_PAUSE_EXPIRED) {
            AccessibilityPauseManager.onWaitPeriodCompleted(context)
            return
        }

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            BlockerForegroundService.start(context)
            DeviceOwnerHelper.enforceDisallowSafeBoot(context)

            val now = System.currentTimeMillis()
            val isA11yDisabledForBanking = BlockerStore.isAccessibilityDisabledForBanking(context)
            if (isA11yDisabledForBanking) {
                // Ensure Layer 2 Foreground Guard Service is running to protect against uninstall/settings
                AccessibilityOffUninstallGuardService.start(context)
                AccessibilityPauseManager.showReEnableNotification(context)
                return
            }

            val waitTarget = BlockerStore.disableAccessibilityWaitTargetMs(context)
            if (waitTarget > 0L) {
                if (waitTarget > now) {
                    AccessibilityPauseManager.scheduleWaitAlarm(context, waitTarget)
                } else {
                    AccessibilityPauseManager.onWaitPeriodCompleted(context)
                }
                return
            }

            val isPaused = com.blocker.data.RuleSnapshotHolder.snapshot.isAccessibilityTemporarilyPaused(now) ||
                BlockerStore.isAccessibilityTemporarilyPaused(context, now)

            if (isPaused) {
                // Do NOT re-enable Accessibility while paused!
                return
            }

            // Auto-restore Accessibility Service if WRITE_SECURE_SETTINGS is granted
            if (BlockerStore.active(context) && BlockerStore.shield(context)) {
                var restored = false
                runCatching {
                    val serviceComponent = "${context.packageName}/${BlockerAccessibilityService::class.java.name}"
                    val enabled = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    ) ?: ""
                    if (!enabled.contains(context.packageName)) {
                        val newServices = if (enabled.isEmpty()) serviceComponent else "$enabled:$serviceComponent"
                        Settings.Secure.putString(
                            context.contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            newServices
                        )
                        Settings.Secure.putString(
                            context.contentResolver,
                            Settings.Secure.ACCESSIBILITY_ENABLED,
                            "1"
                        )
                        restored = true
                    } else {
                        restored = true
                    }
                }

                // If accessibility service is not active after boot, post high-priority alert immediately
                if (!restored && BlockerAccessibilityService.instance == null) {
                    runCatching {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (Build.VERSION.SDK_INT >= 26) {
                            nm.createNotificationChannel(
                                NotificationChannel("blocker_alerts", "Protection alerts", NotificationManager.IMPORTANCE_HIGH)
                            )
                        }
                        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val pi = PendingIntent.getActivity(
                            context,
                            0,
                            settingsIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val n = NotificationCompat.Builder(context, "blocker_alerts")
                            .setSmallIcon(android.R.drawable.ic_lock_lock)
                            .setContentTitle("Blocker protection weakened")
                            .setContentText("Accessibility service is inactive after reboot. Tap to restore protection immediately.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pi)
                            .setAutoCancel(true)
                            .build()
                        nm.notify(NOTIFICATION_ID_A11Y_RESTORE, n)
                    }
                }
            }
        }
    }
}
