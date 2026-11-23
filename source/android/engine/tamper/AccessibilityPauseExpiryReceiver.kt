package com.blocker.engine.tamper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blocker.BlockerAccessibilityService

class AccessibilityPauseExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        AccessibilityPauseState.markPauseExpiredAwaitingReEnable(appCtx)

        val reEnabled = AccessibilityPauseController.tryAutoReEnableAccessibility(
            appCtx,
            BlockerAccessibilityService::class.java
        )
        if (reEnabled) {
            AccessibilityPauseState.clearAllOnAccessibilityReconnected(appCtx)
            AccessibilityPauseGuardService.stop(appCtx)
        } else {
            // Keep Guard Service alive and switch it into Mandatory Re-Enable Lock mode
            AccessibilityPauseGuardService.start(appCtx, triggerExpiryPrompt = true)
        }
    }
}
