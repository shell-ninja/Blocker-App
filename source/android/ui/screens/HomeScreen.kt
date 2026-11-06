package com.blocker.ui.screens

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

@Composable
fun HomeScreen(
    repo: BlockerRepository,
    onNavigateToFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val protection by repo.protectionState.collectAsState()
    val stats by repo.statsState.collectAsState()
    val scrollState = rememberScrollState()

    // Pulsing shield animation when active
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (protection.isActive) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield_pulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberAccentWell)
                        .border(1.dp, CyberAccentSoft, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🛡️", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Row {
                    Text(
                        text = "Block",
                        color = CyberText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "er",
                        color = CyberAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            StatusBadge(
                text = if (protection.isActive) "PROTECTED" else "STANDBY",
                isActive = protection.isActive
            )
        }

        // Delay timer warning banner if pending
        if (protection.pendingUntil > 0L) {
            LockBar(
                pendingUntil = protection.pendingUntil,
                onCancel = { repo.cancelProtectionRequest() },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Hero Shield Card
        val heroBorderColor = if (protection.isActive) CyberOk else CyberAccent
        val heroBgBrush = if (protection.isActive) {
            Brush.verticalGradient(listOf(CyberOkWell, CyberCard))
        } else {
            Brush.verticalGradient(listOf(CyberAccentWell, CyberCard))
        }

        CyberCard(
            modifier = Modifier.fillMaxWidth(),
            isGlowing = protection.isActive,
            glowColor = heroBorderColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulsing Shield Icon
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(if (protection.isActive) CyberOkSoft else CyberAccentSoft)
                        .border(
                            2.dp,
                            if (protection.isActive) CyberOk else CyberAccent,
                            CircleShape
                        )
                        .clickable {
                            repo.toggleProtection(!protection.isActive)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (protection.isActive) "🛡️" else "🔒",
                        fontSize = 48.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (protection.isActive) "Protection is Active" else "Protection is Paused",
                    color = CyberText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (protection.isActive) {
                        "All filters, apps and anti-tamper shields are armed."
                    } else {
                        "Tap the shield or toggle below to arm full defense."
                    },
                    color = CyberTextSub,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                GlowButton(
                    text = if (protection.isActive) "Disarm Defense" else "Arm Full Defense",
                    onClick = { repo.toggleProtection(!protection.isActive) },
                    isEmerald = !protection.isActive,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Shield Stats Bento
        SectionHeader(
            title = "Defense Metrics",
            subtitle = "Real-time threat intercepts and telemetry",
            icon = "📊"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tamper Stat
            CyberCard(
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "🛡️ Ejections", color = CyberTextSub, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${stats.tamperCount}",
                    color = CyberAccentGlow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(text = "Tamper attempts", color = CyberTextMuted, fontSize = 11.sp)
            }

            // NSFW Stat
            CyberCard(
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "🚫 Blocked", color = CyberTextSub, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${stats.nsfwCount}",
                    color = CyberOkGlow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(text = "Explicit web matches", color = CyberTextMuted, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Shields Section
        SectionHeader(
            title = "Active Shields",
            subtitle = "Granular security rules enforced by the C++ engine",
            icon = "⚡"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Facebook Reels
        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Block Facebook Reels",
                        color = CyberText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Instantly skips addictive short-form video reels",
                        color = CyberTextSub,
                        fontSize = 12.5.sp
                    )
                }
                CyberSwitch(
                    checked = protection.blockFacebookReels,
                    onCheckedChange = { repo.setFacebookReelsBlocked(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Prevent Power Off
        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prevent Power Off",
                        color = CyberText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Blocks power menu tampering while protected",
                        color = CyberTextSub,
                        fontSize = 12.5.sp
                    )
                }
                CyberSwitch(
                    checked = protection.preventPowerOff,
                    onCheckedChange = { repo.setPreventPowerOff(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Banking Mode
        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Banking Mode",
                        color = CyberText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Suspends overlays automatically inside banking apps",
                        color = CyberTextSub,
                        fontSize = 12.5.sp
                    )
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
