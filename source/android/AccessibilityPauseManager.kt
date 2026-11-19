package com.blocker

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blocker.data.RuleSnapshotHolder

object AccessibilityPauseManager {
    private const val TAG = "A11yPauseManager"
    const val REQUEST_CODE_ALARM = 8888
    const val NOTIFICATION_ID_A11Y_RE_ENABLE = 9001
    const val ACTION_ACCESSIBILITY_PAUSE_EXPIRED = "com.blocker.ACTION_ACCESSIBILITY_PAUSE_EXPIRED"
    const val ACTION_A11Y_WAIT_EXPIRED = "com.blocker.ACTION_A11Y_WAIT_EXPIRED"
    const val FIXED_WAIT_MS = 5 * 60 * 1000L // 300,000 ms = strictly 5 minutes

    /**
     * Starts the fixed 5-minute waiting countdown before disabling Accessibility.
     */
    fun startWaitPeriod(context: Context) {
        val targetMs = System.currentTimeMillis() + FIXED_WAIT_MS
        BlockerStore.setDisableAccessibilityWaitTargetMs(context, targetMs)
        RuleSnapshotHolder.syncFromStore(context)
        scheduleWaitAlarm(context, targetMs)
    }

    /**
     * Aborts the 5-minute waiting countdown, keeping Accessibility active.
     */
    fun abortWaitPeriod(context: Context) {
        cancelWaitAlarm(context)
        BlockerStore.setDisableAccessibilityWaitTargetMs(context, 0L)
        RuleSnapshotHolder.syncFromStore(context)
    }

    /**
     * Called when the 5-minute waiting timer finishes (alarm triggered, ticker, or user completion).
     */
    fun onWaitPeriodCompleted(context: Context) {
        cancelWaitAlarm(context)
        BlockerStore.setDisableAccessibilityWaitTargetMs(context, 0L)
        BlockerStore.setAccessibilityDisabledForBanking(context, true)
        RuleSnapshotHolder.syncFromStore(context)

        // Suspend WRITE_SECURE_SETTINGS auto-restore and disable Accessibility via disableSelf()
        runCatching {
            BlockerAccessibilityService.instance?.disableSelf()
        }.onFailure {
            Log.e(TAG, "Failed calling disableSelf()", it)
        }

        // Start Layer 2 Foreground Guard Service (UsageStats + Overlay Watchdog)
        AccessibilityOffUninstallGuardService.start(context)

        // Post ongoing notification
        showReEnableNotification(context)
    }

    /**
     * Re-enables Accessibility, stops the overlay guard service, and restores protection.
     */
    fun reEnableAccessibility(context: Context) {
        cancelWaitAlarm(context)
        BlockerStore.setDisableAccessibilityWaitTargetMs(context, 0L)
        BlockerStore.setAccessibilityDisabledForBanking(context, false)
        BlockerStore.setAccessibilityPauseUntilEpochMs(context, 0L)
        RuleSnapshotHolder.syncFromStore(context)

        // Stop Layer 2 Foreground Guard Service & dismiss overlay
        AccessibilityOffUninstallGuardService.stop(context)
        UninstallShieldOverlay.hide()
        dismissReEnableNotification(context)

        // Check if WRITE_SECURE_SETTINGS is granted to auto-re-enable
        val hasWriteSecure = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        var autoRestored = false
        if (hasWriteSecure) {
            runCatching {
                val cr = context.contentResolver
                val serviceComponent = ComponentName(context, BlockerAccessibilityService::class.java).flattenToString()
                val enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                val servicesList = enabled.split(":").filter { it.isNotBlank() }.toMutableList()
                if (!servicesList.contains(serviceComponent)) {
                    servicesList.add(serviceComponent)
                }
                val newServices = servicesList.joinToString(":")
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
                Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                autoRestored = true
            }.onFailure {
                Log.e(TAG, "Failed auto-restoring accessibility via WRITE_SECURE_SETTINGS", it)
            }
        }

        if (!autoRestored || BlockerAccessibilityService.instance == null) {
            // Open Android Accessibility settings
            runCatching {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                PermissionFlowTracker.isLaunchingPermissionIntent = true
                PermissionFlowTracker.lastAccessibilityPermissionRequestMs = SystemClock.elapsedRealtime()
                MainActivity.isLaunchingExternal = true
                context.startActivity(intent)
            }
        }
    }

    fun scheduleWaitAlarm(context: Context, targetMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BootReceiver::class.java).apply {
            action = ACTION_A11Y_WAIT_EXPIRED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE_ALARM, intent, flags)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMs, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMs, pi)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMs, pi)
                }
            } else {
                am.set(AlarmManager.RTC_WAKEUP, targetMs, pi)
            }
        }.onFailure {
            Log.e(TAG, "Failed scheduling wait alarm", it)
        }
    }

    fun cancelWaitAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BootReceiver::class.java).apply {
            action = ACTION_A11Y_WAIT_EXPIRED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE_ALARM, intent, flags)
        runCatching { am.cancel(pi) }
    }

    // Keep legacy aliases for backwards compatibility
    fun startTemporaryPause(context: Context, minutes: Int) = startWaitPeriod(context)
    fun onPauseExpired(context: Context) = onWaitPeriodCompleted(context)
    fun resumeProtectionNow(context: Context) = reEnableAccessibility(context)
    fun disableAccessibilityNow(context: Context) {
        runCatching { BlockerAccessibilityService.instance?.disableSelf() }
    }

    fun showReEnableNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            PermissionFlowTracker.isLaunchingPermissionIntent = true
            PermissionFlowTracker.lastAccessibilityPermissionRequestMs = SystemClock.elapsedRealtime()
            MainActivity.isLaunchingExternal = true

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getActivity(context, NOTIFICATION_ID_A11Y_RE_ENABLE, intent, flags)

            val isBn = BlockerStore.popupLanguage(context) == "bn"
            val title = if (isBn) "অ্যাক্সেসিবিলিটি বন্ধ — আনইনস্টল সুরক্ষা সক্রিয়"
                else "Accessibility Disabled — Uninstall Shield Active (Tap to Re-enable)"
            val message = if (isBn) "ব্লকার অ্যাক্সেসিবিলিটি পুনরায় চালু করতে ট্যাপ করুন"
                else "Tap to re-enable Blocker Accessibility"

            val builder = NotificationCompat.Builder(context, BlockerForegroundService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pi)

            nm.notify(NOTIFICATION_ID_A11Y_RE_ENABLE, builder.build())
        }
    }

    fun dismissReEnableNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_A11Y_RE_ENABLE)
        }
    }
}
