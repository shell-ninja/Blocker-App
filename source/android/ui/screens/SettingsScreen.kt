package com.blocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
fun SettingsScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val protection by repo.protectionState.collectAsState()
    val perms by repo.permissionsState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        SectionHeader(
            title = "Settings & security",
            icon = CyberIcons.Settings,
            subtitle = "Strict covenant locks, delay protection, and hardware defense"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Delay Timer Setting
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Protection Delay Period",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Disarming protection or removing restrictions requires waiting this timer.",
                color = CyberTextSub,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "1 Day", 3 to "3 Days", 7 to "7 Days", 14 to "14 Days", 30 to "30 Days").forEach { (days, label) ->
                    val isSel = protection.delayDays == days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) CyberAccentSoft else CyberCardSurface)
                            .border(1.dp, if (isSel) CyberAccent else CyberCardBorder, RoundedCornerShape(10.dp))
                            .clickable { repo.setDelayDays(days) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) Color.White else CyberTextSub,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Device Admin & System Shields
        SectionHeader(
            title = "Hardware & Admin Protection",
            icon = CyberIcons.ShieldCheck,
            subtitle = "Low-level system enforcement preventing circumvention"
        )

        Spacer(modifier = Modifier.height(10.dp))

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
                            text = "Blocks device power down attempts while protected",
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

        // Device Admin Status
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Icon(
                        imageVector = CyberIcons.ShieldCheck,
                        contentDescription = null,
                        tint = if (perms.deviceAdmin) CyberOkGlow else CyberWarn,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Device Administrator",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (perms.deviceAdmin) "Armed • Uninstall lock active" else "Not activated",
                            color = if (perms.deviceAdmin) CyberOkGlow else CyberWarn,
                            fontSize = 11.5.sp
                        )
                    }
                }
                if (!perms.deviceAdmin) {
                    Btn(
                        label = "Activate",
                        icon = CyberIcons.ShieldCheck,
                        onClick = {
                            repo.openPermissionSettings(context, "deviceAdmin")
                        }
                    )
                } else {
                    StatusPillBadge(label = "Armed", tone = BadgeTone.OK, icon = CyberIcons.Check, small = true)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Language & Localization
        SectionHeader(
            title = "Localization",
            icon = CyberIcons.Web,
            subtitle = "Tamper warning alerts and religious covenant verses language"
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isBn = protection.popupLanguage == "bn"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!isBn) CyberAccentSoft else CyberCardSurface)
                        .border(1.dp, if (!isBn) CyberAccent else CyberCardBorder, RoundedCornerShape(12.dp))
                        .clickable { repo.setPopupLanguage("en") }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "English",
                        color = if (!isBn) Color.White else CyberTextSub,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBn) CyberAccentSoft else CyberCardSurface)
                        .border(1.dp, if (isBn) CyberAccent else CyberCardBorder, RoundedCornerShape(12.dp))
                        .clickable { repo.setPopupLanguage("bn") }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "বাংলা (Bengali)",
                        color = if (isBn) Color.White else CyberTextSub,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Architecture & Native Info
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Engine Architecture",
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "UI Framework", color = CyberTextSub, fontSize = 12.sp)
                Text(text = "100% Pure Jetpack Compose", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Core Engine", color = CyberTextSub, fontSize = 12.sp)
                Text(text = "C++20 NDK (Zero-GC)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Version", color = CyberTextSub, fontSize = 12.sp)
                Text(text = "v1.0.6.7 (Pure Native)", color = CyberAccentGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
