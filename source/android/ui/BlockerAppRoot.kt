package com.blocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.data.BlockerRepository
import com.blocker.ui.screens.*
import com.blocker.ui.theme.*

enum class NavTab(val label: String, val icon: String) {
    HOME("Home", "🛡️"),
    FOCUS("Focus", "🎯"),
    APPS("Apps", "📱"),
    WEB("Web", "🌐"),
    SETTINGS("Settings", "⚙️")
}

@Composable
fun BlockerAppRoot(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val perms by repo.permissionsState.collectAsState()
    val focus by repo.focusState.collectAsState()
    val study by repo.studyState.collectAsState()

    var currentTab by remember { mutableStateOf(NavTab.HOME) }
    var showOnboarding by remember { mutableStateOf(!perms.accessibility) }

    // Intercept back button during study/focus lock
    BackHandler(enabled = study.active || focus.active) {
        // Prevent exiting or backing out during active phone lockout
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CyberBg,
        bottomBar = {
            if (!showOnboarding && !study.active) {
                CyberBottomNav(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CyberBg)
        ) {
            when {
                showOnboarding -> {
                    OnboardingScreen(
                        repo = repo,
                        onComplete = { showOnboarding = false }
                    )
                }
                study.active -> {
                    // Lock directly to Focus Screen during study session
                    FocusScreen(repo = repo)
                }
                else -> {
                    Crossfade(
                        targetState = currentTab,
                        animationSpec = tween(120),
                        label = "tab_fade"
                    ) { tab ->
                        when (tab) {
                            NavTab.HOME -> HomeScreen(repo = repo, onNavigateToFocus = { currentTab = NavTab.FOCUS })
                            NavTab.FOCUS -> FocusScreen(repo = repo)
                            NavTab.APPS -> AppBlockerScreen(repo = repo)
                            NavTab.WEB -> BlocklistScreen(repo = repo)
                            NavTab.SETTINGS -> SettingsScreen(repo = repo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CyberBottomNav(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CyberBgAlt)
            .border(1.dp, CyberCardBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab.values().forEach { tab ->
            val isSelected = selectedTab == tab
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.icon,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    color = if (isSelected) CyberAccentGlow else CyberTextSub,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                )
            }
        }
    }
}
