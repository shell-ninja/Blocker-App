package com.blocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        BlockerStore.prefs(context).edit().putBoolean("admin_lost", false).apply()
    }

    // Shown in the system's deactivation confirmation dialog. Android does not allow vetoing here.
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        BlockerStore.incr(context, "tamper")
        return if (BlockerStore.active(context) && !BlockerStore.guardOpen(context, "shield"))
            "Blocker protection is locked. Deactivating admin will be logged and requires the delay timer."
        else "Deactivating will disable Blocker's uninstall protection."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val p = BlockerStore.prefs(context)
        p.edit().putBoolean("admin_lost", true).apply()
        if (!BlockerStore.active(context)) return
        BlockerStore.incr(context, "tamper")
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel("blocker_alerts", "Protection alerts", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val n = NotificationCompat.Builder(context, "blocker_alerts")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Blocker protection weakened")
                .setContentText("Device admin was deactivated. Re-enable it in Blocker.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(1001, n)
        }
    }
}
