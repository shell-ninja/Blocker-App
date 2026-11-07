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
import com.blocker.ui.theme.*

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
            .background(CyberMeshBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Delay timer banner
        if (protection.pendingUntil > 0L) {
            LockBar(
                pendingUntil = protection.pendingUntil,
                onCancel = { repo.cancelProtectionRequest() },
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        // Primary Hero Bento Card: "Protection Active" / "Protection Paused"
        GlowCard(
            active = protection.isActive,
            tone = if (protection.isActive) CardTone.OK else CardTone.DEFAULT,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                ) {
                    // Luminous circular icon well
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (protection.isActive) CyberOkWell else Color(0xFF161A2B))
                            .border(
                                1.5.dp,
                                if (protection.isActive) CyberOk.copy(alpha = 0.5f) else CyberCardBorder,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (protection.isActive) CyberIcons.Check else CyberIcons.Shield,
                            contentDescription = null,
                            tint = if (protection.isActive) CyberOkGlow else CyberTextSub,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (protection.isActive) "Protection Active" else "Protection Paused",
                            color = Color.White,
                            fontSize = 18.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = if (protection.isActive) {
                                "Domain blocklists, keywords & limits enforced"
                            } else {
                                "Distraction protection is paused"
                            },
                            color = CyberTextSub,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                CyberSwitch(
                    checked = protection.isActive,
                    onCheckedChange = { repo.toggleProtection(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Secondary Hero Bento Card: "Anti-Tamper Fortress"
        GlowCard(
            modifier = Modifier.fillMaxWidth(),
            tone = CardTone.DEFAULT
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = CyberIcons.Lock,
                        contentDescription = null,
                        tint = CyberOkGlow,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Anti-Tamper Fortress",
                        color = Color.White,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                }

                StatusPillBadge(
                    label = "Airtight Lock",
                    tone = BadgeTone.OK,
                    breathing = protection.isShieldActive,
                    small = true
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Device Administrator, launcher shield, and watchdog active. Uninstallation is strictly locked.",
                color = CyberTextSub,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Btn(
                label = "Anti-Uninstall Shield Active",
                icon = CyberIcons.ShieldCheck,
                kind = BtnKind.SECONDARY,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
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
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Health",
                        color = Color.White,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                StatusPillBadge(
                    label = if (isHealthy) "Healthy" else "Attention",
                    tone = if (isHealthy) BadgeTone.OK else BadgeTone.WARN,
                    small = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val healthItems = listOf(
                Triple("Device admin (uninstall guard)", perms.deviceAdmin, "deviceAdmin"),
                Triple("Usage access", perms.usageAccess, "usageAccess"),
                Triple("Display over other apps", perms.overlay, "overlay"),
                Triple("Accessibility service", perms.accessibility, "accessibility")
            )

            healthItems.forEachIndexed { idx, (label, ok, kind) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !ok) { repo.openPermissionSettings(context, kind) }
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (ok) CyberTextSub else CyberText,
                        fontSize = 13.sp
                    )
                    StatusPillBadge(
                        label = if (ok) "On" else "Fix",
                        tone = if (ok) BadgeTone.OK else BadgeTone.DANGER,
                        small = true
                    )
                }
                if (idx < healthItems.size - 1) {
                    Divider(color = CyberCardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Granular Defense Switches
        SectionHeader(
            title = "Granular Rules",
            subtitle = "Zero-GC microsecond filters enforced by native engine",
            icon = CyberIcons.ShieldCheck
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Facebook Reels
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Icon(
                        imageVector = CyberIcons.Movie,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Block Facebook Reels",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Instantly skips addictive short-form video reels",
                            color = CyberTextSub,
                            fontSize = 11.5.sp
                        )
                    }
                }
                CyberSwitch(
                    checked = protection.blockFacebookReels,
                    onCheckedChange = { repo.setFacebookReelsBlocked(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Prevent Power Off
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Icon(
                        imageVector = CyberIcons.Power,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Prevent Power Off",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Blocks power menu tampering while protected",
                            color = CyberTextSub,
                            fontSize = 11.5.sp
                        )
                    }
                }
                CyberSwitch(
                    checked = protection.preventPowerOff,
                    onCheckedChange = { repo.setPreventPowerOff(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Banking Mode
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Icon(
                        imageVector = CyberIcons.Bank,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Banking Mode",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Suspends overlays automatically inside banking apps",
                            color = CyberTextSub,
                            fontSize = 11.5.sp
                        )
                    }
                }
                CyberSwitch(
                    checked = protection.isBankingMode,
                    onCheckedChange = { repo.setBankingMode(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
