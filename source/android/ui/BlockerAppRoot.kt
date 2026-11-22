package com.blocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

    LaunchedEffect(com.blocker.MainActivity.requestedTab) {
        if (com.blocker.MainActivity.requestedTab == "focus") {
            currentTab = NavTab.FOCUS
            com.blocker.MainActivity.requestedTab = null
        }
    }
    val isActivated = remember { repo.isInitialSetupActivated() }
    var showOnboarding by remember { mutableStateOf(!isActivated) }

    DisposableEffect(showOnboarding) {
        com.blocker.PermissionFlowTracker.isOnPermissionSetupScreen = showOnboarding
        onDispose {
            com.blocker.PermissionFlowTracker.isOnPermissionSetupScreen = false
        }
    }
    val isAppLocked = securityLock.isParentLockEnabled && !securityLock.isAppUnlocked

    // Back button interception during active lockouts
    BackHandler(enabled = study.active || focus.active || isAppLocked) {
        // Prevent exiting during active lockout
    }

    NordicTexturedScaffold(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
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
                                repo.setInitialSetupActivated(true)
                                repo.toggleProtection(true)
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
                                    animationSpec = tween(180),
                                    label = "tab_crossfade"
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
    val navShape = RoundedCornerShape(28.dp)
    val dockBorder = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFFA78BFA).copy(alpha = 0.50f),
                Color(0xFF3B2B6E).copy(alpha = 0.25f),
                Color(0xFF05030A).copy(alpha = 0.90f)
            )
        )
    }
    val dockBg = remember {
        Brush.verticalGradient(
            listOf(Color(0xFF1B1438), Color(0xFF100B24), Color(0xFF080514))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.shadowElevation = 18.dp.toPx()
                    this.shape = navShape
                    this.clip = false
                    this.spotShadowColor = Color(0xFF7C3AED)
                    this.ambientShadowColor = Color(0xFF7C3AED)
                }
                .clip(navShape)
                .background(dockBg)
                .border(1.2.dp, dockBorder, navShape)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFF5F3FF) else Color(0xFF7E74A8),
                    animationSpec = tween(180),
                    label = "dock_icon_tint"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFF5F3FF) else Color(0xFF7E74A8),
                    animationSpec = tween(180),
                    label = "dock_label_color"
                )
                val pillWidth by animateDpAsState(
                    targetValue = if (isSelected) 48.dp else 40.dp,
                    animationSpec = tween(180),
                    label = "dock_pill_width"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val activePillBrush = remember {
                        Brush.verticalGradient(
                            listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                        )
                    }
                    val activePillBorder = remember {
                        Brush.verticalGradient(
                            listOf(Color(0xFFA78BFA).copy(alpha = 0.8f), Color(0xFF4C1D95).copy(alpha = 0.3f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = pillWidth, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .graphicsLayer {
                                            shadowElevation = 4.dp.toPx()
                                            spotShadowColor = Color(0xFF8B5CF6).copy(alpha = 0.22f)
                                        }
                                        .background(activePillBrush)
                                        .border(1.2.dp, activePillBorder, RoundedCornerShape(15.dp))
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = iconTint,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        color = labelColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
