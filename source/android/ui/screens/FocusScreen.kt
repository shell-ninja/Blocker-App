package com.blocker.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.blocker.FocusSchedule
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
    val distraction by repo.distractionShield.collectAsState()
    val apps by repo.installedApps.collectAsState()
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

    // Essential Apps Search
    var appSearchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Segmented Tab Switcher (Focus Mode vs Study Mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0E1322))
                .border(1.dp, Color(0xFF1E2640), RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            // Focus Mode Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selectedTab == 0) CyberAccent else Color.Transparent)
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
                        tint = if (selectedTab == 0) Color.White else CyberTextSub,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Focus Mode",
                        color = if (selectedTab == 0) Color.White else CyberTextSub,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Study Mode Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selectedTab == 1) CyberAccent else Color.Transparent)
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
                        tint = if (selectedTab == 1) Color.White else CyberTextSub,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Study Mode",
                        color = if (selectedTab == 1) Color.White else CyberTextSub,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (study.active) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CyberOk)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
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

                GlowCard(modifier = Modifier.fillMaxWidth(), active = true, tone = CardTone.ACCENT) {
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = timeStr,
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Only essential allowed apps are accessible",
                            color = CyberTextSub,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Btn(
                            label = "End Focus Early",
                            icon = CyberIcons.Stop,
                            onClick = { repo.endQuickFocus() },
                            kind = BtnKind.SECONDARY
                        )
                    }
                }
            } else {
                GlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Quick Focus Countdown",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Instantly block distractions for a targeted interval",
                                color = CyberTextSub,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                                    .background(CyberCardSurface)
                                    .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { repo.startQuickFocus(m) }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = lbl,
                                    color = Color.White,
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

            GlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recurring Windows",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${focus.schedules.size} schedule${if (focus.schedules.size == 1) "" else "s"} configured",
                            color = CyberTextSub,
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
                            .background(CyberBg)
                            .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "New Schedule Details",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Name input
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberCardSurface)
                                    .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (newScheduleLabel.isEmpty()) {
                                    Text(
                                        text = "Schedule name (e.g. Work, Bedtime)",
                                        color = CyberTextSub,
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = newScheduleLabel,
                                    onValueChange = { newScheduleLabel = it },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(CyberAccent),
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
                                    Text(text = "Starts", color = CyberTextSub, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CyberCardSurface)
                                            .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp))
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
                                            color = Color.White,
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
                                        color = CyberOk,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "duration",
                                        color = CyberTextSub,
                                        fontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Ends
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Ends", color = CyberTextSub, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CyberCardSurface)
                                            .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp))
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
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Btn(
                                label = "Save Schedule",
                                icon = CyberIcons.Check,
                                onClick = {
                                    val name = newScheduleLabel.trim().ifEmpty { "Schedule ${focus.schedules.size + 1}" }
                                    repo.addSchedule(name, newStartMin, newEndMin)
                                    newScheduleLabel = ""
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
                        color = CyberTextSub,
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
                                    .background(if (isActiveNow) Color(0xFF0F1E25) else CyberBg)
                                    .border(
                                        1.dp,
                                        if (isActiveNow) CyberOk.copy(alpha = 0.5f) else CyberCardBorder,
                                        RoundedCornerShape(12.dp)
                                    )
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
                                                color = Color.White,
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
                                            color = CyberTextSub,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { repo.deleteSchedule(s.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = CyberIcons.Delete,
                                                contentDescription = "Delete",
                                                tint = CyberDanger,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        CyberSwitch(
                                            checked = s.enabled,
                                            onCheckedChange = { repo.toggleScheduleEnabled(s.id, it) }
                                        )
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
                onCheckedChange = { repo.toggleDistraction("block_yt_shorts", it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Individual Item 2: Instagram Reels
            DistractionItemCard(
                title = "Block Instagram Reels",
                subtitle = "Immediately exits Reels back to your main timeline and messages",
                icon = CyberIcons.Movie,
                checked = distraction.blockInstaReels,
                onCheckedChange = { repo.toggleDistraction("block_insta_reels", it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Individual Item 3: Instagram Explore & Search
            DistractionItemCard(
                title = "Block Instagram Explore",
                subtitle = "Blocks discovery algorithm and search tab to prevent doom-scrolling",
                icon = CyberIcons.Search,
                checked = distraction.blockInstaSearch,
                onCheckedChange = { repo.toggleDistraction("block_insta_search", it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Individual Item 4: Facebook Reels & Videos
            DistractionItemCard(
                title = "Block Facebook Reels & Videos",
                subtitle = "Exits Reels, Watch and video feeds (Stories and text posts stay open)",
                icon = CyberIcons.Movie,
                checked = distraction.blockFbReels,
                onCheckedChange = { repo.toggleDistraction("block_fb_reels", it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Item 5: Reddit Mature Content Lockdown
            GlowCard(modifier = Modifier.fillMaxWidth()) {
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
                                .background(CyberAccentWell)
                                .border(1.dp, CyberAccent.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CyberIcons.Lock,
                                contentDescription = null,
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Reddit Mature Content Lockdown",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enforces 'Show mature content' OFF at the system level",
                                color = CyberTextSub,
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

            GlowCard(modifier = Modifier.fillMaxWidth()) {
                // Search box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberBg)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (appSearchQuery.isEmpty()) {
                        Text(text = "Search apps to whitelist...", color = CyberTextSub, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(CyberAccent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val filteredApps = remember(apps, appSearchQuery, focus.allowedApps) {
                    val q = appSearchQuery.trim().lowercase()
                    val userApps = apps.filter { !it.isSystem }
                    userApps
                        .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                        .sortedWith(
                            compareByDescending<com.blocker.data.AppItem> { focus.allowedApps.contains(it.packageName) }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(15)
                }

                if (filteredApps.isEmpty()) {
                    Text(
                        text = "No apps found matching query",
                        color = CyberTextSub,
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
                                    Text(
                                        text = app.label,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                CyberSwitch(
                                    checked = isAllowed,
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
                            label = if (study.isBreak) "Break Time — Rest Your Mind" else "Deep Study Lockout",
                            tone = if (study.isBreak) BadgeTone.OK else BadgeTone.ACCENT,
                            breathing = true,
                            icon = if (study.isBreak) CyberIcons.Check else CyberIcons.Lock
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = timeStr,
                            color = Color.White,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Round ${study.currentRound} of ${study.totalRounds}",
                            color = CyberTextSub,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

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
                GlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "\"${randomQuote.quote}\"",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "— ${randomQuote.source}",
                        color = Color(0xFFA78BFA),
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

                GlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Customize Study Cycles",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Full device lockout during focus; relax during scheduled breaks.",
                        color = CyberTextSub,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Focus Duration Chips
                    Text(
                        text = "FOCUS DURATION",
                        color = CyberTextSub,
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
                                    .background(if (isSel) CyberAccentSoft else CyberCardSurface)
                                    .border(1.dp, if (isSel) CyberAccent else CyberCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { studyFocusMins = m }
                                    .padding(horizontal = 13.dp, vertical = 7.dp)
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Break Duration Chips
                    Text(
                        text = "BREAK DURATION",
                        color = CyberTextSub,
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Total Rounds Chips
                    Text(
                        text = "TOTAL ROUNDS",
                        color = CyberTextSub,
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
                                    .background(if (isSel) CyberAccentSoft else CyberCardSurface)
                                    .border(1.dp, if (isSel) CyberAccent else CyberCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { studyRounds = r }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "$r",
                                    color = if (isSel) Color.White else CyberTextSub,
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

                // Quote Card
                GlowCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "\"${randomQuote.quote}\"",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "— ${randomQuote.source}",
                        color = Color(0xFFA78BFA),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun DistractionItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    GlowCard(modifier = modifier.fillMaxWidth()) {
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
                        .background(CyberAccentWell)
                        .border(1.dp, CyberAccent.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = CyberTextSub,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            CyberSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
