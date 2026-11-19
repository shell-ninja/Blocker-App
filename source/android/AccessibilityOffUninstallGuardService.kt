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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dedicated Foreground Guard Service running while Accessibility is temporarily disabled
 * (e.g. for Banking apps).
 *
 * Enforces uninstallation and settings protection by statefully tracking the foreground package
 * every 100ms and immediately blocking Settings, PackageInstaller, Security Centers, and Play Store.
 */
class AccessibilityOffUninstallGuardService : Service() {

    companion object {
        private const val TAG = "A11yOffGuardService"
        const val CHANNEL_ID = "blocker_a11y_off_guard_channel"
        const val NOTIFICATION_ID = 9002

        fun start(context: Context) {
            val intent = Intent(context, AccessibilityOffUninstallGuardService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
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
    private var currentForegroundPkg: String? = null
    private var lastQueryTimeMs: Long = 0L
    private var lastHomeKickTimeMs: Long = 0L

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
                            UninstallShieldOverlay.hide()
                            stopSelf()
                            break
                        }

                        val now = System.currentTimeMillis()
                        updateForegroundPackage(now)

                        val pkg = currentForegroundPkg
                        if (pkg != null && isBlockedTarget(pkg)) {
                            // 1. Show and keep visible full-screen touch-blocking overlay
                            UninstallShieldOverlay.show(this@AccessibilityOffUninstallGuardService)

                            // 2. Continuously fire Home Intent on every 250ms tick until foreground is non-blocked
                            if (now - lastHomeKickTimeMs >= 250L) {
                                lastHomeKickTimeMs = now
                                sendHome()
                            }
                        } else {
                            // If current app is verified non-blocked (e.g. Banking app or Home launcher)
                            // and user requested return to home or overlay is dismissed
                            if (UninstallShieldOverlay.isShowing && UninstallShieldOverlay.hasRequestedHomeReturn) {
                                UninstallShieldOverlay.hide()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in guard loop", t)
                }
                delay(100L) // 100ms Watchdog poll
            }
        }
    }

    private fun updateForegroundPackage(now: Long) {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        if (lastQueryTimeMs == 0L) {
            lastQueryTimeMs = now - 2000L
        }
        val events = runCatching {
            usm.queryEvents(lastQueryTimeMs - 1000L, now)
        }.getOrNull()
        lastQueryTimeMs = now

        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == 1 /* MOVE_TO_FOREGROUND */) {
                    currentForegroundPkg = event.packageName
                }
            }
        }

        if (currentForegroundPkg == null) {
            val stats = runCatching {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 120_000L, now)
            }.getOrNull()
            currentForegroundPkg = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        }
    }

    private fun isBlockedTarget(pkg: String): Boolean {
        val p = pkg.lowercase().trim()
        if (p.isEmpty() || p == packageName) return false

        // 1. Android Settings
        if (p == "com.android.settings" || (p.contains("settings") && !p.contains("quicksettings"))) {
            return true
        }

        // 2. Package Installers
        if (p == "com.android.packageinstaller" ||
            p == "com.google.android.packageinstaller" ||
            p == "com.samsung.android.packageinstaller" ||
            p == "com.miui.packageinstaller" ||
            p.contains("packageinstaller") ||
            p.contains("packagemanager")) {
            return true
        }

        // 3. Google Play Store (where users can tap "Uninstall")
        if (p == "com.android.vending") {
            return true
        }

        // 4. OEM Security Centers
        if (p == "com.miui.securitycenter" ||
            p == "com.samsung.android.lool" ||
            p == "com.coloros.safecenter" ||
            p == "com.iqoo.secure" ||
            p == "com.transsion.phonemaster") {
            return true
        }

        return false
    }

    private fun sendHome() {
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
        }
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
