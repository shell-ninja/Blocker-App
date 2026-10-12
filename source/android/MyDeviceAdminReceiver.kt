package com.blocker

/**
 * Device Admin Receiver dedicated to Device Owner management.
 * Inherits all anti-tamper, deactivation detection, and guard logic from
 * [BlockerDeviceAdminReceiver], while referencing `device_admin_policies.xml`
 * to support full Device Owner provisioning via ADB (`dpm set-device-owner`).
 */
class MyDeviceAdminReceiver : BlockerDeviceAdminReceiver()
