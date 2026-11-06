package com.blocker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.ui.theme.*

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    isGlowing: Boolean = false,
    glowColor: Color = CyberCardBorderGlow,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val borderModifier = if (isGlowing) {
        Modifier.border(1.5.dp, glowColor, shape)
    } else {
        Modifier.border(1.dp, CyberCardBorder, shape)
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(CyberCard)
            .then(borderModifier)
            .then(clickModifier)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun CyberSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = CyberAccent,
            checkedBorderColor = CyberAccentGlow,
            uncheckedThumbColor = CyberTextSub,
            uncheckedTrackColor = CyberCardSurface,
            uncheckedBorderColor = CyberCardBorder
        )
    )
}

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEmerald: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val brush = if (isEmerald) {
        Brush.horizontalGradient(listOf(CyberOk, Color(0xFF059669)))
    } else {
        Brush.horizontalGradient(listOf(CyberAccent, CyberAccentGlow))
    }

    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(CyberCardSurface, CyberCardSurface)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else CyberTextSub,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else CyberTextSub,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val targetBg = if (isActive) CyberOkSoft else CyberWarnSoft
    val targetBorder = if (isActive) CyberOk else CyberWarn
    val targetText = if (isActive) CyberOkGlow else CyberWarn

    val bg by animateColorAsState(targetBg, tween(200, easing = FastOutSlowInEasing), label = "bg")
    val border by animateColorAsState(targetBorder, tween(200, easing = FastOutSlowInEasing), label = "border")
    val textColor by animateColorAsState(targetText, tween(200, easing = FastOutSlowInEasing), label = "text")

    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(border)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun LockBar(
    pendingUntil: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingUntil <= 0L) return

    val now = System.currentTimeMillis()
    val remainingMs = (pendingUntil - now).coerceAtLeast(0L)
    val remainingSec = remainingMs / 1000L
    val hours = remainingSec / 3600L
    val minutes = (remainingSec % 3600L) / 60L
    val seconds = remainingSec % 60L

    val timeStr = if (hours > 0) {
        String.format("%02dh %02dm %02ds", hours, minutes, seconds)
    } else {
        String.format("%02dm %02ds", minutes, seconds)
    }

    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CyberAccentWell)
            .border(1.dp, CyberAccentSoft, shape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberAccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔒", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unlock timer active",
                    color = CyberText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$timeStr remaining",
                    color = CyberTextSub,
                    fontSize = 12.sp
                )
            }
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = CyberWarn)
            ) {
                Text(text = "Cancel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    icon: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Text(text = icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = CyberText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp
            )
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = CyberTextSub,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
