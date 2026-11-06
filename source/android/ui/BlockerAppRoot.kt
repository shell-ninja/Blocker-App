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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.CyberIcons
import com.blocker.ui.components.TopHeader
import com.blocker.ui.screens.*
import com.blocker.ui.theme.*

enum class NavTab(val label: String, val icon: ImageVector) {
    HOME("Home", CyberIcons.Home),
    FOCUS("Focus", CyberIcons.Focus),
    APPS("Apps", CyberIcons.Apps),
    WEB("Web", CyberIcons.Web),
    SETTINGS("Settings", CyberIcons.Settings)
}

@Composable
fun BlockerAppRoot(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val perms by repo.permissionsState.collectAsState()
    val focus by repo.focusState.collectAsState()
    val study by repo.studyState.collectAsState()
    val protection by repo.protectionState.collectAsState()

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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CyberBg)
                    ) {
                        TopHeader(
                            isLive = protection.isActive,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
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
    }
}

@Composable
fun CyberBottomNav(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberBg)
            .border(width = 1.dp, color = CyberCardBorder)
            .padding(top = 8.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab.values().forEach { tab ->
            val isSelected = selectedTab == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            if (isSelected) CyberAccent.copy(alpha = 0.22f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) CyberAccentGlow else CyberTextSub,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    color = if (isSelected) CyberAccentGlow else CyberTextSub,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
