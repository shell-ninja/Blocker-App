package com.blocker.engine.tamper

import com.blocker.BlockerAccessibilityService

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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
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
import java.util.Locale

class AccessibilityPauseGuardService : Service() {

    companion object {
        private const val CHANNEL_ID = "blocker_pause_uninstall_guard"
        private const val NOTIFICATION_ID = 4041
        private const val ALERT_CHANNEL_ID = "blocker_pause_expiry_alert"
        private const val EXPIRY_NOTIF_ID = 4042
        private const val EXTRA_TRIGGER_EXPIRY = "extra_trigger_expiry"
        private const val EXTRA_FORCE_LOCKDOWN = "extra_force_lockdown"

        fun start(
            context: Context,
            triggerExpiryPrompt: Boolean = false,
            forceLockdownNow: Boolean = false
        ) {
            val intent = Intent(context, AccessibilityPauseGuardService::class.java).apply {
                putExtra(EXTRA_TRIGGER_EXPIRY, triggerExpiryPrompt)
                putExtra(EXTRA_FORCE_LOCKDOWN, forceLockdownNow)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(EXPIRY_NOTIF_ID)
                context.stopService(Intent(context, AccessibilityPauseGuardService::class.java))
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogJob: Job? = null

    @Volatile
    private var currentForegroundPkg: String? = null
    private var lastUsageQueryMs: Long = 0L
    private var lastHomeKickElapsedMs: Long = 0L

    private var activeOverlayView: View? = null
    private var currentOverlayMode: OverlayMode = OverlayMode.NONE

    private enum class OverlayMode { NONE, SETTINGS_BLOCKED, REENABLE_REQUIRED }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        promoteToForeground()
        lastUsageQueryMs = System.currentTimeMillis() - 5_000L
        startWatchdogLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        val snap = AccessibilityPauseState.load(this)

        // If Accessibility already reconnected, clean up immediately
        if (AccessibilityPauseController.isAccessibilityServiceEnabled(
                this,
                BlockerAccessibilityService::class.java
            )
        ) {
            AccessibilityPauseState.clearAllOnAccessibilityReconnected(this)
            removeOverlayOnMain()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!snap.isGuardRequired()) {
            removeOverlayOnMain()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.getBooleanExtra(EXTRA_FORCE_LOCKDOWN, false) == true) {
            showOverlayOnMain(OverlayMode.SETTINGS_BLOCKED)
            sendToHomeScreen()
        } else if (intent?.getBooleanExtra(EXTRA_TRIGGER_EXPIRY, false) == true) {
            postExpiryHighPriorityNotification()
            if (!AccessibilityPauseState.isInsideReEnableSettingsWindow()) {
                showOverlayOnMain(OverlayMode.REENABLE_REQUIRED)
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (AccessibilityPauseState.load(this).isGuardRequired()) {
            start(this)
        }
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        serviceScope.cancel()
        removeOverlayOnMain()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val snap = AccessibilityPauseState.load(this)
        val contentText = if (snap.isAwaitingReEnable) {
            "10-minute pause ended. Tap to re-enable Accessibility."
        } else {
            "Accessibility paused (10m) — Settings & Uninstall Shield Active"
        }

        val reEnableIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            this, 101, reEnableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock) // Replace with app icon drawable
            .setContentTitle("Blocker Uninstall Shield Active")
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        }
    }

    private fun startWatchdogLoop() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = serviceScope.launch {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            while (isActive) {
                val nowMs = System.currentTimeMillis()
                val snap = AccessibilityPauseState.load(this@AccessibilityPauseGuardService)

                // 1. Check if Accessibility reconnected
                if (AccessibilityPauseController.isAccessibilityServiceEnabled(
                        this@AccessibilityPauseGuardService,
                        BlockerAccessibilityService::class.java
                    )
                ) {
                    AccessibilityPauseState.clearAllOnAccessibilityReconnected(
                        this@AccessibilityPauseGuardService
                    )
                    removeOverlayOnMain()
                    stopSelf()
                    return@launch
                }

                // 2. Check if 10-minute timer just expired (backup to AlarmManager)
                if (snap.pauseUntilEpochMs > 0L && nowMs >= snap.pauseUntilEpochMs) {
                    AccessibilityPauseState.markPauseExpiredAwaitingReEnable(
                        this@AccessibilityPauseGuardService
                    )
                    val autoDone = AccessibilityPauseController.tryAutoReEnableAccessibility(
                        this@AccessibilityPauseGuardService,
                        BlockerAccessibilityService::class.java
                    )
                    if (autoDone) {
                        AccessibilityPauseState.clearAllOnAccessibilityReconnected(
                            this@AccessibilityPauseGuardService
                        )
                        removeOverlayOnMain()
                        stopSelf()
                        return@launch
                    } else {
                        postExpiryHighPriorityNotification()
                        promoteToForeground()
                    }
                }

                val updatedSnap = AccessibilityPauseState.load(this@AccessibilityPauseGuardService)
                if (!updatedSnap.isGuardRequired(nowMs)) {
                    removeOverlayOnMain()
                    stopSelf()
                    return@launch
                }

                // 3. Only poll UsageStats when screen is ON
                if (pm.isInteractive) {
                    updateForegroundPackage(usm, nowMs)
                    val fgPkg = currentForegroundPkg?.lowercase(Locale.ROOT)
                    val inReEnableWindow = AccessibilityPauseState.isInsideReEnableSettingsWindow()

                    val isAlwaysBlockedUninstallPkg = fgPkg != null && isHardBlockedInstallerOrStore(fgPkg)
                    val isSettingsBlockedNow = fgPkg != null && isSettingsOrSecurityPkg(fgPkg) && !inReEnableWindow

                    when {
                        isAlwaysBlockedUninstallPkg || isSettingsBlockedNow -> {
                            showOverlayOnMain(OverlayMode.SETTINGS_BLOCKED)
                            val elapsed = SystemClock.elapsedRealtime()
                            if (elapsed - lastHomeKickElapsedMs >= 250L) {
                                lastHomeKickElapsedMs = elapsed
                                sendToHomeScreen()
                            }
                        }
                        updatedSnap.isAwaitingReEnable && !inReEnableWindow -> {
                            // Timer expired & user hasn't tapped Re-Enable -> show mandatory re-enable lock
                            showOverlayOnMain(OverlayMode.REENABLE_REQUIRED)
                        }
                        else -> {
                            if (currentOverlayMode != OverlayMode.NONE && !updatedSnap.isAwaitingReEnable) {
                                removeOverlayOnMain()
                            }
                        }
                    }
                }

                delay(100L)
            }
        }
    }

    private fun updateForegroundPackage(usm: UsageStatsManager, nowMs: Long) {
        val startMs = (lastUsageQueryMs - 500L).coerceAtLeast(nowMs - 60_000L)
        runCatching {
            val events = usm.queryEvents(startMs, nowMs)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isResumed = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    @Suppress("DEPRECATION") (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
                if (isResumed && !event.packageName.isNullOrBlank()) {
                    latestPkg = event.packageName
                }
            }
            if (latestPkg != null) {
                currentForegroundPkg = latestPkg
            } else if (currentForegroundPkg == null) {
                val stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    nowMs - 120_000L,
                    nowMs
                )
                currentForegroundPkg = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            }
        }
        lastUsageQueryMs = nowMs
    }

