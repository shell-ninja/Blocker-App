package com.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Persistent foreground service to ensure the Blocker app process remains alive with
 * foreground priority in the background, preventing Android low memory killer and
 * OEM battery managers (MIUI, Samsung OneUI, HiOS, ColorOS) from killing the service.
 */
class BlockerForegroundService : Service {
    constructor() : super()

    companion object {
        const val CHANNEL_ID = "blocker_protection_channel"
        const val NOTIFICATION_ID = 1001

        fun start(ctx: Context) {
            runCatching {
                val intent = Intent(ctx, BlockerForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            }
        }

        fun stop(ctx: Context) {
            runCatching {
                val intent = Intent(ctx, BlockerForegroundService::class.java)
                ctx.stopService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            // Fallback for devices without type declaration
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Blocker Protection",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps Blocker running reliably in the background."
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            PendingIntent.getActivity(this, 0, launchIntent, flags)
        } else null

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val appIconRes = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_lock_idle_lock

        builder.setContentTitle("Blocker is active")
            .setContentText("Continuous distraction and adult content protection.")
            .setSmallIcon(appIconRes)
            .setOngoing(true)
            .setAutoCancel(false)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        return builder.build()
    }
}
