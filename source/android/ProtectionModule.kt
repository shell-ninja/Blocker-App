package com.blocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class ProtectionModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    // Device Owner status/actions always use MyDeviceAdminReceiver, NOT BlockerDeviceAdminReceiver.
    // MyDeviceAdminReceiver is a separate, dedicated admin receiver (its own manifest <receiver>,
    // its own device_admin_policies.xml with the fuller policy set Device Owner setup expects) that
    // exists specifically so `adb shell dpm set-device-owner com.blocker/.MyDeviceAdminReceiver` has
    // a distinct target from the regular onboarding-granted admin. Passing the wrong ComponentName
    // to setUninstallBlocked()/addUserRestriction() fails with a SecurityException even when Device
    // Owner provisioning succeeded, since these calls require the exact owner component.
    override fun getName(): String = "ProtectionModule"

    /** Lets the UI show whether hardware-level protection is available/on, without side effects. */
    @ReactMethod
    fun getStatus(promise: Promise) {
        val result = com.facebook.react.bridge.Arguments.createMap()
        try {
            val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val packageName = reactContext.packageName
            val isDeviceOwner = dpm?.isDeviceOwnerApp(packageName) == true
            var uninstallBlocked = false
            if (isDeviceOwner && dpm != null) {
                val adminComponent = ComponentName(reactContext, MyDeviceAdminReceiver::class.java)
                uninstallBlocked = runCatching { dpm.isUninstallBlocked(adminComponent, packageName) }.getOrDefault(false)
            }
            result.putBoolean("isDeviceOwner", isDeviceOwner)
            result.putBoolean("uninstallBlocked", uninstallBlocked)
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("PROTECTION_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setProtectionMode(enabled: Boolean, promise: Promise) {
        try {
            val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm == null) {
                promise.reject("DPM_UNAVAILABLE", "DevicePolicyManager service is unavailable.")
                return
            }

            val packageName = reactContext.packageName
            if (!dpm.isDeviceOwnerApp(packageName)) {
                promise.reject("NOT_DEVICE_OWNER", "App is not set as device owner.")
                return
            }

            val adminComponent = ComponentName(reactContext, MyDeviceAdminReceiver::class.java)

            // Block or unblock package uninstallation
            dpm.setUninstallBlocked(adminComponent, packageName, enabled)

            // Toggle debugging features and app control restrictions (e.g. adb uninstall, clearing data/cache)
            if (enabled) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            }

            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("PROTECTION_ERROR", e.message, e)
        }
    }
}
