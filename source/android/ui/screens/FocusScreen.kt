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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.FocusSchedule
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.CyberCard
import com.blocker.ui.components.CyberSwitch
import com.blocker.ui.components.GlowButton
import com.blocker.ui.components.SectionHeader
import com.blocker.ui.components.StatusBadge
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "Focus & Study Lockout",
            subtitle = "Strict Pomodoro lockout, daily curfews, and distraction shielding",
            icon = "🎯"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Active Study Lockout Card (if active)
        if (study.active) {
            val remSec = study.remainingMs / 1000L
            val mins = remSec / 60L
            val secs = remSec % 60L
            val timeStr = String.format("%02d:%02d", mins, secs)

            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                isGlowing = true,
                glowColor = if (study.isBreak) CyberOkGlow else CyberAccentGlow
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusBadge(
                        text = if (study.isBreak) "BREAK TIME" else "DEEP STUDY LOCKOUT",
                        isActive = study.isBreak
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = timeStr,
                        color = Color.White,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Round ${study.currentRound} of ${study.totalRounds}",
                        color = CyberTextSub,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (study.isBreak) {
                            GlowButton(
                                text = "Skip Break",
                                onClick = { repo.skipStudyBreak() },
                                isEmerald = true
                            )
                        }
                        GlowButton(
                            text = "End Session",
                            onClick = { repo.endStudySession() },
                            modifier = Modifier
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            // Study Mode Configurator
            CyberCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Start Pomodoro Lockout",
                    color = CyberText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aggressively locks distracting phone access and silences notifications.",
                    color = CyberTextSub,
                    fontSize = 12.5.sp
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) CyberAccentSoft else CyberCardSurface)
                                .border(1.dp, if (isSel) CyberAccent else CyberCardBorder, RoundedCornerShape(8.dp))
                                .clickable { studyFocusMins = m }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) CyberOkSoft else CyberCardSurface)
                                .border(1.dp, if (isSel) CyberOk else CyberCardBorder, RoundedCornerShape(8.dp))
                                .clickable { studyBreakMins = m }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
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

                GlowButton(
                    text = "Launch Study Lockout",
                    onClick = {
                        repo.startStudySession(studyFocusMins, studyBreakMins, studyRounds)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Quick Focus Mode
        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Focus Countdown",
                        color = CyberText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (focus.active) "Active: ${focus.remainingMs / 60000L}m left" else "Block non-essential apps for a set period",
                        color = if (focus.active) CyberAccentGlow else CyberTextSub,
                        fontSize = 12.5.sp
                    )
                }

                if (focus.active) {
                    GlowButton(
                        text = "Stop",
                        onClick = { repo.endQuickFocus() }
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
                            Text(text = label, color = CyberText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Whitelist / Allowed Apps in Focus Mode
        SectionHeader(
            title = "Allowed Essential Apps",
            subtitle = "Apps permitted during Focus and Study modes (e.g. Phone, Notes)",
            icon = "✅"
        )

        Spacer(modifier = Modifier.height(10.dp))

        CyberCard(modifier = Modifier.fillMaxWidth()) {
            val userApps = remember(apps) { apps.filter { !it.isSystem }.take(8) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                userApps.forEach { app ->
                    val isAllowed = focus.allowedApps.contains(app.packageName)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            AppIconView(drawable = app.icon, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = app.label, color = CyberText, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
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
