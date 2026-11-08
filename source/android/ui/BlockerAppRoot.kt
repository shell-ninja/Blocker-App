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
import com.blocker.ui.components.BrandTopBar
import com.blocker.ui.components.CyberIcons
import com.blocker.ui.components.NordicTexturedScaffold
import com.blocker.ui.screens.*
import com.blocker.ui.theme.NordicColors

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
    val securityLock by repo.securityLockState.collectAsState()

    var currentTab by remember { mutableStateOf(NavTab.HOME) }
    val isOnboardingDone = remember { repo.isOnboardingCompleted() }
    var showOnboarding by remember { mutableStateOf(!isOnboardingDone && !perms.accessibility) }
    val isAppLocked = securityLock.isParentLockEnabled && !securityLock.isAppUnlocked

    // Back button interception during active lockouts
    BackHandler(enabled = study.active || focus.active || isAppLocked) {
        // Prevent exiting during active lockout
    }

    NordicTexturedScaffold(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (!showOnboarding && !study.active && !isAppLocked) {
                    CyberBottomNav(
                        selectedTab = currentTab,
                        onTabSelected = { currentTab = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    isAppLocked -> {
                        ParentLockScreen(repo = repo)
                    }
                    showOnboarding -> {
                        OnboardingScreen(
                            repo = repo,
                            onComplete = {
                                repo.setOnboardingCompleted(true)
                                showOnboarding = false
                            }
                        )
                    }
                    study.active -> {
                        FocusScreen(repo = repo)
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            BrandTopBar(
                                isProtected = protection.isActive,
                                modifier = Modifier.fillMaxWidth()
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
}

@Composable
fun CyberBottomNav(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val navShape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(navShape)
            .background(NordicColors.CardSurface)
            .border(1.dp, NordicColors.HairlineBorder, navShape)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab.values().forEach { tab ->
            val isSelected = selectedTab == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) NordicColors.AccentPrimary
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    color = if (isSelected) NordicColors.TextPrimary else NordicColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
