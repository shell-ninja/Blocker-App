package com.blocker.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.blocker.ui.theme.NordicColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

fun formatCountdownHms(remainingSec: Long): String {
    val s = remainingSec.coerceAtLeast(0L)
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = s % 60L
    return if (h > 0L) {
        String.format("%02d:%02d:%02d", h, m, sec)
    } else {
        String.format("%02d:%02d", m, sec)
    }
}

/**
 * Isolated leaf countdown composable for text labels.
 * Ticks locally every second without triggering recomposition of parent screens.
 */
@Composable
fun LiveCountdownText(
    targetEpochMs: Long,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    color: Color = NordicColors.AlertMuted
) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(targetEpochMs) {
        while (isActive && targetEpochMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
        nowMs = System.currentTimeMillis()
    }

    val remainingSec = ((targetEpochMs - nowMs) / 1000L).coerceAtLeast(0L)
    val formatted = formatCountdownHms(remainingSec)

    Text(
        text = "$prefix$formatted$suffix",
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

/**
 * Isolated leaf countdown composable for status pill badges.
 * Ticks locally every second without triggering recomposition of parent screens.
 */
@Composable
fun LiveCountdownBadge(
    targetEpochMs: Long,
    modifier: Modifier = Modifier,
    prefix: String = "Unlocking in ",
    tone: BadgeTone = BadgeTone.WARN,
    small: Boolean = true
) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(targetEpochMs) {
        while (isActive && targetEpochMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
        nowMs = System.currentTimeMillis()
    }

    val remainingSec = ((targetEpochMs - nowMs) / 1000L).coerceAtLeast(0L)
    val formatted = formatCountdownHms(remainingSec)

    StatusPillBadge(
        label = "$prefix$formatted",
        tone = tone,
        small = small,
        modifier = modifier
    )
}
