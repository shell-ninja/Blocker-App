package com.blocker

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import androidx.core.app.NotificationCompat

/**
 * Helper to manage Device Owner operations, Safe Boot restriction, and deactivation.
 */
object DeviceOwnerHelper {
    fun getAdminComponent(context: Context): ComponentName {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val myAdmin = ComponentName(context, MyDeviceAdminReceiver::class.java)
        val blockerAdmin = ComponentName(context, BlockerDeviceAdminReceiver::class.java)
        return when {
            dpm?.isAdminActive(myAdmin) == true -> myAdmin
            dpm?.isAdminActive(blockerAdmin) == true -> blockerAdmin
            else -> myAdmin
        }
    }

    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.isDeviceOwnerApp(context.packageName) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * When the app is a Device Owner, enforce DISALLOW_SAFE_BOOT so users cannot
     * bypass protection by booting into Android Safe Mode (Volume Down during boot).
     * Wrapped in try-catch so normal Device Admin mode is unaffected.
     */
    fun enforceDisallowSafeBoot(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                val candidates = listOf(
                    ComponentName(context, MyDeviceAdminReceiver::class.java),
                    ComponentName(context, BlockerDeviceAdminReceiver::class.java)
                )
                for (admin in candidates) {
                    try {
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                    } catch (_: Exception) {}
                    // Freeze / hide scheduled power-off packages so RTC hardware alarms cannot trigger
                    val scheduledPowerPackages = listOf(
                        "com.mediatek.schpwronoff",
                        "com.coloros.oppopowermanager",
                        "com.vivo.scheduledpower"
                    )
                    for (p in scheduledPowerPackages) {
                        try {
                            dpm.setApplicationHidden(admin, p, true)
                        } catch (_: Exception) {}
                    }
                    break
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Clears DISALLOW_SAFE_BOOT user restriction when deactivating Device Owner.
     */
    fun clearDisallowSafeBoot(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
            val candidates = listOf(
                ComponentName(context, MyDeviceAdminReceiver::class.java),
                ComponentName(context, BlockerDeviceAdminReceiver::class.java)
            )
            for (admin in candidates) {
                try {
                    dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                } catch (_: Exception) {}
                val scheduledPowerPackages = listOf(
                    "com.mediatek.schpwronoff",
                    "com.coloros.oppopowermanager",
                    "com.vivo.scheduledpower"
                )
                for (p in scheduledPowerPackages) {
                    try {
                        dpm.setApplicationHidden(admin, p, false)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Deactivates Device Owner: clears DISALLOW_SAFE_BOOT, removes restrictions,
     * and calls dpm.clearDeviceOwnerApp.
     */
    fun removeDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: throw IllegalStateException("DevicePolicyManager service is unavailable.")
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            throw IllegalStateException("App is not set as device owner.")
        }

        // 1. Clear DISALLOW_SAFE_BOOT restriction
        clearDisallowSafeBoot(context)

        // 2. Clear any additional restrictions and uninstall block
        try {
            val candidates = listOf(
                ComponentName(context, MyDeviceAdminReceiver::class.java),
                ComponentName(context, BlockerDeviceAdminReceiver::class.java)
            )
            for (admin in candidates) {
                try {
                    dpm.setUninstallBlocked(admin, context.packageName, false)
                    dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
                    dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 3. Clear device owner status in Android OS
        dpm.clearDeviceOwnerApp(context.packageName)
        return true
    }
}

open class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        const val NOTIFICATION_ID_ADMIN_ALERT = 1002
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        DeviceOwnerHelper.enforceDisallowSafeBoot(context)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        BlockerStore.prefs(context).edit().putBoolean("admin_lost", false).apply()
        DeviceOwnerHelper.enforceDisallowSafeBoot(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        DeviceOwnerHelper.enforceDisallowSafeBoot(context)
    }

    // Both Protection and the Uninstall & settings shield must be on for any kick/cancel action here —
    // this is what lets the app be uninstalled normally once the user has deliberately turned both off.
    private fun guardEngaged(context: Context): Boolean =
        BlockerStore.active(context) && BlockerStore.shield(context) && !BlockerStore.guardOpen(context, "shield")

    // Shown in the system's deactivation confirmation dialog. Android does not allow vetoing here.
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        if (!guardEngaged(context)) return "Deactivate this device admin app?"
        BlockerStore.incr(context, "tamper")
        // Signal the AccessibilityService via static flag (zero latency, same process)
        BlockerAccessibilityService.pendingAdminKick = true
        // Also write to SharedPreferences as a fallback so the poll loop picks it up
        BlockerStore.prefs(context).edit()
            .putBoolean("admin_disable_requested", true)
            .putLong("admin_disable_requested_ts", System.currentTimeMillis())
            .apply()

        // Immediate mitigation: attempt back/home actions and cancel dialog directly to minimize latency window
        runCatching {
            BlockerAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        runCatching {
            BlockerAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        runCatching { BlockerAccessibilityService.instance?.kickToHomeAndCancel() }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        runCatching { context.startActivity(homeIntent) }

        return "Blocker protection is locked. Deactivating admin will be logged and requires the delay timer."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val p = BlockerStore.prefs(context)
        p.edit().putBoolean("admin_lost", true).apply()
        if (!guardEngaged(context)) return   // let the OS's own uninstall flow continue unimpeded
        // Kick to home immediately — the package installer uninstall dialog may follow
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        runCatching { context.startActivity(homeIntent) }
        runCatching { BlockerAccessibilityService.instance?.kickToHomeAndCancel() }
        // Signal the accessibility service to watch for any follow-up installer dialogs
        BlockerAccessibilityService.pendingAdminKick = true
        p.edit()
            .putBoolean("admin_disable_requested", true)
            .putLong("admin_disable_requested_ts", System.currentTimeMillis())
            .apply()
        BlockerStore.incr(context, "tamper")

        // Immediately prompt to re-enable Device Admin
        runCatching {
            val addAdminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(context, BlockerDeviceAdminReceiver::class.java)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Blocker protection is locked. Device Admin must remain enabled."
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(addAdminIntent)
        }

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
            // Fix Finding 4.1: Use unique ID 1002 to avoid collision with foreground service ID 1001
            nm.notify(NOTIFICATION_ID_ADMIN_ALERT, n)
        }
    }
}
