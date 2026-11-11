package com.blocker

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
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
}

class MainActivity : ComponentActivity() {

    companion object {
        var isLaunchingExternal: Boolean
            get() = PermissionFlowTracker.isLaunchingPermissionIntent
            set(value) {
                PermissionFlowTracker.isLaunchingPermissionIntent = value
            }
    }

    private lateinit var repo: BlockerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
        BlockerStore.autoClampStuckTimers(this)
        PermissionFlowTracker.isLaunchingPermissionIntent = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (nm?.isNotificationPolicyAccessGranted == true) {
            PermissionFlowTracker.lastDndPermissionRequestMs = 0L
        }
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.appTasks?.forEach { runCatching { it.setExcludeFromRecents(true) } }
        if (::repo.isInitialized) {
            repo.refreshAll()
        }
    }

    override fun onStop() {
        super.onStop()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.appTasks?.forEach { runCatching { it.setExcludeFromRecents(true) } }
        if (!isChangingConfigurations && !PermissionFlowTracker.isLaunchingPermissionIntent) {
            finishAndRemoveTask()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PermissionFlowTracker.isLaunchingPermissionIntent) return
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAndRemoveTask()
    }
}
