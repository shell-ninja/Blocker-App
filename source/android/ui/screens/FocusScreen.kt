package com.blocker.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.blocker.BlockerStore
import com.blocker.FocusSchedule
import com.blocker.data.AppItem
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val clean = totalMinutes.coerceIn(0, 1439)
    val h24 = (clean / 60) % 24
    val min = clean % 60
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

private fun formatHms(seconds: Long): String {
    val sec = seconds.coerceAtLeast(0L)
    val h = sec / 3600L
    val m = (sec % 3600L) / 60L
    val s = sec % 60L
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private fun formatStartsIn(millis: Long): String {
    val sec = millis / 1000L
    val h = sec / 3600L
    val m = (sec % 3600L) / 60L
    return when {
        h > 0 -> "Starts in ${h}h ${m}m"
        m > 0 -> "Starts in ${m}m"
        else -> "Starting soon"
    }
}

private fun formatClockTime(epochMs: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.US).format(Date(epochMs))
}

private fun openEmergencyDialer(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            val emergencyIntent = Intent("android.intent.action.EMERGENCY_ASSISTANCE").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(emergencyIntent)
        } catch (_: Throwable) {}
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
    val context = LocalContext.current

    val isWifiOn = remember(nowMillis) { repo.isWifiEnabled() }

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

    // Daily Schedule Add/Edit State
    var showAddOrEditSchedule by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<FocusSchedule?>(null) }
    var scheduleFormLabel by remember { mutableStateOf("") }
    var scheduleFormStartMin by remember { mutableStateOf(0) } // Default 12:00 AM (0)
    var scheduleFormEndMin by remember { mutableStateOf(360) } // Default 06:00 AM (360)
    var scheduleFormDays by remember { mutableStateOf<Set<Int>>(setOf(1, 2, 3, 4, 5, 6, 7)) }
    var scheduleFormAllowedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showScheduleFormAppPicker by remember { mutableStateOf(false) }

    // Schedule Card Quick App Picker
    var editingScheduleForApps by remember { mutableStateOf<FocusSchedule?>(null) }

    // Manual Focus Duration Selector
    val durationOptions = listOf(15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h", 120 to "2h", 240 to "4h", -1 to "Custom")
    var selectedManualDurationMin by remember { mutableStateOf(45) }
    var customManualDurationMin by remember { mutableStateOf(90) }

    // Essential Apps Search & Filter
    var appSearchQuery by remember { mutableStateOf("") }
    var appFilterTab by remember { mutableStateOf(0) } // 0: User Apps, 1: System Apps, 2: All Apps

    // Active Status Calculations
    val isManualActive = focus.focusUntil > nowMillis
    val activeSchedule = focus.schedules.firstOrNull { it.isRunningAt() }
    val isFocusRunningNow = isManualActive || activeSchedule != null
    val isFocusCurrentlyLocked = isFocusRunningNow || study.active

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top Segmented Tab Switcher (Focus Mode vs Study Mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (selectedTab == 1) NordicColors.OnAccentPrimary else NordicColors.AccentPrimary)
                        )
                    }
                }
            }
        }

        if (selectedTab == 0) {
            // ==========================================
            // FOCUS MODE TAB (Single Scrollable LazyColumn)
            // ==========================================
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
                        compareByDescending<AppItem> { focus.allowedApps.contains(it.packageName) }
                            .thenBy { it.label.lowercase() }
                    )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ----------------------------------------------------
                // Section 1: Focus Session Status & Live Timer Card
                // ----------------------------------------------------
                item(key = "focus_status_hero") {
                    if (isFocusRunningNow) {
                        val remainingSec = if (isManualActive) {
                            ((focus.focusUntil - nowMillis) / 1000L).coerceAtLeast(0L)
                        } else {
                            (activeSchedule!!.getRemainingMillisNow() / 1000L).coerceAtLeast(0L)
                        }
                        val timeStr = formatHms(remainingSec)
                        val finishClock = if (isManualActive) {
                            formatClockTime(focus.focusUntil)
                        } else {
                            clock12(activeSchedule!!.endMin)
                        }
                        val sessionSubtitle = if (isManualActive) "Manual Session" else "Schedule: ${activeSchedule?.label}"

                        NordicFoliageHeroCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                StatusPillBadge(
                                    label = "FOCUS SESSION ACTIVE",
                                    tone = BadgeTone.ACCENT,
                                    breathing = true,
                                    icon = CyberIcons.Focus
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = sessionSubtitle,
                                    color = NordicColors.TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                NordicArcGauge(
                                    progress = (remainingSec % 3600L).toFloat() / 3600f,
                                    size = 175.dp
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = timeStr,
                                            color = NordicColors.TextPrimary,
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Remaining Time",
                                            color = NordicColors.TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Runs until $finishClock",
                                            color = NordicColors.AccentPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Only essential allowed apps are accessible",
                                    color = NordicColors.TextSecondary,
                                    fontSize = 12.sp
                                )

                                val hasPendingUnlock = focus.pendingUnlockUntil > 0L
                                val unlockRemMs = (focus.pendingUnlockUntil - nowMillis).coerceAtLeast(0L)
                                if (hasPendingUnlock) {
                                    val totalSec = unlockRemMs / 1000L
                                    val unlockTimeStr = formatHms(totalSec)
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
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Btn(
                                        label = "Stop Focus Mode",
                                        icon = CyberIcons.Stop,
                                        onClick = { repo.requestEndQuickFocus() },
                                        kind = BtnKind.SECONDARY
                                    )
                                }
                            }
                        }
                    } else {
                        // Focus Mode Inactive
                        val nextUpcomingSched = focus.schedules
                            .filter { it.enabled }
                            .minByOrNull { it.getNextStartMillis() ?: Long.MAX_VALUE }

                        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Focus Mode Inactive",
                                        color = NordicColors.TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val subtitle = if (nextUpcomingSched != null) {
                                        "Next schedule starts at ${clock12(nextUpcomingSched.startMin)}"
                                    } else {
                                        "Instantly block distractions for a targeted interval"
                                    }
                                    Text(
                                        text = subtitle,
                                        color = NordicColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Duration Chips Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                durationOptions.forEach { (mins, lbl) ->
                                    val isSel = (selectedManualDurationMin == mins)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                            .border(1.dp, if (isSel) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                            .clickable { selectedManualDurationMin = mins }
                                            .padding(horizontal = 13.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = if (isSel) NordicColors.OnAccentPrimary else NordicColors.TextPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (selectedManualDurationMin == -1) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(10, 20, 90, 180, 360).forEach { cm ->
                                        val isCustomSel = (customManualDurationMin == cm)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCustomSel) NordicColors.AccentSecondary else NordicColors.CanvasBg)
                                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(8.dp))
                                                .clickable { customManualDurationMin = cm }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "${cm}m",
                                                color = NordicColors.TextPrimary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val finalDurationMin = if (selectedManualDurationMin == -1) customManualDurationMin else selectedManualDurationMin
                            val finalDurationLabel = if (finalDurationMin >= 60) "${finalDurationMin / 60}h ${if (finalDurationMin % 60 > 0) "${finalDurationMin % 60}m" else ""}".trim() else "${finalDurationMin}m"
                            Btn(
                                label = "Start Focus Session ($finalDurationLabel)",
                                icon = CyberIcons.Play,
                                onClick = { repo.startQuickFocus(finalDurationMin) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ----------------------------------------------------
                // Section 2: Daily Schedules Card
                // ----------------------------------------------------
                item(key = "daily_schedules_card") {
                    NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "DAILY SCHEDULES",
                                    color = NordicColors.TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Automate recurring focus windows with custom allowed apps",
                                    color = NordicColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Btn(
                                label = "+ New Schedule",
                                icon = CyberIcons.Plus,
                                onClick = {
                                    editingSchedule = null
                                    scheduleFormLabel = ""
                                    scheduleFormStartMin = 0 // 12:00 AM (0)
                                    scheduleFormEndMin = 360 // 06:00 AM (360)
                                    scheduleFormDays = setOf(1, 2, 3, 4, 5, 6, 7)
                                    scheduleFormAllowedApps = emptySet()
                                    showAddOrEditSchedule = true
                                },
                                kind = BtnKind.PRIMARY
                            )
                        }

                        if (focus.schedules.isEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No daily schedules yet — tap '+ New Schedule' to create a recurring focus window.",
                                color = NordicColors.TextSecondary,
                                fontSize = 12.5.sp
                            )
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                focus.schedules.forEach { s ->
                                    val isActiveNow = s.isRunningAt()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isActiveNow) NordicColors.CardSurfaceHero else NordicColors.CanvasBg)
                                            .border(
                                                1.dp,
                                                if (isActiveNow) NordicColors.AccentPrimary.copy(alpha = 0.6f) else NordicColors.HairlineBorder,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = s.label,
                                                    color = NordicColors.TextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (isActiveNow) {
                                                    val leftSec = s.getRemainingMillisNow() / 1000L
                                                    val leftStr = formatHms(leftSec)
                                                    StatusPillBadge(
                                                        label = "Running Now ($leftStr left)",
                                                        tone = BadgeTone.OK,
                                                        small = true,
                                                        breathing = true
                                                    )
                                                } else if (s.enabled) {
                                                    val nextMs = s.getNextStartMillis()
                                                    val statusStr = if (nextMs != null) formatStartsIn(nextMs) else "Upcoming at ${clock12(s.startMin)}"
                                                    StatusPillBadge(
                                                        label = statusStr,
                                                        tone = BadgeTone.ACCENT,
                                                        small = true
                                                    )
                                                } else {
                                                    StatusPillBadge(
                                                        label = "Disabled",
                                                        tone = BadgeTone.IDLE,
                                                        small = true
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${clock12(s.startMin)} – ${clock12(s.endMin)} (${formatDuration(s.startMin, s.endMin)})",
                                                color = NordicColors.TextSecondary,
                                                fontSize = 12.sp
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Days of Week Pills
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S").forEach { (dow, ch) ->
                                                    val isDayActive = s.daysOfWeek.contains(dow)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isDayActive) NordicColors.AccentPrimary.copy(alpha = 0.85f) else NordicColors.CardSurfaceHero)
                                                            .border(1.dp, NordicColors.HairlineBorder, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = ch,
                                                            color = if (isDayActive) Color.White else NordicColors.TextSecondary.copy(alpha = 0.6f),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Allowed Apps Chip
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable { editingScheduleForApps = s }
                                                ) {
                                                    StatusPillBadge(
                                                        label = "${s.allowedPackages.size} Apps Allowed",
                                                        tone = BadgeTone.IDLE,
                                                        small = true,
                                                        icon = CyberIcons.Apps
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            val schedPendingUntil = focus.pendingScheduleUnlocks[s.id] ?: 0L
                                            val hasSchedPending = schedPendingUntil > 0L
                                            val schedRemMs = (schedPendingUntil - nowMillis).coerceAtLeast(0L)
                                            val action = focus.pendingScheduleActions[s.id] ?: "delete"

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                if (hasSchedPending) {
                                                    val timeStr = formatHms(schedRemMs / 1000L)
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
                                                        onClick = {
                                                            editingSchedule = s
                                                            scheduleFormLabel = s.label
                                                            scheduleFormStartMin = s.startMin
                                                            scheduleFormEndMin = s.endMin
                                                            scheduleFormDays = s.daysOfWeek
                                                            scheduleFormAllowedApps = s.allowedPackages
                                                            showAddOrEditSchedule = true
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = CyberIcons.Edit,
                                                            contentDescription = "Edit",
                                                            tint = NordicColors.TextSecondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(2.dp))
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
                                                    Spacer(modifier = Modifier.width(6.dp))
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
                }

                // ----------------------------------------------------
                // Section 2.5: Distraction & Short-Form Shield
                // ----------------------------------------------------
                item(key = "distraction_shield_section") {
                    SectionHeader(
                        title = "Distraction & Short-Form Shield",
                        icon = CyberIcons.ShieldCheck,
                        subtitle = "Exit short-form video feeds and algorithms while keeping communication intact"
                    )
                }

                item(key = "distraction_yt") {
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
                }

                item(key = "distraction_insta_reels") {
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
                }

                item(key = "distraction_insta_explore") {
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
                }

                item(key = "distraction_fb_reels") {
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
                }

                // ----------------------------------------------------
                // Section 3: Focus Mode Allowed Apps Whitelist Header & Filter
                // ----------------------------------------------------
                item(key = "allowed_apps_header") {
                    Column {
                        SectionHeader(
                            title = "Allowed Essential Apps",
                            icon = CyberIcons.Apps,
                            subtitle = "Apps permitted to open during manual or scheduled focus sessions"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Lock Banner (strictly ONLY when a focus session is actively running right now!)
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
                    }
                }

                // ----------------------------------------------------
                // Section 3: App Rows
                // ----------------------------------------------------
                if (filteredApps.isEmpty()) {
                    item(key = "empty_apps") {
                        Text(
                            text = "No apps found matching query",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(filteredApps.take(40), key = { it.packageName }) { app ->
                        val isAllowed = focus.allowedApps.contains(app.packageName)
                        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
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
            // STUDY MODE TAB (Clean Scrollable Column)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 110.dp), // Clearance for bottom navigation
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (study.active) {
                    // Active Study Lockout State
                    val remSec = study.remainingMs / 1000L
                    val timeStr = formatHms(remSec)
                    val finishClock = formatClockTime(study.until)
                    val totalCycleSec = ((if (study.isBreak) study.breakMinutes else study.focusMinutes) * 60).coerceAtLeast(1)
                    val progressFraction = (remSec.toFloat() / totalCycleSec.toFloat()).coerceIn(0f, 1f)

                    NordicFoliageHeroCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StatusPillBadge(
                                label = if (study.isBreak) "REST BREAK" else "FOCUS SESSION",
                                tone = if (study.isBreak) BadgeTone.OK else BadgeTone.ACCENT,
                                breathing = true,
                                icon = if (study.isBreak) CyberIcons.Check else CyberIcons.Lock
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            NordicArcGauge(
                                progress = progressFraction,
                                size = 185.dp
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = timeStr,
                                        color = NordicColors.TextPrimary,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (study.isBreak) "Break Remaining" else "Study Countdown",
                                        color = NordicColors.TextSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Ends at $finishClock",
                                        color = NordicColors.AccentPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Round ${study.currentRound} of ${study.totalRounds}",
                                color = NordicColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            NordicLinearBar(
                                progress = study.currentRound.toFloat() / study.totalRounds.coerceAtLeast(1).toFloat(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Quick Controls Row (Wi-Fi + Emergency Dialer)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Btn(
                                    label = if (isWifiOn) "Turn Off Wi-Fi" else "Restore Wi-Fi",
                                    icon = if (isWifiOn) CyberIcons.WifiOff else CyberIcons.Wifi,
                                    onClick = { repo.toggleWifi(context) },
                                    kind = BtnKind.SECONDARY,
                                    modifier = Modifier.weight(1f)
                                )
                                Btn(
                                    label = "Emergency Dialer",
                                    icon = CyberIcons.Phone,
                                    onClick = { openEmergencyDialer(context) },
                                    kind = BtnKind.SECONDARY,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Session Actions Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (study.isBreak) {
                                    Btn(
                                        label = "Skip Break",
                                        icon = CyberIcons.Play,
                                        onClick = { repo.skipStudyBreak() },
                                        kind = BtnKind.PRIMARY,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Btn(
                                    label = "End Session",
                                    icon = CyberIcons.Stop,
                                    onClick = { repo.endStudySession() },
                                    kind = BtnKind.SECONDARY,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Active Motivation Quote Card
                    NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(2.dp)) {
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
                    }

                } else {
                    // Study Mode Setup & Configurator (Sequential Vertical Order)

                    // Card 1: Motivation Quote Card
                    NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(2.dp)) {
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
                    }

                    // Card 2: System Readiness & Quick Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Card: Do Not Disturb
                        NordicMicroGridCard(modifier = Modifier.weight(1f)) {
                            Column {
                                Text(
                                    text = "Do Not Disturb",
                                    color = NordicColors.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (study.hasDndAccess) "Granted" else "Required",
                                    color = if (study.hasDndAccess) NordicColors.SuccessMuted else NordicColors.AlertMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                if (!study.hasDndAccess) {
                                    Btn(
                                        label = "Enable DND",
                                        icon = CyberIcons.Dnd,
                                        onClick = { repo.requestDndAccess(context) },
                                        kind = BtnKind.PRIMARY,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Btn(
                                        label = "DND Ready",
                                        icon = CyberIcons.Check,
                                        onClick = {},
                                        kind = BtnKind.GHOST,
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Right Card: Wi-Fi Network
                        NordicMicroGridCard(modifier = Modifier.weight(1f)) {
                            Column {
                                Text(
                                    text = "Wi-Fi Network",
                                    color = NordicColors.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isWifiOn) "Connected" else "Off",
                                    color = if (isWifiOn) NordicColors.SuccessMuted else NordicColors.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Btn(
                                    label = "Toggle Wi-Fi",
                                    icon = if (isWifiOn) CyberIcons.WifiOff else CyberIcons.Wifi,
                                    onClick = { repo.toggleWifi(context) },
                                    kind = BtnKind.SECONDARY,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Card 3: Study Session Configuration Card
                    NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column {
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
                            }

                            // Section A: FOCUS DURATION
                            Column {
                                Text(
                                    text = "FOCUS DURATION",
                                    color = NordicColors.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(15, 25, 45, 60, 90, 120, 180).forEach { m ->
                                        val isSel = (studyFocusMins == m)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                                .border(1.dp, if (isSel) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                                .clickable { studyFocusMins = m }
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
                            }

                            // Section B: BREAK DURATION
                            Column {
                                Text(
                                    text = "BREAK DURATION",
                                    color = NordicColors.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
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
                            }

                            // Section C: TOTAL ROUNDS
                            Column {
                                Text(
                                    text = "TOTAL ROUNDS",
                                    color = NordicColors.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(1, 2, 3, 4, 5, 6).forEach { r ->
                                        val isSel = (studyRounds == r)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                                .border(1.dp, if (isSel) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                                .clickable { studyRounds = r }
                                                .padding(horizontal = 15.dp, vertical = 7.dp)
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
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Section D: Launch Button
                            Btn(
                                label = "Launch Study Lockout ($studyFocusMins min focus, $studyRounds rounds)",
                                icon = CyberIcons.Play,
                                onClick = { repo.startStudySession(studyFocusMins, studyBreakMins, studyRounds) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // Add / Edit Schedule Dialog
    // ==========================================
    if (showAddOrEditSchedule) {
        Dialog(
            onDismissRequest = { showAddOrEditSchedule = false },
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(NordicColors.CardSurface)
                        .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    // Title Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingSchedule != null) "Edit Schedule" else "New Schedule",
                            color = NordicColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showAddOrEditSchedule = false }) {
                            Icon(CyberIcons.Close, contentDescription = "Close", tint = NordicColors.TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Name Field
                    Text(text = "Schedule Name", color = NordicColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CanvasBg)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        if (scheduleFormLabel.isEmpty()) {
                            Text(text = "e.g. Bedtime, Deep Work", color = NordicColors.TextSecondary.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = scheduleFormLabel,
                            onValueChange = { scheduleFormLabel = it },
                            textStyle = TextStyle(color = NordicColors.TextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(NordicColors.AccentPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Start & End Time Pickers (12:00 AM converts to 0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Starts", color = NordicColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                            { _, hourOfDay, minute ->
                                                scheduleFormStartMin = hourOfDay * 60 + minute
                                            },
                                            scheduleFormStartMin / 60,
                                            scheduleFormStartMin % 60,
                                            false
                                        ).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = clock12(scheduleFormStartMin),
                                    color = NordicColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Ends", color = NordicColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                            { _, hourOfDay, minute ->
                                                scheduleFormEndMin = hourOfDay * 60 + minute
                                            },
                                            scheduleFormEndMin / 60,
                                            scheduleFormEndMin % 60,
                                            false
                                        ).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = clock12(scheduleFormEndMin),
                                    color = NordicColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Window: ${formatDuration(scheduleFormStartMin, scheduleFormEndMin)}",
                        color = NordicColors.AccentPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Days of Week
                    Text(text = "Days of Week", color = NordicColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S").forEach { (dow, ch) ->
                            val isSel = scheduleFormDays.contains(dow)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSel) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                    .border(1.dp, if (isSel) NordicColors.AccentPrimary else NordicColors.HairlineBorder, CircleShape)
                                    .clickable {
                                        scheduleFormDays = if (isSel) {
                                            if (scheduleFormDays.size > 1) scheduleFormDays - dow else scheduleFormDays
                                        } else {
                                            scheduleFormDays + dow
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ch,
                                    color = if (isSel) Color.White else NordicColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Select Allowed Apps Button
                    Btn(
                        label = "Select Allowed Apps (${scheduleFormAllowedApps.size} selected)",
                        icon = CyberIcons.Apps,
                        onClick = { showScheduleFormAppPicker = true },
                        kind = BtnKind.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Btn(
                            label = "Cancel",
                            onClick = { showAddOrEditSchedule = false },
                            kind = BtnKind.GHOST,
                            modifier = Modifier.weight(1f)
                        )
                        Btn(
                            label = "Save Schedule",
                            icon = CyberIcons.Check,
                            onClick = {
                                val label = scheduleFormLabel.ifBlank { "Focus Window" }
                                if (editingSchedule != null) {
                                    repo.updateSchedule(
                                        id = editingSchedule!!.id,
                                        label = label,
                                        startMin = scheduleFormStartMin,
                                        endMin = scheduleFormEndMin,
                                        enabled = editingSchedule!!.enabled,
                                        allowedApps = scheduleFormAllowedApps,
                                        daysOfWeek = scheduleFormDays
                                    )
                                } else {
                                    repo.addSchedule(
                                        label = label,
                                        startMin = scheduleFormStartMin,
                                        endMin = scheduleFormEndMin,
                                        allowedApps = scheduleFormAllowedApps,
                                        daysOfWeek = scheduleFormDays
                                    )
                                }
                                showAddOrEditSchedule = false
                            },
                            kind = BtnKind.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // Modal App Picker for Schedule Form
    if (showScheduleFormAppPicker) {
        ScheduleAppPickerDialog(
            title = "Allowed Apps for Schedule",
            initialAllowed = scheduleFormAllowedApps,
            allApps = apps,
            isLocked = false,
            unlockTargetMs = 0L,
            nowMillis = nowMillis,
            onToggleApp = { pkg ->
                scheduleFormAllowedApps = if (scheduleFormAllowedApps.contains(pkg)) {
                    scheduleFormAllowedApps - pkg
                } else {
                    scheduleFormAllowedApps + pkg
                }
            },
            onAbortUnlock = {},
            onDismiss = { showScheduleFormAppPicker = false }
        )
    }

    // Modal App Picker for Existing Schedule Card Quick Tap
    editingScheduleForApps?.let { sched ->
        val schedLockKey = "sched_apps_${sched.id}"
        val unlockTarget = BlockerStore.settingUnlockTarget(context, schedLockKey)
        val isLocked = sched.isRunningAt() || protection.isActive
        ScheduleAppPickerDialog(
            title = "Allowed Apps — ${sched.label}",
            initialAllowed = sched.allowedPackages,
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
    allApps: List<AppItem>,
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
                            compareByDescending<AppItem> { initialAllowed.contains(it.packageName) }
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
