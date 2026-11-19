package com.blocker

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.blocker.data.BlockerRepository
import com.blocker.ui.BlockerAppRoot
import com.blocker.ui.components.RoyalObsidianBackground
import com.blocker.ui.theme.BlockerTheme
import com.blocker.ui.theme.NordicColors

object PermissionFlowTracker {
    @Volatile
    var isLaunchingPermissionIntent: Boolean = false
    @Volatile
    var lastDndPermissionRequestMs: Long = 0L
    @Volatile
    var lastNotificationAccessRequestMs: Long = 0L
    @Volatile
    var lastBatteryPermissionRequestMs: Long = 0L
    @Volatile
    var lastAccessibilityPermissionRequestMs: Long = 0L
    @Volatile
    var isOnPermissionSetupScreen: Boolean = true
}

class MainActivity : ComponentActivity() {

    companion object {
        var isLaunchingExternal: Boolean
            get() = PermissionFlowTracker.isLaunchingPermissionIntent
            set(value) {
                PermissionFlowTracker.isLaunchingPermissionIntent = value
            }
        @Volatile
        var requestedTab: String? = null
    }

    private lateinit var repo: BlockerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val tab = intent?.getStringExtra("tab")
        if (tab != null) {
            requestedTab = tab
        }

        BlockerStore.autoClampStuckTimers(this)
        repo = BlockerRepository(this)

        setContent {
            BlockerTheme {
                RoyalObsidianBackground(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        BlockerAppRoot(repo = repo)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BlockerStore.isAccessibilityDisabledForBanking(this)) {
            AccessibilityOffUninstallGuardService.start(this)
        }
        BlockerStore.autoClampStuckTimers(this)
        PermissionFlowTracker.isLaunchingPermissionIntent = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (nm?.isNotificationPolicyAccessGranted == true) {
            PermissionFlowTracker.lastDndPermissionRequestMs = 0L
        }
        if (BlockerNotificationListenerService.isGranted(this)) {
            PermissionFlowTracker.lastNotificationAccessRequestMs = 0L
        }
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isIgnoringBatteryOptimizations(packageName) == true) {
            PermissionFlowTracker.lastBatteryPermissionRequestMs = 0L
        }
        val isActivated = BlockerStore.isInitialSetupActivated(this)
        val shouldKeepAlive = !isActivated || PermissionFlowTracker.isOnPermissionSetupScreen || PermissionFlowTracker.isLaunchingPermissionIntent
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.appTasks?.forEach { runCatching { it.setExcludeFromRecents(!shouldKeepAlive) } }
        if (::repo.isInitialized) {
            repo.refreshAll()
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent?.getStringExtra("tab")
        if (tab != null) {
            requestedTab = tab
        }
    }

    override fun onStop() {
        super.onStop()
        val isActivated = BlockerStore.isInitialSetupActivated(this)
        val shouldKeepAlive = !isActivated || PermissionFlowTracker.isOnPermissionSetupScreen || PermissionFlowTracker.isLaunchingPermissionIntent
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (shouldKeepAlive) {
            am?.appTasks?.forEach { runCatching { it.setExcludeFromRecents(false) } }
            // Keep alive and visible in Recents so user can switch between Settings and App
            return
        }
        am?.appTasks?.forEach { runCatching { it.setExcludeFromRecents(true) } }
        if (!isChangingConfigurations) {
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isActivated = BlockerStore.isInitialSetupActivated(this)
        val shouldKeepAlive = !isActivated || PermissionFlowTracker.isOnPermissionSetupScreen || PermissionFlowTracker.isLaunchingPermissionIntent
        if (shouldKeepAlive) return
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val isActivated = BlockerStore.isInitialSetupActivated(this)
        val shouldKeepAlive = !isActivated || PermissionFlowTracker.isOnPermissionSetupScreen
        if (shouldKeepAlive) {
            moveTaskToBack(true)
            return
        }
        finish()
    }
}
