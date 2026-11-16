package com.blocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.blocker.data.RuleSnapshotHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FocusNotificationManager {

    const val CHANNEL_FOCUS_TIMER = "blocker_focus_timer"
    const val CHANNEL_FOCUS_ALERTS = "blocker_focus_alerts"

    const val NOTIFICATION_ID_START = 2001
    const val NOTIFICATION_ID_TIMER = 2002
    const val NOTIFICATION_ID_FINISH = 2003

    @Volatile
    private var channelsCreated = false

    @Volatile
    private var activeSessionId: String? = null

    @Volatile
    private var sessionStartMs: Long = 0L

    @Volatile
    private var sessionEndMs: Long = 0L

    @Volatile
    private var sessionDurationMs: Long = 0L

    @Volatile
    private var sessionLabel: String = "Focus Session"

    @Volatile
    private var wasActive: Boolean = false

    @Volatile
    private var isCancelledEarly: Boolean = false

    @Volatile
    private var lastTimerNotifyMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun initChannels(context: Context) {
        if (channelsCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val timerChannel = NotificationChannel(
                CHANNEL_FOCUS_TIMER,
                "Focus Mode Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live countdown timer for active focus sessions"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_FOCUS_ALERTS,
                "Focus Mode Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for when focus mode starts and completes"
                setShowBadge(true)
            }

            nm.createNotificationChannel(timerChannel)
            nm.createNotificationChannel(alertsChannel)
        }
        channelsCreated = true
    }

    fun onSessionCancelled(context: Context) {
        isCancelledEarly = true
        cancelTimer(context)
        activeSessionId = null
        wasActive = false
        lastTimerNotifyMs = 0L
    }

    fun cancelTimer(context: Context) {
        lastTimerNotifyMs = 0L
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID_TIMER)
        }
    }

    fun onTick(context: Context) {
        runCatching {
            initChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val now = System.currentTimeMillis()

            val snap = RuleSnapshotHolder.getFreshSnapshot(context)
            val isManual = snap.isManualFocusRunningNow(now)
            val activeSched = snap.getActiveScheduleNow()
            val activePrayer = snap.getActivePrayerSlotNow()
            val isStudy = snap.isStudyModeRunningNow(context)

            var runningSessionId: String? = null
            var targetEndMs = 0L
            var label = "Focus Session"

            if (isManual) {
                targetEndMs = snap.focusUntilMs
                label = "Quick Focus"
                runningSessionId = "manual_${targetEndMs}"
            } else if (activeSched != null) {
                val remMs = activeSched.getRemainingMillisNow()
                targetEndMs = now + remMs
                label = activeSched.label.ifBlank { "Scheduled Focus" }
                runningSessionId = "sched_${activeSched.id}_${activeSched.endMin}"
            } else if (activePrayer != null) {
                val remMs = BlockerStore.prayerRemainingMs(context)
                targetEndMs = now + remMs
                label = "${activePrayer.nameEn} Prayer"
                runningSessionId = "prayer_${activePrayer.id}"
            } else if (isStudy) {
                val remMs = BlockerStore.studyRemainingMs(context)
                targetEndMs = now + remMs
                label = "Study Session"
                runningSessionId = "study_${targetEndMs / 60_000L}"
            }

            if (runningSessionId != null && targetEndMs > now) {
                // Focus session IS active
                if (activeSessionId != runningSessionId) {
                    // New session started
                    activeSessionId = runningSessionId
                    sessionStartMs = now
                    sessionEndMs = targetEndMs
                    sessionDurationMs = (sessionEndMs - sessionStartMs).coerceAtLeast(60_000L)
                    sessionLabel = label
                    wasActive = true
                    isCancelledEarly = false

                    // 1. Post start alert on CHANNEL_FOCUS_ALERTS
                    showStartAlert(context, nm)

                    // 2. Post notification once at session start
                    updateFocusNotification(context, nm)
                    lastTimerNotifyMs = now
                }
            } else {
                // No focus session currently active
                if (wasActive) {
                    cancelTimer(context)
                    if (!isCancelledEarly) {
                        // Natural completion!
                        showCompletionAlert(context, nm)
                    }
                    wasActive = false
                    activeSessionId = null
                    isCancelledEarly = false
                    lastTimerNotifyMs = 0L
                }
            }
        }
    }

    private fun showStartAlert(context: Context, nm: NotificationManager) {
        val formattedDuration = formatDuration(sessionDurationMs)
        val formattedEndTime = formatClockTime(sessionEndMs)

        val pendingIntent = createContentIntent(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Focus Mode Started")
            .setContentText("Session active for $formattedDuration (Ends at $formattedEndTime)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(4000L)
            .setContentIntent(pendingIntent)

        nm.notify(NOTIFICATION_ID_START, builder.build())

        mainHandler.postDelayed({
            runCatching { nm.cancel(NOTIFICATION_ID_START) }
        }, 4000L)
    }

    private fun updateFocusNotification(context: Context, nm: NotificationManager) {
        val formattedEndTime = formatClockTime(sessionEndMs)
        val pendingIntent = createContentIntent(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Focus Mode Active")
            .setContentText("Focus mode will be off at $formattedEndTime")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        nm.notify(NOTIFICATION_ID_TIMER, builder.build())
    }

    private fun showCompletionAlert(context: Context, nm: NotificationManager) {
        val formattedDuration = formatDuration(sessionDurationMs)
        val pendingIntent = createContentIntent(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Focus Mode Completed")
            .setContentText("Your $formattedDuration focus session has finished. Normal mode restored.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        nm.notify(NOTIFICATION_ID_FINISH, builder.build())
    }

    private fun createContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("tab", "focus")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, NOTIFICATION_ID_TIMER, intent, flags)
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(1)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    private fun formatClockTime(epochMillis: Long): String {
        return runCatching {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            sdf.format(Date(epochMillis))
        }.getOrDefault("")
    }

    private fun formatHms(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }
}
