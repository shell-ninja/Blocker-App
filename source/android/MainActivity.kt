package com.blocker

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

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized) {
            repo.refreshAll()
        }
    }
}
