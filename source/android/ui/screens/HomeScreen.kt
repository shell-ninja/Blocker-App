package com.blocker.ui.screens

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
    val scrollState = rememberScrollState()

    val isHealthy = perms.allGranted

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

        // Primary Hero Bento Card with Botanical Watermark Texture
        NordicFoliageHeroCard(
            modifier = Modifier.fillMaxWidth()
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
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NordicColors.CardSurface)
                            .border(
                                1.5.dp,
                                if (protection.isActive) NordicColors.AccentPrimary else NordicColors.HairlineBorder,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (protection.isActive) CyberIcons.Check else CyberIcons.Shield,
                            contentDescription = null,
                            tint = if (protection.isActive) NordicColors.AccentPrimary else NordicColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
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

        // 7-Day Protection Activity Wave Graph Card
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
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
                        text = "Protection Activity",
                        color = NordicColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                StatusPillBadge(
                    label = "7-Day Shield",
                    tone = BadgeTone.OK,
                    small = true
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            HomeDottedWaveActivityGraph(
                activeDayIndex = 6
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2x2 Bento Stat Cards Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoStatCard(
                value = stats.nsfwCount,
                label = "Sites redirected",
                icon = CyberIcons.Web,
                modifier = Modifier.weight(1f)
            )

            BentoStatCard(
                value = stats.appBlockCount,
                label = "Apps blocked",
                icon = CyberIcons.Apps,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoStatCard(
                value = stats.reelsCount,
                label = "Screen closes",
                icon = CyberIcons.EyeOff,
                modifier = Modifier.weight(1f)
            )

            BentoStatCard(
                value = stats.tamperCount,
                label = "Bypass attempts",
                icon = CyberIcons.Lock,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // System Health Bento Card
        GlowCard(modifier = Modifier.fillMaxWidth()) {
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
                    Divider(color = NordicColors.HairlineBorder.copy(alpha = 0.6f), thickness = 0.8.dp)
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
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Power,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Prevent Power Off",
                            color = NordicColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Blocks power menu tampering while protected",
                            color = NordicColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                NordicSwitch(
                    checked = protection.preventPowerOff,
                    onCheckedChange = { repo.setPreventPowerOff(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
