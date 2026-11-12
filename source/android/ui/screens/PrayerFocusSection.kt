package com.blocker.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blocker.PrayerSlot
import com.blocker.data.BlockerRepository
import com.blocker.data.FocusState
import com.blocker.ui.components.*
import com.blocker.ui.theme.NordicColors
import java.time.DayOfWeek
import java.time.LocalDateTime

private fun prayerClock12(hour24: Int, minute: Int): String {
    val h = hour24.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format("%d:%02d %s", h12, m, ampm)
}

private fun prayerClock12Total(totalMin: Int): String {
    val clean = ((totalMin % 1440) + 1440) % 1440
    return prayerClock12(clean / 60, clean % 60)
}

private fun prayerHms(seconds: Long): String {
    val sec = seconds.coerceAtLeast(0L)
    val h = sec / 3600L
    val m = (sec % 3600L) / 60L
    val s = sec % 60L
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun prayerStartsInLabel(millis: Long): String {
    val totalMin = (millis / 60_000L).coerceAtLeast(1L)
    val h = totalMin / 60L
    val m = totalMin % 60L
    return when {
        h > 0 && m > 0 -> "Starts in ${h}h ${m}m"
        h > 0 -> "Starts in ${h}h"
        else -> "Starts in ${m}m"
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PrayerFocusTab(
    focus: FocusState,
    nowMillis: Long,
    repo: BlockerRepository
) {
    val context = LocalContext.current
    val slots = focus.prayerSlots
    val jumuahEnabled = remember(slots) { slots.any { it.id == "JUMUAH" && it.isEnabled } }
    val nowDt = remember(nowMillis / 1000L) { LocalDateTime.now() }
    val isFridayToday = nowDt.dayOfWeek == DayOfWeek.FRIDAY

    val activeSlot = slots.firstOrNull { it.isRunningAt(nowDt, jumuahEnabled) }
    val activeRemainingMs = activeSlot?.getRemainingMillisNow(nowDt, jumuahEnabled) ?: 0L

    var editingSlot by remember { mutableStateOf<PrayerSlot?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Status / Readiness Card
        item(key = "prayer_hero_card") {
            if (activeSlot != null) {
                val remSec = (activeRemainingMs / 1000L).coerceAtLeast(0L)
                val totalSlotSec = (activeSlot.durationMinutes.coerceIn(10, 90) * 60L).toFloat()
                NordicFoliageHeroCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusPillBadge(
                            label = "PRAYER FOCUS ACTIVE",
                            tone = BadgeTone.ACCENT,
                            breathing = true,
                            icon = CyberIcons.Clock
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${activeSlot.nameEn} • ${activeSlot.nameBn}",
                            color = NordicColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        NordicArcGauge(
                            progress = (remSec.toFloat() / totalSlotSec).coerceIn(0f, 1f),
                            size = 175.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = prayerHms(remSec),
                                    color = NordicColors.TextPrimary,
                                    fontSize = 25.sp,
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
                                    text = "Runs until ${prayerClock12Total(activeSlot.endTotalMinutes())}",
                                    color = NordicColors.AccentPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val dndLabel = when {
                            !activeSlot.autoDnd -> "Auto-DND Off"
                            focus.hasDndAccess -> "Auto-DND Active"
                            else -> "Grant DND Access Below"
                        }
                        StatusPillBadge(
                            label = dndLabel,
                            tone = if (activeSlot.autoDnd && focus.hasDndAccess) BadgeTone.OK else BadgeTone.IDLE,
                            icon = CyberIcons.Dnd
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Only essential allowed apps are accessible during prayer",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                val upcoming = slots
                    .mapNotNull { s -> s.getNextStartMillis(nowDt, jumuahEnabled)?.let { s to it } }
                    .minByOrNull { it.second }

                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(
                                text = "Prayer Time (Salah) Focus",
                                color = NordicColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (upcoming != null) {
                                    "Next: ${upcoming.first.nameEn} at ${prayerClock12(upcoming.first.startHour24, upcoming.first.startMinute)} (${prayerStartsInLabel(upcoming.second)})"
                                } else {
                                    "Manually set and adjust all 5 daily prayers plus Friday Jumu'ah as seasons shift."
                                },
                                color = if (upcoming != null) NordicColors.AccentSecondary else NordicColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (upcoming != null) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NordicColors.CardSurfaceSecondary)
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = CyberIcons.Dnd,
                                    contentDescription = null,
                                    tint = if (focus.hasDndAccess) NordicColors.SuccessMuted else NordicColors.AccentSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Auto Do Not Disturb",
                                        color = NordicColors.TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (focus.hasDndAccess) "Permission granted for prayer silencing" else "Required to silence calls & alerts during prayer",
                                        color = NordicColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (!focus.hasDndAccess) {
                                Btn(
                                    label = "Enable DND",
                                    icon = CyberIcons.Dnd,
                                    compact = true,
                                    onClick = { repo.requestDndAccess(context) },
                                    kind = BtnKind.PRIMARY
                                )
                            } else {
                                StatusPillBadge(
                                    label = "Ready",
                                    tone = BadgeTone.OK,
                                    small = true,
                                    icon = CyberIcons.Check
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Daily & Friday Jumu'ah Prayer Cards
        items(slots, key = { it.id }) { slot ->
            val pendingTarget = focus.pendingPrayerUnlocks[slot.id] ?: 0L
            val isPendingUnlock = pendingTarget > 0L
            val unlockRemSec = ((pendingTarget - nowMillis) / 1000L).coerceAtLeast(0L)
            val isRunning = slot.isRunningAt(nowDt, jumuahEnabled)
            val isDhuhrOverriddenToday = (slot.id == "DHUHR" && isFridayToday && jumuahEnabled && slot.isEnabled)

            NordicMicroGridCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header: Name + Bangla label + Status badge + Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = slot.nameEn,
                                    color = NordicColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = slot.nameBn,
                                    color = NordicColors.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            if (slot.id == "JUMUAH" || isDhuhrOverriddenToday || isRunning) {
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isRunning) {
                                        StatusPillBadge(
                                            label = "Active Now",
                                            tone = BadgeTone.OK,
                                            small = true,
                                            breathing = true
                                        )
                                    }
                                    if (slot.id == "JUMUAH") {
                                        StatusPillBadge(
                                            label = "Replaces Dhuhr on Fridays",
                                            tone = BadgeTone.ACCENT,
                                            small = true
                                        )
                                    }
                                    if (isDhuhrOverriddenToday) {
                                        StatusPillBadge(
                                            label = "Jumu'ah Active Today",
                                            tone = BadgeTone.IDLE,
                                            small = true
                                        )
                                    }
                                }
                            }
                        }

                        NordicSwitch(
                            checked = slot.isEnabled,
                            onCheckedChange = { wantEnable ->
                                repo.requestTogglePrayerSlot(slot.id, wantEnable)
                            }
                        )
                    }

                    // Time Window + Duration + DND quick controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Start Time (Direct TimePickerDialog)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NordicColors.CardSurfaceHero)
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            repo.updatePrayerSlotConfig(
                                                slotId = slot.id,
                                                startHour24 = h,
                                                startMinute = m,
                                                durationMinutes = slot.durationMinutes,
                                                autoDnd = slot.autoDnd
                                            )
                                        },
                                        slot.startHour24,
                                        slot.startMinute,
                                        false
                                    ).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = CyberIcons.Clock,
                                contentDescription = null,
                                tint = NordicColors.AccentSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${prayerClock12(slot.startHour24, slot.startMinute)} - ${prayerClock12Total(slot.endTotalMinutes())}",
                                color = NordicColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Duration Pill (Opens Edit Dialog)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NordicColors.CardSurfaceSecondary)
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                .clickable { editingSlot = slot }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${slot.durationMinutes}m",
                                color = NordicColors.AccentSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Adjust Button (Opens Seasonal Config Dialog)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NordicColors.CardSurfaceSecondary)
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                                .clickable { editingSlot = slot }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = CyberIcons.Edit,
                                    contentDescription = "Adjust ${slot.nameEn}",
                                    tint = NordicColors.TextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (slot.autoDnd) "DND" else "Adjust",
                                    color = if (slot.autoDnd) NordicColors.SuccessMuted else NordicColors.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Pending Delay Unlock Row (when disabling while Protection Mode is armed)
                    if (isPendingUnlock) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatusPillBadge(
                                label = "Disabling in ${prayerHms(unlockRemSec)}",
                                tone = BadgeTone.WARN,
                                small = true,
                                breathing = true,
                                icon = CyberIcons.Clock
                            )
                            Btn(
                                label = "Cancel",
                                icon = CyberIcons.Close,
                                compact = true,
                                onClick = { repo.cancelPrayerSlotUnlock(slot.id) },
                                kind = BtnKind.GHOST
                            )
                        }
                    }
                }
            }
        }
    }

    editingSlot?.let { slot ->
        PrayerSlotConfigDialog(
            slot = slot,
            onSave = { h, m, dur, autoDnd ->
                repo.updatePrayerSlotConfig(slot.id, h, m, dur, autoDnd)
                editingSlot = null
            },
            onDismiss = { editingSlot = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrayerSlotConfigDialog(
    slot: PrayerSlot,
    onSave: (hour24: Int, minute: Int, durationMin: Int, autoDnd: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hour24 by remember(slot.id) { mutableStateOf(slot.startHour24) }
    var minute by remember(slot.id) { mutableStateOf(slot.startMinute) }
    var durationMin by remember(slot.id) { mutableStateOf(slot.durationMinutes.coerceIn(10, 90)) }
    var autoDnd by remember(slot.id) { mutableStateOf(slot.autoDnd) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(NordicColors.CardSurface)
                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${slot.nameEn} (${slot.nameBn})",
                            color = NordicColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Seasonal start time & focus duration",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = CyberIcons.Close,
                        contentDescription = "Close",
                        tint = NordicColors.TextSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onDismiss() }
                    )
                }

                // Start Time Picker Box
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Start Time",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NordicColors.CardSurfaceSecondary)
                            .border(1.dp, NordicColors.TopEdgeHighlight, RoundedCornerShape(12.dp))
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        hour24 = h
                                        minute = m
                                    },
                                    hour24,
                                    minute,
                                    false
                                ).show()
                            }
                            .padding(vertical = 12.dp, horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${prayerClock12(hour24, minute)}  (Ends ${prayerClock12Total((hour24 * 60 + minute + durationMin) % 1440)})",
                            color = NordicColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Duration Presets + Stepper (10..90 mins)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duration (10–90 mins)",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NordicColors.CardSurfaceHero)
                                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(8.dp))
                                    .clickable { durationMin = (durationMin - 5).coerceIn(10, 90) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", color = NordicColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "${durationMin}m",
                                color = NordicColors.AccentSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NordicColors.CardSurfaceHero)
                                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(8.dp))
                                    .clickable { durationMin = (durationMin + 5).coerceIn(10, 90) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = NordicColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 20, 25, 30, 45, 60, 90).forEach { m ->
                            val isSel = (durationMin == m)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSel) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                                    .border(1.dp, if (isSel) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(9.dp))
                                    .clickable { durationMin = m }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "${m}m",
                                    color = if (isSel) NordicColors.OnAccentPrimary else NordicColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Auto-DND Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NordicColors.CardSurfaceSecondary)
                        .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "Auto Do Not Disturb",
                            color = NordicColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Silence notifications during this prayer",
                            color = NordicColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    NordicSwitch(
                        checked = autoDnd,
                        onCheckedChange = { autoDnd = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Cancel",
                        icon = CyberIcons.Close,
                        onClick = onDismiss,
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = "Save Prayer",
                        icon = CyberIcons.Check,
                        onClick = { onSave(hour24, minute, durationMin, autoDnd) },
                        kind = BtnKind.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
