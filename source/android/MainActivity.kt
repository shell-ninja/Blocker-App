package com.blocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.blocker.data.BlockerRepository
import com.blocker.ui.BlockerAppRoot
import com.blocker.ui.theme.BlockerTheme
import com.blocker.ui.theme.CyberBg

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isLaunchingExternal: Boolean = false
    }

    private lateinit var repo: BlockerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        repo = BlockerRepository(this)

        setContent {
            BlockerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberBg
                ) {
                    BlockerAppRoot(repo = repo)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isLaunchingExternal = false
        if (::repo.isInitialized) {
            repo.refreshAll()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLaunchingExternal) {
            // User intentionally navigated to a permission or settings screen from inside Blocker.
            // Keep the activity alive so the user can complete setup and return cleanly.
            return
        }
        // Anti-tamper minimize defense: close completely and purge task when user presses Home or Recents
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAndRemoveTask()
    }
}
