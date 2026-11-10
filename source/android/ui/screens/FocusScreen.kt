package com.blocker.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blocker.FocusSchedule
import com.blocker.BlockerStore
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

private data class StudyQuote(val quote: String, val source: String)

private val STUDY_QUOTES = listOf(
    StudyQuote(
        quote = "Take advantage of five before five: your youth before your old age, your health before your sickness, your wealth before your poverty, your free time before your busyness, and your life before your death.",
        source = "Hadith — Al-Hakim"
    ),
    StudyQuote(
        quote = "And that there is not for man except that [good] for which he strives, and that his effort is going to be seen.",
        source = "Surah An-Najm (53:39-40)"
    ),
    StudyQuote(
        quote = "You have power over your mind - not outside events. Realize this, and you will find strength.",
        source = "Marcus Aurelius"
    ),
    StudyQuote(
        quote = "The secret of getting ahead is getting started. The secret of getting started is breaking your complex overwhelming tasks into small manageable tasks.",
        source = "Mark Twain"
    ),
    StudyQuote(
        quote = "Indeed, with hardship comes ease. So when you have finished, still labor hard.",
        source = "Surah Ash-Sharh (94:6-7)"
    )
)

private fun clock12(totalMinutes: Int): String {
    val h24 = (totalMinutes / 60) % 24
    val min = totalMinutes % 60
    val ampm = if (h24 < 12) "AM" else "PM"
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    return String.format("%d:%02d %s", h12, min, ampm)
}

