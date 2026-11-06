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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

@Composable
fun FocusScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val focus by repo.focusState.collectAsState()
    val study by repo.studyState.collectAsState()
    val apps by repo.installedApps.collectAsState()
    val scrollState = rememberScrollState()

    var studyFocusMins by remember { mutableStateOf(25) }
    var studyBreakMins by remember { mutableStateOf(5) }
    var studyRounds by remember { mutableStateOf(4) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        SectionHeader(
            title = "Focus & study",
            icon = CyberIcons.Focus,
            subtitle = "Pomodoro phone lockout, daily schedules, and distraction shielding"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Active Study Lockout Card (if active)
        if (study.active) {
            val remSec = study.remainingMs / 1000L
            val mins = remSec / 60L
            val secs = remSec % 60L
            val timeStr = String.format("%02d:%02d", mins, secs)

            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                active = true,
                tone = if (study.isBreak) CardTone.OK else CardTone.ACCENT
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusPillBadge(
                        label = if (study.isBreak) "Break Time" else "Deep Study Lockout",
                        tone = if (study.isBreak) BadgeTone.OK else BadgeTone.ACCENT,
                        breathing = true,
                        icon = if (study.isBreak) CyberIcons.Check else CyberIcons.Lock
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = timeStr,
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Round ${study.currentRound} of ${study.totalRounds}",
                        color = CyberTextSub,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (study.isBreak) {
                            Btn(
                                label = "Skip Break",
                                icon = CyberIcons.Play,
                                onClick = { repo.skipStudyBreak() },
                                kind = BtnKind.PRIMARY
                            )
                        }
                        Btn(
                            label = "End Session",
                            icon = CyberIcons.Stop,
                            onClick = { repo.endStudySession() },
                            kind = BtnKind.SECONDARY
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // Study Mode Configurator
            GlowCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Start Pomodoro Lockout",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Aggressively locks distracting phone access and silences notifications.",
                    color = CyberTextSub,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Focus Duration Chips
                Text(text = "Focus Duration", color = CyberTextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60).forEach { m ->
                        val isSel = studyFocusMins == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) CyberAccentSoft else CyberCardSurface)
                                .border(1.dp, if (isSel) CyberAccent else CyberCardBorder, RoundedCornerShape(10.dp))
                                .clickable { studyFocusMins = m }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "${m}m",
                                color = if (isSel) Color.White else CyberTextSub,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Break Duration Chips
                Text(text = "Break Duration", color = CyberTextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10, 15).forEach { m ->
                        val isSel = studyBreakMins == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) CyberOkSoft else CyberCardSurface)
                                .border(1.dp, if (isSel) CyberOk else CyberCardBorder, RoundedCornerShape(10.dp))
                                .clickable { studyBreakMins = m }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "${m}m",
                                color = if (isSel) Color.White else CyberTextSub,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Btn(
                    label = "Launch Study Lockout",
                    icon = CyberIcons.Play,
                    onClick = {
                        repo.startStudySession(studyFocusMins, studyBreakMins, studyRounds)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick Focus Mode
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "Quick Focus Countdown",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (focus.active) "Active: ${focus.remainingMs / 60000L}m left" else "Block non-essential apps for a set period",
                        color = if (focus.active) CyberAccentGlow else CyberTextSub,
                        fontSize = 12.sp
                    )
                }

                if (focus.active) {
                    Btn(
                        label = "Stop",
                        icon = CyberIcons.Stop,
                        onClick = { repo.endQuickFocus() },
                        kind = BtnKind.SECONDARY
                    )
                }
            }

            if (!focus.active) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (mins, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyberCardSurface)
                                .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp))
                                .clickable { repo.startQuickFocus(mins) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(text = label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Whitelist / Allowed Apps in Focus Mode
        SectionHeader(
            title = "Allowed Essential Apps",
            icon = CyberIcons.ShieldCheck,
            subtitle = "Apps permitted during Focus and Study modes (e.g. Phone, Maps)"
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlowCard(modifier = Modifier.fillMaxWidth()) {
            val userApps = remember(apps) { apps.filter { !it.isSystem }.take(8) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                userApps.forEach { app ->
                    val isAllowed = focus.allowedApps.contains(app.packageName)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            AppIconView(drawable = app.icon, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = app.label, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                        CyberSwitch(
                            checked = isAllowed,
                            onCheckedChange = { repo.toggleAllowedFocusApp(app.packageName) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
