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
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Persistent foreground service to ensure the Blocker app process remains alive with
 * foreground priority in the background, preventing Android low memory killer and
 * OEM battery managers (MIUI, Samsung OneUI, HiOS, ColorOS) from killing the service.
 * Also runs scheduled background update checks every 2h (24h backoff when update pending).
 */
class BlockerForegroundService : Service {
    constructor() : super()

    companion object {
        const val CHANNEL_ID = "blocker_protection_channel"
        const val UPDATE_CHANNEL_ID = "blocker_updates"
        const val NOTIFICATION_ID = 1001
        const val UPDATE_NOTIFICATION_ID = 1002

        const val REGULAR_UPDATE_COOLDOWN_MS = 2L * 60 * 60 * 1000L  // 2 hours
        const val PENDING_UPDATE_COOLDOWN_MS = 24L * 60 * 60 * 1000L // 24 hours

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

    private val updateExecutor = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val updateCheckRunnable = object : Runnable {
        override fun run() {
            checkUpdateSchedule()
            handler.postDelayed(this, 15 * 60 * 1000L) // evaluate schedule every 15 minutes
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        handler.postDelayed(updateCheckRunnable, 30_000L) // initial check 30s after service launch
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateCheckRunnable)
        runCatching { updateExecutor.shutdownNow() }
        super.onDestroy()
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
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
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
            if (nm.getNotificationChannel(UPDATE_CHANNEL_ID) == null) {
                val updateChannel = NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "Blocker Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new releases and updates."
                }
                nm.createNotificationChannel(updateChannel)
            }
        }
    }

    private fun checkUpdateSchedule() {
        val prefs = BlockerStore.prefs(this)
        val lastCheck = prefs.getLong("last_update_check_ts", 0L)
        val hasPendingUpdate = prefs.getBoolean("has_pending_update", false)
        val cooldownMs = if (hasPendingUpdate) PENDING_UPDATE_COOLDOWN_MS else REGULAR_UPDATE_COOLDOWN_MS
        val now = System.currentTimeMillis()
        if (now - lastCheck >= cooldownMs) {
            updateExecutor.execute {
                runBackgroundUpdateCheck()
            }
        }
    }

    private fun runBackgroundUpdateCheck() {
        try {
            val url = URL("https://api.github.com/repos/shell-ninja/Blocker-App/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Blocker-App")
            }
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()
                val json = JSONObject(resp)
                val tagName = json.optString("tag_name", "")
                val version = tagName.removePrefix("v").removePrefix("V")
                val currentVersion = runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty().trim()
                }.getOrDefault("")

                val hasUpdate = isNewerVersion(version, currentVersion)
                val prefs = BlockerStore.prefs(this)
                val now = System.currentTimeMillis()

                prefs.edit()
                    .putLong("last_update_check_ts", now)
                    .putBoolean("has_pending_update", hasUpdate)
                    .apply()

                if (hasUpdate) {
                    val lastNotified = prefs.getString("last_notified_version", null)
                    if (lastNotified != version) {
                        prefs.edit().putString("last_notified_version", version).apply()
                        showUpdateNotification(tagName, version)
                    }
                }
            }
            conn.disconnect()
        } catch (_: Throwable) {
            // Network failure / offline: safe ignore, will retry
        }
    }

    private fun isNewerVersion(v1: String, v2: String): Boolean {
        if (v1.isEmpty() || v2.isEmpty()) return false
        val p1 = v1.split('-', '+')[0].split('.').map { it.toIntOrNull() ?: 0 }
        val p2 = v2.split('-', '+')[0].split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 > num2) return true
            if (num1 < num2) return false
        }
        return false
    }

    private fun showUpdateNotification(tagName: String, version: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_update", true)
                putExtra("update_version", version)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(this, UPDATE_NOTIFICATION_ID, launchIntent, flags)
            val builder = NotificationCompat.Builder(this, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Blocker Update Available ($tagName)")
                .setContentText("Blocker $tagName is now available with new features and fixes. Tap to update.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Blocker $tagName is now available with new features and fixes. Tap to update."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            nm.notify(UPDATE_NOTIFICATION_ID, builder.build())
        } catch (_: Throwable) {}
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
