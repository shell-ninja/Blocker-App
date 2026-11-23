package com.blocker.engine.tamper

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.blocker.PermissionFlowTracker

object AccessibilityPauseController {
    private const val ALARM_REQUEST_CODE = 9410

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expected = ComponentName(context, serviceClass).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun canStartPause(context: Context, adminReceiverClass: Class<*>): Pair<Boolean, String?> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminActive = dpm.isAdminActive(ComponentName(context, adminReceiverClass))
        if (!adminActive) return false to "Device Admin must be active to keep Uninstall Protection locked."
        if (!Settings.canDrawOverlays(context)) return false to "Overlay permission is required to lock Settings."
        return true to null
    }

    fun startTenMinutePause(
        context: Context,
        disableSelfAction: () -> Unit
    ) {
        val appCtx = context.applicationContext
        // 1. Persist state synchronously FIRST
        val endEpochMs = AccessibilityPauseState.startTenMinutePause(appCtx)

        // 2. Start Foreground Guard Service BEFORE turning off Accessibility
        AccessibilityPauseGuardService.start(appCtx)

        // 3. Schedule exact 10-minute wake alarm
        scheduleExpiryAlarm(appCtx, endEpochMs)

        // 4. Disable Accessibility Service (API 24+)
        runCatching { disableSelfAction() }
    }

    fun scheduleExpiryAlarm(context: Context, triggerAtEpochMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AccessibilityPauseExpiryReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true

            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pi)
            }
        }
    }

    fun cancelExpiryAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AccessibilityPauseExpiryReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    /**
     * Attempts silent automatic re-enable via WRITE_SECURE_SETTINGS (if granted via ADB).
     * Returns true if Accessibility is now enabled.
     */
    fun tryAutoReEnableAccessibility(context: Context, serviceClass: Class<*>): Boolean {
        if (isAccessibilityServiceEnabled(context, serviceClass)) return true
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return false

        return runCatching {
            val flatName = ComponentName(context, serviceClass).flattenToString()
            val existing = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val parts = existing.split(':').filter { it.isNotBlank() }.toMutableList()
            if (parts.none { it.equals(flatName, ignoreCase = true) }) {
                parts.add(flatName)
            }
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                parts.joinToString(":")
            )
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            isAccessibilityServiceEnabled(context, serviceClass)
        }.getOrDefault(false)
    }

    fun openAccessibilitySettingsForReEnable(context: Context) {
        // End pause early if still ticking and mark awaiting re-enable
        cancelExpiryAlarm(context)
        AccessibilityPauseState.markPauseExpiredAwaitingReEnable(context)
        AccessibilityPauseState.beginReEnableSettingsWindow()

        // Set existing PermissionFlowTracker flag so MainActivity doesn't close prematurely
        PermissionFlowTracker.isLaunchingPermissionIntent = true

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(intent) }
    }
}
