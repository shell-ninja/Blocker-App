package com.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AccessibilityOffUninstallGuardService : Service() {

    companion object {
        private const val TAG = "A11yOffGuardService"
        const val CHANNEL_ID = "blocker_a11y_off_guard_channel"
        const val NOTIFICATION_ID = 9002

        fun start(context: Context) {
            val intent = Intent(context, AccessibilityOffUninstallGuardService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                Log.e(TAG, "Failed starting AccessibilityOffUninstallGuardService", it)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AccessibilityOffUninstallGuardService::class.java)
            runCatching { context.stopService(intent) }
            UninstallShieldOverlay.hide()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null
    private var powerManager: PowerManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (_: Throwable) {
            runCatching { startForeground(NOTIFICATION_ID, notif) }
        }

        startGuardLoop()
        return START_STICKY
    }

    private fun startGuardLoop() {
        loopJob?.cancel()
        loopJob = serviceScope.launch {
            while (isActive) {
                try {
                    val isInteractive = powerManager?.isInteractive ?: true
                    if (isInteractive) {
                        val isA11yDisabled = BlockerStore.isAccessibilityDisabledForBanking(this@AccessibilityOffUninstallGuardService)
                        if (!isA11yDisabled) {
                            stopSelf()
                            break
                        }

                        val topPkg = getTopPackage()
                        if (topPkg != null && topPkg != packageName && isSettingsOrInstaller(topPkg)) {
                            UninstallShieldOverlay.show(this@AccessibilityOffUninstallGuardService)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in guard loop", t)
                }
                delay(300L)
            }
        }
    }

    private fun isSettingsOrInstaller(pkg: String): Boolean {
        val p = pkg.lowercase()
        if (p == packageName) return false
        return p.contains("packageinstaller") ||
            p.contains("packagemanager") ||
            p == "com.miui.securitycenter" ||
            p == "com.android.settings" ||
            p == "com.google.android.packageinstaller" ||
            p == "com.samsung.android.packageinstaller" ||
            p == "com.miui.packageinstaller" ||
            (p.contains("settings") && !p.contains("quicksettings"))
    }

    private fun getTopPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3000L, now) ?: return null
        var lastPkg: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == 1 /* MOVE_TO_FOREGROUND */) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Uninstall & Tamper Shield",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Protects Blocker against tampering while Accessibility is off for Banking apps"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
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
        val pi = PendingIntent.getActivity(this, 9005, intent, flags)

        val isBn = BlockerStore.popupLanguage(this) == "bn"
        val title = if (isBn) "অ্যাক্সেসিবিলিটি বন্ধ — আনইনস্টল সুরক্ষা সক্রিয়"
            else "Accessibility Disabled — Uninstall Shield Active (Tap to Re-enable)"
        val message = if (isBn) "ব্যাংকিং অ্যাপস শেষ হলে পুনরায় চালু করতে ট্যাপ করুন"
            else "Android Settings & uninstallation locked while Accessibility is off."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        UninstallShieldOverlay.hide()
    }
}