private fun formatDuration(startMin: Int, endMin: Int): String {
    val diff = if (endMin >= startMin) endMin - startMin else (1440 - startMin + endMin)
    if (diff == 0 || diff == 1440) return "24h"
    val h = diff / 60
    val m = diff % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

@Composable
fun FocusScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val focus by repo.focusState.collectAsState()
    val study by repo.studyState.collectAsState()
    val protection by repo.protectionState.collectAsState()
    val distraction by repo.distractionShield.collectAsState()
    val apps by repo.installedApps.collectAsState()
    val nowMillis by repo.currentTimeMillisFlow.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Segmented page: 0 -> Focus Mode, 1 -> Study Mode
    var selectedTab by remember { mutableStateOf(if (study.active) 1 else 0) }

    LaunchedEffect(study.active) {
        if (study.active) selectedTab = 1
    }

    // Study Configurator State
    var studyFocusMins by remember { mutableStateOf(25) }
    var studyBreakMins by remember { mutableStateOf(5) }
    var studyRounds by remember { mutableStateOf(4) }
    val randomQuote = remember { STUDY_QUOTES.random() }

    // Daily Schedule Creation State
    var showAddSchedule by remember { mutableStateOf(false) }
    var newScheduleLabel by remember { mutableStateOf("") }
    var newStartMin by remember { mutableStateOf(540) } // 9:00 AM default
    var newEndMin by remember { mutableStateOf(1020) }  // 5:00 PM default
    var newScheduleAllowedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showNewScheduleAppPicker by remember { mutableStateOf(false) }
    var editingScheduleForApps by remember { mutableStateOf<FocusSchedule?>(null) }

    // Essential Apps Search & Filter
    var appSearchQuery by remember { mutableStateOf("") }
    var appFilterTab by remember { mutableStateOf(0) } // 0: User Apps, 1: System Apps, 2: All Apps

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Segmented Tab Switcher (Focus Mode vs Study Mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NordicColors.CardSurface)
                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            // Focus Mode Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selectedTab == 0) NordicColors.AccentPrimary else Color.Transparent)
                    .clickable(enabled = !study.active) { selectedTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = CyberIcons.Focus,
                        contentDescription = null,
                        tint = if (selectedTab == 0) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Focus Mode",
                        color = if (selectedTab == 0) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Study Mode Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selectedTab == 1) NordicColors.AccentPrimary else Color.Transparent)
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = CyberIcons.Book,
                        contentDescription = null,
                        tint = if (selectedTab == 1) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Study Mode",
                        color = if (selectedTab == 1) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (study.active) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (selectedTab == 1) NordicColors.OnAccentPrimary else NordicColors.AccentPrimary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(animationSpec = tween(240)) + slideInVertically(animationSpec = tween(240)) { it / 18 })
                    .togetherWith(fadeOut(animationSpec = tween(150)))
            },
            label = "focus_study_tab_anim"
        ) { currentTab ->
            if (currentTab == 0) {
            // ==========================================
            // FOCUS MODE VIEW
            // ==========================================

            // 1. Quick Focus Mode Card
            if (focus.active && focus.remainingMs > 0) {
                val remSec = focus.remainingMs / 1000L
                val hours = remSec / 3600L
                val mins = (remSec % 3600L) / 60L
                val secs = remSec % 60L
                val timeStr = if (hours > 0) {
                    String.format("%02d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format("%02d:%02d", mins, secs)
                }

                NordicFoliageHeroCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusPillBadge(
                            label = "Focus Mode Active",
                            tone = BadgeTone.ACCENT,
                            breathing = true,
                            icon = CyberIcons.Focus
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        HeroNumeral(
                            value = timeStr,
                            caption = "Remaining Focus Time",
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Only essential allowed apps are accessible",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        val hasPendingUnlock = focus.pendingUnlockUntil > 0L
                        val unlockRemMs = (focus.pendingUnlockUntil - nowMillis).coerceAtLeast(0L)
                        if (hasPendingUnlock) {
                            val totalSec = (unlockRemMs / 1000).coerceAtLeast(0)
                            val h = totalSec / 3600
                            val m = (totalSec % 3600) / 60
                            val s = totalSec % 60
                            val unlockTimeStr = if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
                            Spacer(modifier = Modifier.height(14.dp))
                            StatusPillBadge(
                                label = "Unlocking in $unlockTimeStr",
                                tone = BadgeTone.WARN,
                                breathing = true,
                                icon = CyberIcons.Clock
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Btn(
                                label = "Cancel Unlock",
                                icon = CyberIcons.Close,
                                onClick = { repo.cancelFocusUnlock() },
                                kind = BtnKind.GHOST
                            )
                        } else {
                            Spacer(modifier = Modifier.height(18.dp))
                            Btn(
                                label = "End Focus Early",
                                icon = CyberIcons.Stop,
                                onClick = { repo.requestEndQuickFocus() },
                                kind = BtnKind.SECONDARY
                            )
                        }
                    }
                }
            } else {
                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Quick Focus Countdown",
                                color = NordicColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Instantly block distractions for a targeted interval",
                                color = NordicColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            15 to "15m",
                            30 to "30m",
                            60 to "1h",
                            120 to "2h",
                            240 to "4h"
                        ).forEach { (m, lbl) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NordicColors.CardSurfaceHero)
                                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable { repo.startQuickFocus(m) }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = lbl,
                                    color = NordicColors.TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Daily Schedules Section
            SectionHeader(
                title = "Daily Schedules",
                icon = CyberIcons.Clock,
                subtitle = "Focus mode turns on automatically during recurring daily windows"
            )

            Spacer(modifier = Modifier.height(10.dp))

            NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recurring Windows",
                            color = NordicColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${focus.schedules.size} schedule${if (focus.schedules.size == 1) "" else "s"} configured",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Btn(
                        label = if (showAddSchedule) "Cancel" else "Add",
                        icon = if (showAddSchedule) CyberIcons.Close else CyberIcons.Add,
                        onClick = { showAddSchedule = !showAddSchedule },
                        kind = if (showAddSchedule) BtnKind.GHOST else BtnKind.PRIMARY
                    )
                }

                // Add Schedule Form
                if (showAddSchedule) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "New Schedule Details",
                                color = NordicColors.TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Name input
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NordicColors.CanvasBg)
                                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (newScheduleLabel.isEmpty()) {
                                    Text(
                                        text = "Schedule name (e.g. Work, Bedtime)",
                                        color = NordicColors.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = newScheduleLabel,
                                    onValueChange = { newScheduleLabel = it },
                                    textStyle = TextStyle(
                                        color = NordicColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(NordicColors.AccentPrimary),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Time pickers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Starts
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Starts", color = NordicColors.TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NordicColors.CanvasBg)
                                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                            .clickable {
                                                TimePickerDialog(
                                                    context,
                                                    { _, h, m -> newStartMin = h * 60 + m },
                                                    newStartMin / 60,
                                                    newStartMin % 60,
                                                    false
                                                ).show()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = clock12(newStartMin),
                                            color = NordicColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Duration pill
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = formatDuration(newStartMin, newEndMin),
                                        color = NordicColors.AccentPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "duration",
                                        color = NordicColors.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Ends
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Ends", color = NordicColors.TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NordicColors.CanvasBg)
                                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                            .clickable {
                                                TimePickerDialog(
                                                    context,
                                                    { _, h, m -> newEndMin = h * 60 + m },
                                                    newEndMin / 60,
                                                    newEndMin % 60,
                                                    false
                                                ).show()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = clock12(newEndMin),
                                            color = NordicColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Btn(
                                label = "Select Allowed Apps (${newScheduleAllowedApps.size} selected)",
                                icon = CyberIcons.Apps,
                                onClick = { showNewScheduleAppPicker = true },
                                kind = BtnKind.SECONDARY,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Btn(
                                label = "Save Schedule",
                                icon = CyberIcons.Check,
                                onClick = {
                                    val name = newScheduleLabel.trim().ifEmpty { "Schedule ${focus.schedules.size + 1}" }
                                    repo.addSchedule(name, newStartMin, newEndMin, newScheduleAllowedApps)
                                    newScheduleLabel = ""
                                    newScheduleAllowedApps = emptySet()
                                    showAddSchedule = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Schedule items list
                if (focus.schedules.isEmpty() && !showAddSchedule) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No daily schedules yet — tap Add to create a recurring focus window.",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.5.sp
                    )
                } else if (focus.schedules.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        focus.schedules.forEach { s ->
                            val isActiveNow = repo.isScheduleActiveNow(s)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isActiveNow) NordicColors.CardSurfaceHero else NordicColors.CanvasBg)
                                    .border(
                                        1.dp,
                                        if (isActiveNow) NordicColors.AccentPrimary.copy(alpha = 0.5f) else NordicColors.HairlineBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = s.label,
                                                color = NordicColors.TextPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isActiveNow) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                StatusPillBadge(
                                                    label = "Active",
                                                    tone = BadgeTone.OK,
                                                    small = true,
                                                    breathing = true
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${clock12(s.startMin)} – ${clock12(s.endMin)} (${formatDuration(s.startMin, s.endMin)})",
                                            color = NordicColors.TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { editingScheduleForApps = s }
                                        ) {
                                            StatusPillBadge(
                                                label = "${s.allowedApps.size} Apps Allowed",
                                                tone = BadgeTone.IDLE,
                                                small = true,
                                                icon = CyberIcons.Apps
                                            )
                                        }
                                    }

                                    val schedPendingUntil = focus.pendingScheduleUnlocks[s.id] ?: 0L
                                    val hasSchedPending = schedPendingUntil > 0L
                                    val schedRemMs = (schedPendingUntil - nowMillis).coerceAtLeast(0L)
                                    val action = focus.pendingScheduleActions[s.id] ?: "delete"

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (hasSchedPending) {
                                            val totalSec = (schedRemMs / 1000).coerceAtLeast(0)
                                            val h = totalSec / 3600
                                            val m = (totalSec % 3600) / 60
                                            val sec = totalSec % 60
                                            val timeStr = if (h > 0) String.format("%02d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
                                            val actionLabel = if (action == "delete") "Deleting" else "Disabling"
                                            StatusPillBadge(
                                                label = "$actionLabel in $timeStr",
                                                tone = BadgeTone.WARN,
                                                small = true
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { repo.cancelScheduleUnlock(s.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = CyberIcons.Close,
                                                    contentDescription = "Abort",
                                                    tint = NordicColors.AlertMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { repo.requestDeleteSchedule(s.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = CyberIcons.Delete,
                                                    contentDescription = "Delete",
                                                    tint = NordicColors.AlertMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            NordicSwitch(
                                                checked = s.enabled,
                                                onCheckedChange = { repo.requestToggleScheduleEnabled(s.id, it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Distraction & Short-Form Shield (Separated items)
            SectionHeader(
                title = "Distraction & Short-Form Shield",
                icon = CyberIcons.ShieldCheck,
                subtitle = "Exit short-form video feeds and algorithms while keeping communication intact"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dedicated Individual Item 1: YouTube Shorts
            DistractionItemCard(
                title = "Block YouTube Shorts",
                subtitle = "Immediately ejects Shorts back to Home feed or subscription tab",
                icon = CyberIcons.Movie,
                checked = distraction.blockYtShorts,
                pendingUnlockUntil = distraction.pendingUnlocks["block_yt_shorts"] ?: 0L,
                nowMillis = nowMillis,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_yt_shorts") },
                onCheckedChange = { repo.toggleDistraction("block_yt_shorts", it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Individual Item 2: Instagram Reels
            DistractionItemCard(
                title = "Block Instagram Reels",
                subtitle = "Immediately exits Reels back to your main timeline and messages",
                icon = CyberIcons.Movie,
                checked = distraction.blockInstaReels,
                pendingUnlockUntil = distraction.pendingUnlocks["block_insta_reels"] ?: 0L,
                nowMillis = nowMillis,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_insta_reels") },
                onCheckedChange = { repo.toggleDistraction("block_insta_reels", it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Individual Item 3: Instagram Explore & Search
            DistractionItemCard(
                title = "Block Instagram Explore",
                subtitle = "Blocks discovery algorithm and search tab to prevent doom-scrolling",
                icon = CyberIcons.Search,
                checked = distraction.blockInstaSearch,
                pendingUnlockUntil = distraction.pendingUnlocks["block_insta_search"] ?: 0L,
                nowMillis = nowMillis,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_insta_search") },
                onCheckedChange = { repo.toggleDistraction("block_insta_search", it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Individual Item 4: Facebook Reels & Videos
            DistractionItemCard(
                title = "Block Facebook Reels & Videos",
                subtitle = "Exits Reels, Watch and video feeds (Stories and text posts stay open)",
                icon = CyberIcons.Movie,
                checked = distraction.blockFbReels,
                pendingUnlockUntil = distraction.pendingUnlocks["block_fb_reels"] ?: 0L,
                nowMillis = nowMillis,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_fb_reels") },
                onCheckedChange = { repo.toggleDistraction("block_fb_reels", it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Item 5: Reddit Mature Content Lockdown
            NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(NordicColors.CardSurfaceHero)
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CyberIcons.Lock,
                                contentDescription = null,
                                tint = NordicColors.AccentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Reddit Mature Content Lockdown",
                                color = NordicColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enforces 'Show mature content' OFF at the system level",
                                color = NordicColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    StatusPillBadge(label = "Enforced", tone = BadgeTone.OK, small = true)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Allowed Essential Apps Whitelist
            SectionHeader(
                title = "Allowed Essential Apps",
                icon = CyberIcons.Apps,
                subtitle = "Only these apps (plus Phone and keyboard) remain usable during focus"
            )

            Spacer(modifier = Modifier.height(10.dp))

            val isFocusCurrentlyLocked = focus.active || study.active || focus.schedules.any { repo.isScheduleActiveNow(it) }

            NordicMicroGridCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                if (isFocusCurrentlyLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.AlertMuted, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = CyberIcons.Lock,
                                contentDescription = null,
                                tint = NordicColors.AlertMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App selection is locked while a Focus session is active",
                                color = NordicColors.AlertMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (protection.isActive) {
                    val pendingAppsUnlock = focus.pendingAppsUnlockUntil
                    if (pendingAppsUnlock > 0L) {
                        val remSec = ((pendingAppsUnlock - nowMillis) / 1000L).coerceAtLeast(0L)
                        val timeStr = String.format("%02d:%02d", remSec / 60, remSec % 60)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NordicColors.CardSurfaceHero)
                                .border(1.dp, NordicColors.AlertMuted, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = CyberIcons.Lock,
                                    contentDescription = null,
                                    tint = NordicColors.AlertMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Modifying allowed apps unlocking in $timeStr",
                                    color = NordicColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Abort",
                                color = NordicColors.AlertMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { repo.cancelFocusAppsUnlock() }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // 3-Tab Filter Chips: User Apps | System Apps | All Apps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("User Apps", "System Apps", "All Apps").forEachIndexed { idx, title ->
                        val selected = appFilterTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                .border(
                                    1.dp,
                                    if (selected) NordicColors.AccentPrimary else NordicColors.HairlineBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { appFilterTab = idx }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (selected) Color.White else NordicColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NordicColors.CanvasBg)
                        .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (appSearchQuery.isEmpty()) {
                        Text(text = "Search apps to whitelist...", color = NordicColors.TextSecondary, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        textStyle = TextStyle(color = NordicColors.TextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(NordicColors.AccentPrimary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val filteredApps = remember(apps, appSearchQuery, focus.allowedApps, appFilterTab) {
                    val q = appSearchQuery.trim().lowercase()
                    val baseList = when (appFilterTab) {
                        0 -> apps.filter { !it.isSystem }
                        1 -> apps.filter { it.isSystem }
                        else -> apps
                    }
                    baseList
                        .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                        .sortedWith(
                            compareByDescending<com.blocker.data.AppItem> { focus.allowedApps.contains(it.packageName) }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(30)
                }

                if (filteredApps.isEmpty()) {
                    Text(
                        text = "No apps found matching query",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredApps.forEach { app ->
                            val isAllowed = focus.allowedApps.contains(app.packageName)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    AppIconView(drawable = app.icon, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = app.label,
                                                color = NordicColors.TextPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (app.isSystem) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                StatusPillBadge(
                                                    label = "System",
                                                    tone = BadgeTone.ACCENT,
                                                    small = true
                                                )
                                            }
                                        }
                                        Text(
                                            text = app.packageName,
                                            color = NordicColors.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                NordicSwitch(
                                    checked = isAllowed,
                                    enabled = !isFocusCurrentlyLocked || isAllowed,
                                    onCheckedChange = { repo.toggleAllowedFocusApp(app.packageName) }
                                )
                            }
                        }
                    }
                }
            }

        } else {
            // ==========================================
            // STUDY MODE VIEW (Pomodoro Lockout)
            // ==========================================

            if (study.active) {
                val remSec = study.remainingMs / 1000L
                val mins = remSec / 60L
                val secs = remSec % 60L
                val timeStr = String.format("%02d:%02d", mins, secs)

                NordicFoliageHeroCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusPillBadge(
                            label = if (study.isBreak) "Break Time — Rest Your Mind" else "Deep Study Lockout",
                            tone = if (study.isBreak) BadgeTone.OK else BadgeTone.ACCENT,
                            breathing = true,
                            icon = if (study.isBreak) CyberIcons.Check else CyberIcons.Lock
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        HeroNumeral(
                            value = timeStr,
                            caption = if (study.isBreak) "Break Remaining" else "Study Session Countdown",
                            horizontalAlignment = Alignment.CenterHorizontally
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Round ${study.currentRound} of ${study.totalRounds}",
                            color = NordicColors.TextSecondary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        NordicLinearBar(
                            progress = study.currentRound.toFloat() / study.totalRounds.coerceAtLeast(1).toFloat(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        )

                        if (study.autoDnd || study.autoWifi) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (study.autoDnd) {
                                    StatusPillBadge(
                                        label = "DND Active",
                                        tone = BadgeTone.ACCENT,
                                        small = true,
                                        icon = CyberIcons.Dnd
                                    )
                                }
                                if (study.autoWifi) {
                                    StatusPillBadge(
                                        label = if (study.isBreak) "Wi-Fi Restored" else "Wi-Fi Muted",
                                        tone = if (study.isBreak) BadgeTone.OK else BadgeTone.WARN,
                                        small = true,
                                        icon = if (study.isBreak) CyberIcons.Wifi else CyberIcons.WifiOff
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

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

                // Active Quote Card
                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "\"${randomQuote.quote}\"",
                        color = NordicColors.TextPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "— ${randomQuote.source}",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

            } else {
                // Study Mode Setup & Configurator
                SectionHeader(
                    title = "Pomodoro Study Lockout",
                    icon = CyberIcons.Book,
                    subtitle = "Eliminates all digital friction with strict timed focus cycles"
                )

                Spacer(modifier = Modifier.height(12.dp))

                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Customize Study Cycles",
                        color = NordicColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Full device lockout during focus; relax during scheduled breaks.",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Focus Duration Chips
                    Text(
                        text = "FOCUS DURATION",
                        color = NordicColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 25, 30, 45, 60).forEach { m ->
                            val isSel = (studyFocusMins == m)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                    .border(1.dp, if (isSel) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable { studyFocusMins = m }
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "${m}m",
                                    color = if (isSel) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Break Duration Chips
                    Text(
                        text = "BREAK DURATION",
                        color = NordicColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10, 15).forEach { m ->
                            val isSel = (studyBreakMins == m)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                    .border(1.dp, if (isSel) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable { studyBreakMins = m }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "${m}m",
                                    color = if (isSel) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Total Rounds Chips
                    Text(
                        text = "TOTAL ROUNDS",
                        color = NordicColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3, 4, 6).forEach { r ->
                            val isSel = (studyRounds == r)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                    .border(1.dp, if (isSel) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable { studyRounds = r }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "$r",
                                    color = if (isSel) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Btn(
                        label = "Launch Study Lockout ($studyFocusMins min focus, $studyRounds rounds)",
                        icon = CyberIcons.Play,
                        onClick = {
                            repo.startStudySession(studyFocusMins, studyBreakMins, studyRounds)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Connectivity & Notification Lockdown
                SectionHeader(
                    title = "Lockdown Safeguards & Connectivity",
                    icon = CyberIcons.Dnd,
                    subtitle = "Automate Do Not Disturb, silence notifications, and eliminate internet distractions"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // DND Access Warning Card if not granted
                if (!study.hasDndAccess) {
                    NordicMicroGridCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = CyberIcons.Warning,
                                    contentDescription = null,
                                    tint = NordicColors.AlertMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "DND Permission Needed",
                                    color = NordicColors.TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "To automatically silence incoming rings, alerts, and notifications during study sessions, grant Do Not Disturb access.",
                                color = NordicColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Btn(
                                label = "Grant DND Permission",
                                icon = CyberIcons.Dnd,
                                onClick = { repo.requestDndAccess(context) },
                                kind = BtnKind.PRIMARY
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Auto DND Toggle
                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
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
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(NordicColors.CardSurfaceHero)
                                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CyberIcons.Dnd,
                                    contentDescription = null,
                                    tint = NordicColors.AccentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Silence Notifications (DND)",
                                    color = NordicColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Auto-enables Do Not Disturb during focus cycles",
                                    color = NordicColors.TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        NordicSwitch(
                            checked = study.autoDnd,
                            onCheckedChange = {
                                if (it && !study.hasDndAccess) {
                                    repo.requestDndAccess(context)
                                } else {
                                    repo.setStudyAutoDnd(it)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Auto Wi-Fi Disconnect Toggle
                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
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
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(NordicColors.CardSurfaceHero)
                                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CyberIcons.WifiOff,
                                    contentDescription = null,
                                    tint = NordicColors.AccentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Turn off Wi-Fi during Study",
                                    color = NordicColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Disconnects Wi-Fi during focus; restores on breaks",
                                    color = NordicColors.TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        NordicSwitch(
                            checked = study.autoWifi,
                            onCheckedChange = { repo.setStudyAutoWifi(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cellular & Connectivity Quick Actions
                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(NordicColors.CardSurfaceHero)
                                        .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(11.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CyberIcons.Cellular,
                                        contentDescription = null,
                                        tint = NordicColors.AccentPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Mobile Data & Network Controls",
                                        color = NordicColors.TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Quick shortcuts to toggle off cellular or internet data",
                                        color = NordicColors.TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn(
                                label = "Cellular Settings",
                                icon = CyberIcons.Cellular,
                                onClick = { repo.openDataSettings(context) },
                                kind = BtnKind.SECONDARY,
                                modifier = Modifier.weight(1f)
                            )
                            Btn(
                                label = "Wi-Fi Settings",
                                icon = CyberIcons.Wifi,
                                onClick = { repo.openWifiSettings(context) },
                                kind = BtnKind.SECONDARY,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quote Card
                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "\"${randomQuote.quote}\"",
                        color = NordicColors.TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "— ${randomQuote.source}",
                        color = NordicColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showNewScheduleAppPicker) {
        ScheduleAppPickerDialog(
            title = "Allowed Apps for New Schedule",
            initialAllowed = newScheduleAllowedApps,
            allApps = apps,
            isLocked = false,
            unlockTargetMs = 0L,
            nowMillis = nowMillis,
            onToggleApp = { pkg ->
                newScheduleAllowedApps = if (newScheduleAllowedApps.contains(pkg)) {
                    newScheduleAllowedApps - pkg
                } else {
                    newScheduleAllowedApps + pkg
                }
            },
            onAbortUnlock = {},
            onDismiss = { showNewScheduleAppPicker = false }
        )
    }

    editingScheduleForApps?.let { sched ->
        val schedLockKey = "sched_apps_${sched.id}"
        val unlockTarget = BlockerStore.settingUnlockTarget(context, schedLockKey)
        val isLocked = repo.isScheduleActiveNow(sched) || protection.isActive
        ScheduleAppPickerDialog(
            title = "Allowed Apps — ${sched.label}",
            initialAllowed = sched.allowedApps,
            allApps = apps,
            isLocked = isLocked,
            unlockTargetMs = unlockTarget,
            nowMillis = nowMillis,
            onToggleApp = { pkg ->
                repo.toggleScheduleAllowedApp(sched.id, pkg)
            },
            onAbortUnlock = {
                repo.cancelScheduleAppsUnlock(sched.id)
            },
            onDismiss = { editingScheduleForApps = null }
        )
    }
}

@Composable
private fun DistractionItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    pendingUnlockUntil: Long = 0L,
    nowMillis: Long = 0L,
    onCancelUnlock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPending = pendingUnlockUntil > 0L
    NordicMicroGridCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                NordicSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
            if (isPending) {
                val remSec = ((pendingUnlockUntil - nowMillis) / 1000L).coerceAtLeast(0L)
                val timeStr = String.format("%02d:%02d", remSec / 60, remSec % 60)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    StatusPillBadge(
                        label = "Unlocking in $timeStr",
                        tone = BadgeTone.WARN,
                        small = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onCancelUnlock,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = CyberIcons.Close,
                            contentDescription = "Cancel",
                            tint = NordicColors.AlertMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleAppPickerDialog(
    title: String,
    initialAllowed: Set<String>,
    allApps: List<com.blocker.data.AppItem>,
    isLocked: Boolean,
    unlockTargetMs: Long,
    nowMillis: Long,
    onToggleApp: (String) -> Unit,
    onAbortUnlock: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var tabIndex by remember { mutableStateOf(0) } // 0: User, 1: System, 2: All

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NordicColors.CardSurface)
                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = NordicColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${initialAllowed.size} apps permitted during schedule",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(CyberIcons.Close, contentDescription = "Close", tint = NordicColors.TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Locked or Ticking Countdown Banner
                if (unlockTargetMs > 0L) {
                    val remSec = ((unlockTargetMs - nowMillis) / 1000L).coerceAtLeast(0L)
                    val timeStr = String.format("%02d:%02d", remSec / 60, remSec % 60)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.AlertMuted, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(CyberIcons.Lock, contentDescription = null, tint = NordicColors.AlertMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Modifying apps unlocking in $timeStr",
                                color = NordicColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Abort",
                            color = NordicColors.AlertMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onAbortUnlock() }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(CyberIcons.Lock, contentDescription = null, tint = NordicColors.AlertMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Adding new apps requires countdown while active/protected",
                                color = NordicColors.AlertMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 3 Tabs: User Apps | System Apps | All Apps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("User Apps", "System Apps", "All Apps").forEachIndexed { idx, tabTitle ->
                        val selected = tabIndex == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                .border(1.dp, if (selected) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(8.dp))
                                .clickable { tabIndex = idx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (selected) Color.White else NordicColors.TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NordicColors.CanvasBg)
                        .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(text = "Search apps to allow...", color = NordicColors.TextSecondary, fontSize = 12.5.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = NordicColors.TextPrimary, fontSize = 12.5.sp),
                        cursorBrush = SolidColor(NordicColors.AccentPrimary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val filtered = remember(allApps, searchQuery, tabIndex, initialAllowed) {
                    val q = searchQuery.trim().lowercase()
                    val base = when (tabIndex) {
                        0 -> allApps.filter { !it.isSystem }
                        1 -> allApps.filter { it.isSystem }
                        else -> allApps
                    }
                    base.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                        .sortedWith(
                            compareByDescending<com.blocker.data.AppItem> { initialAllowed.contains(it.packageName) }
                                .thenBy { it.label.lowercase() }
                        )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered.size) { i ->
                        val app = filtered[i]
                        val isAllowed = initialAllowed.contains(app.packageName)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                AppIconView(drawable = app.icon, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.label,
                                            color = NordicColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (app.isSystem) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            StatusPillBadge(label = "System", tone = BadgeTone.ACCENT, small = true)
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        color = NordicColors.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            NordicSwitch(
                                checked = isAllowed,
                                onCheckedChange = { onToggleApp(app.packageName) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Btn(
                    label = "Done",
                    icon = CyberIcons.Check,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
