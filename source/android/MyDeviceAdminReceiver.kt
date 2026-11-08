package com.blocker

/**
 * Prachir Device Administrator Receiver.
 * Manages device policy administration and anti-tamper enforcement.
 */
open class PrachirDeviceAdminReceiver : BlockerDeviceAdminReceiver()

/**
 * Device Admin Receiver dedicated to Device Owner management.
 * Inherits all anti-tamper, deactivation detection, and guard logic from
 * [PrachirDeviceAdminReceiver], while referencing `device_admin_policies.xml`
 * to support full Device Owner provisioning via ADB (`dpm set-device-owner`).
 */
class MyDeviceAdminReceiver : PrachirDeviceAdminReceiver()
