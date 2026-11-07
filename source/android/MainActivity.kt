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

    private var promptedAdminOnce = false

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized) {
            repo.refreshAll()
        }
        if (!promptedAdminOnce) {
            promptedAdminOnce = true
            checkDeviceAdminPrompt()
        }
    }

    private fun checkDeviceAdminPrompt() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        val admin1 = ComponentName(this, BlockerDeviceAdminReceiver::class.java)
        val admin2 = ComponentName(this, MyDeviceAdminReceiver::class.java)
        val isActive = dpm.isAdminActive(admin1) || dpm.isAdminActive(admin2)
        if (!isActive) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin1)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enforces active protection and anti-tamper safeguards.")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