    private fun isHardBlockedInstallerOrStore(pkg: String): Boolean {
        return pkg.contains("packageinstaller") ||
            pkg == "com.android.vending" ||
            pkg == "com.miui.securitycenter" ||
            pkg == "com.samsung.android.lool" ||
            pkg == "com.coloros.safecenter" ||
            pkg == "com.iqoo.secure" ||
            pkg == "com.transsion.phonemaster" ||
            pkg == "com.transsion.himanager"
    }

    private fun isSettingsOrSecurityPkg(pkg: String): Boolean {
        return pkg == "com.android.settings" || pkg.contains("settings")
    }

    private fun sendToHomeScreen() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching { startActivity(home) }
    }

    private fun showOverlayOnMain(mode: OverlayMode) {
        if (currentOverlayMode == mode && activeOverlayView != null) return
        mainHandler.post {
            if (!Settings.canDrawOverlays(this)) return@post
            removeOverlayInternal()
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = buildLockdownOverlayView(mode)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.CENTER
            }
            runCatching {
                wm.addView(view, params)
                activeOverlayView = view
                currentOverlayMode = mode
            }
        }
    }

    private fun removeOverlayOnMain() {
        mainHandler.post { removeOverlayInternal() }
    }

    private fun removeOverlayInternal() {
        val existing = activeOverlayView ?: return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeViewImmediate(existing) }
        activeOverlayView = null
        currentOverlayMode = OverlayMode.NONE
    }

    private fun buildLockdownOverlayView(mode: OverlayMode): View {
        val dp = { v: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
            ).toInt()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#070512"))
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(26), dp(22), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1D153B"), Color.parseColor("#0F0B20"))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.parseColor("#8B5CF6"))
            }
        }

        val titleText = TextView(this).apply {
            text = if (mode == OverlayMode.REENABLE_REQUIRED) {
                "10-Minute Pause Ended"
            } else {
                "Settings & Uninstall Protection Active"
            }
            setTextColor(Color.parseColor("#F5F3FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val bodyText = TextView(this).apply {
            text = if (mode == OverlayMode.REENABLE_REQUIRED) {
                "Your 10-minute Accessibility pause has finished. Tap below to turn Blocker Accessibility back ON and restore full protection."
            } else {
                "System Settings, Play Store, and App Uninstallation are strictly locked while Accessibility is paused for Banking apps."
            }
            setTextColor(Color.parseColor("#B4ABD4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(22))
        }

        val primaryBtn = TextView(this).apply {
            text = if (mode == OverlayMode.REENABLE_REQUIRED) {
                "Re-enable Accessibility Now"
            } else {
                "End Pause & Re-enable"
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#9F75FF"), Color.parseColor("#6D28D9"))
            ).apply { cornerRadius = dp(14).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                removeOverlayInternal()
                AccessibilityPauseController.openAccessibilitySettingsForReEnable(
                    this@AccessibilityPauseGuardService
                )
            }
        }

        card.addView(titleText)
        card.addView(bodyText)
        card.addView(primaryBtn)

        if (mode == OverlayMode.SETTINGS_BLOCKED) {
            val homeBtn = TextView(this).apply {
                text = "Return to Home"
                setTextColor(Color.parseColor("#D8B4FE"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#16102E"))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.parseColor("#4C3B87"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
                setOnClickListener {
                    sendToHomeScreen()
                    removeOverlayInternal()
                }
            }
            card.addView(homeBtn)
        }

        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return root
    }

    private fun postExpiryHighPriorityNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            this, 202, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("10-Minute Pause Ended")
            .setContentText("Tap to re-enable Blocker Accessibility protection.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(EXPIRY_NOTIF_ID, notif) }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Uninstall Shield (Accessibility Pause)",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Accessibility Pause Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }
}