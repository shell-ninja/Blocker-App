package com.blocker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.NordicColors

@Composable
fun HomeScreen(
    repo: BlockerRepository,
    onNavigateToFocus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val protection by repo.protectionState.collectAsState()
    val perms by repo.permissionsState.collectAsState()
    val stats by repo.statsState.collectAsState()
    val focus by repo.focusState.collectAsState()
    val study by repo.studyState.collectAsState()
    val distraction by repo.distractionShield.collectAsState()
    val apps by repo.installedApps.collectAsState()
    val domains by repo.blockedDomains.collectAsState()
    val keywords by repo.blockedKeywords.collectAsState()
    val scrollState = rememberScrollState()

    val isHealthy = perms.allGranted

    val todayBlocks = remember(stats) {
        stats.dailyHistory.firstOrNull { it.isToday }?.count
            ?: (stats.appBlockCount + stats.nsfwCount + stats.reelsCount)
    }

    val activeModeName = when {
        focus.active -> "Focus"
        study.active -> "Study"
        else -> "Standard"
    }

    val totalRulesCount = remember(apps, domains, keywords, distraction) {
        val blockedAppsCount = apps.count { it.isBlocked }
        var shieldCount = 0
        if (distraction.blockFbReels) shieldCount++
        if (distraction.blockInstaReels) shieldCount++
        if (distraction.blockYtShorts) shieldCount++
        if (distraction.blockIncognitoMode) shieldCount++
        if (distraction.enforceSafeSearchGuard) shieldCount++
        blockedAppsCount + domains.size + keywords.size + shieldCount
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Delay timer / confirmation banner
        if (protection.pendingUntil > 0L || protection.guardOpenUntil > 0L) {
            LockBar(
                pendingUntil = protection.pendingUntil,
                guardOpenUntil = protection.guardOpenUntil,
                onConfirm = { repo.confirmProtectionOff() },
                onCancel = { repo.cancelProtectionRequest() },
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        // 1. Primary Hero 3D Master Protection Card
        Hero3DCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                ) {
                    PulsingHaloShield(isActive = protection.isActive) {
                        RaisedSquirclePedestal(size = 46.dp) {
                            Icon(
                                imageVector = if (protection.isActive) CyberIcons.Check else CyberIcons.Shield,
                                contentDescription = null,
                                tint = if (protection.isActive) NordicColors.AccentPrimary else NordicColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (protection.isActive) "Protection Active" else "Protection Paused",
                            color = NordicColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (protection.isActive) {
                                "Adult filters, keywords & limits enforced"
                            } else {
                                "Shield is currently paused"
                            },
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                NordicSwitch(
                    checked = protection.isActive,
                    onCheckedChange = { repo.toggleProtection(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. 3-Tile 3D Bento Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile 1: Today's Blocks
            Elevated3DCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    text = "Today's Blocks",
                    color = NordicColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$todayBlocks",
                    color = NordicColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "intercepted",
                    color = NordicColors.AccentSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Tile 2: Active Mode
            Elevated3DCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    text = "Active Mode",
                    color = NordicColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activeModeName,
                    color = NordicColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (focus.active || study.active) "enforced" else "idle",
                    color = if (focus.active || study.active) NordicColors.SuccessMuted else NordicColors.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Tile 3: Active Rules
            Elevated3DCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    text = "Active Rules",
                    color = NordicColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalRulesCount",
                    color = NordicColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "configured",
                    color = NordicColors.AccentSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. 3D Daily Blocked Activity Chart in Recessed3DWell
        Elevated3DCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = CyberIcons.ShieldCheck,
                        contentDescription = null,
                        tint = NordicColors.AccentPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily Blocked Activity",
                        color = NordicColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                StatusPillBadge(
                    label = "Last 7 Days",
                    tone = BadgeTone.OK,
                    small = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Recessed3DWell(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                DailyBlockedActivityGraph(
                    history = stats.dailyHistory
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Quick Distraction Shields Card
        Elevated3DCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RaisedSquirclePedestal(size = 36.dp) {
                        Icon(
                            imageVector = CyberIcons.EyeOff,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Quick Distraction Shields",
                            color = NordicColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Instant short-form video & reels blocking",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 1: Facebook Reels & Videos
            QuickShieldRow(
                title = "Block Facebook Reels & Videos",
                subtitle = "Blocks Reels, Watch feeds, and playing videos",
                icon = CyberIcons.Movie,
                checked = distraction.blockFacebookReels,
                pendingUnlockUntil = distraction.pendingUnlocks["block_fb_reels"] ?: 0L,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_fb_reels") },
                onCheckedChange = { repo.toggleDistraction("block_fb_reels", it) }
            )

            SoftGradientHairlineDivider(modifier = Modifier.padding(vertical = 10.dp))

            // Row 2: Instagram Reels
            QuickShieldRow(
                title = "Block Instagram Reels",
                subtitle = "Blocks the Reels tab and full-screen Reels feed",
                icon = CyberIcons.Movie,
                checked = distraction.blockInstaReels,
                pendingUnlockUntil = distraction.pendingUnlocks["block_insta_reels"] ?: 0L,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_insta_reels") },
                onCheckedChange = { repo.toggleDistraction("block_insta_reels", it) }
            )

            SoftGradientHairlineDivider(modifier = Modifier.padding(vertical = 10.dp))

            // Row 3: Instagram Explore
            QuickShieldRow(
                title = "Block Instagram Explore",
                subtitle = "Blocks the Explore discovery grid and media feed",
                icon = CyberIcons.Search,
                checked = distraction.blockInstaSearch,
                pendingUnlockUntil = distraction.pendingUnlocks["block_insta_search"] ?: 0L,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_insta_search") },
                onCheckedChange = { repo.toggleDistraction("block_insta_search", it) }
            )

            SoftGradientHairlineDivider(modifier = Modifier.padding(vertical = 10.dp))

            // Row 4: YouTube Shorts
            QuickShieldRow(
                title = "Block YouTube Shorts",
                subtitle = "Blocks Shorts player and shorts feed",
                icon = CyberIcons.Movie,
                checked = distraction.blockYtShorts,
                pendingUnlockUntil = distraction.pendingUnlocks["block_yt_shorts"] ?: 0L,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_yt_shorts") },
                onCheckedChange = { repo.toggleDistraction("block_yt_shorts", it) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // System Health Card
        Elevated3DCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = CyberIcons.Shield,
                        contentDescription = null,
                        tint = NordicColors.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Health",
                        color = NordicColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                StatusPillBadge(
                    label = if (isHealthy) "Healthy" else "Attention",
                    tone = if (isHealthy) BadgeTone.OK else BadgeTone.WARN,
                    small = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val healthItems = listOf(
                Triple("Device admin (uninstall guard)", perms.deviceAdmin, "deviceAdmin"),
                Triple("Usage access", perms.usageAccess, "usageAccess"),
                Triple("Display over other apps", perms.overlay, "overlay"),
                Triple("Accessibility service", perms.accessibility, "accessibility"),
                Triple("Do Not Disturb access", perms.dndAccess, "dnd"),
                Triple("Notification access", perms.notificationAccess, "notification"),
                Triple("Unrestricted battery", perms.batteryOptimizationsIgnored, "battery")
            )

            healthItems.forEachIndexed { idx, (label, ok, kind) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !ok) { repo.openPermissionSettings(context, kind) }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (ok) NordicColors.TextSecondary else NordicColors.TextPrimary,
                        fontSize = 13.sp
                    )
                    StatusPillBadge(
                        label = if (ok) "On" else "Fix",
                        tone = if (ok) BadgeTone.OK else BadgeTone.DANGER,
                        small = true
                    )
                }
                if (idx < healthItems.size - 1) {
                    SoftGradientHairlineDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // System Safeguards
        SectionHeader(
            title = "System Safeguards",
            subtitle = "Protects against hardware key tampering and unauthorized shutdowns",
            icon = CyberIcons.Power
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Prevent Power Off Card
        PreventPowerOffCard(
            preventPowerOff = protection.preventPowerOff,
            powerShieldCooldownUntil = protection.powerShieldCooldownUntil,
            onCheckedChange = { repo.setPreventPowerOff(it) },
            onCancelCooldown = { repo.cancelPowerOffCooldown() }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuickShieldRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    pendingUnlockUntil: Long,
    onCancelUnlock: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val isPending = pendingUnlockUntil > 0L
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RaisedSquirclePedestal(size = 42.dp) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NordicColors.AccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = NordicColors.TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = NordicColors.TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            NordicSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
        if (isPending) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                LiveCountdownBadge(
                    targetEpochMs = pendingUnlockUntil,
                    prefix = "Unlocking in ",
                    tone = BadgeTone.WARN,
                    small = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onCancelUnlock,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = CyberIcons.Close,
                        contentDescription = "Cancel",
                        tint = NordicColors.AlertMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DailyBlockedActivityGraph(
    history: List<com.blocker.BlockerStore.DailyBlockPoint>,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxCount = (history.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    // Callout pill if selected
    val selectedItem = selectedIndex?.let { history.getOrNull(it) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (selectedItem != null) {
            Surface(
                color = NordicColors.CardSurfaceSecondary,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NordicColors.AccentPrimary)
            ) {
                Text(
                    text = "${selectedItem.count} blocks on ${selectedItem.dayLabel} (${selectedItem.dateIso})",
                    color = NordicColors.TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Spacer(modifier = Modifier.height(26.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            history.forEachIndexed { index, point ->
                val isSelected = selectedIndex == index
                val isToday = point.isToday
                val barFraction = if (maxCount > 0) {
                    (point.count.toFloat() / maxCount.toFloat()).coerceIn(0.06f, 1f)
                } else {
                    0.06f
                }
                val animBarFraction by animateFloatAsState(
                    targetValue = barFraction,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "bar_anim_$index"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            selectedIndex = if (selectedIndex == index) null else index
                        },
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Count above bar
                    Text(
                        text = "${point.count}",
                        color = when {
                            isSelected -> NordicColors.TextPrimary
                            isToday -> NordicColors.AccentSecondary
                            point.count > 0 -> NordicColors.TextSecondary
                            else -> NordicColors.TextSecondary.copy(alpha = 0.5f)
                        },
                        fontSize = 10.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Vertical Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(animBarFraction * 0.72f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(
                                when {
                                    isSelected -> NordicColors.TextPrimary
                                    isToday -> NordicColors.AccentPrimary
                                    point.count > 0 -> Color(0xFF4C3B87)
                                    else -> Color(0xFF231B45)
                                }
                            )
                            .then(
                                if (isToday) {
                                    Modifier.border(
                                        1.dp,
                                        NordicColors.AccentSecondary,
                                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                                    )
                                } else Modifier
                            )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Day label at bottom
                    Text(
                        text = if (isToday) "Today" else point.dayLabel,
                        color = if (isToday) NordicColors.AccentPrimary else NordicColors.TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
