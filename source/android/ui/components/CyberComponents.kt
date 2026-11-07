package com.blocker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.R
import com.blocker.ui.theme.*

enum class CardTone {
    DEFAULT, OK, ACCENT, DANGER
}

enum class BadgeTone {
    OK, WARN, DANGER, ACCENT, IDLE
}

enum class BtnKind {
    PRIMARY, SECONDARY, GHOST, DANGER
}

/**
 * 3D Bento Squircle Card Surface
 * Dark slate-navy surface (#0E1322 / #0E1726), 18dp corner radius.
 * Active cards support luminous emerald highlight.
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tone: CardTone = CardTone.DEFAULT,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)

    val bgBrush = when {
        active || tone == CardTone.OK -> Brush.verticalGradient(listOf(Color(0xFF0F2231), Color(0xFF09141F)))
        tone == CardTone.ACCENT -> Brush.verticalGradient(listOf(Color(0xFF1B1336), Color(0xFF0E0B1A)))
        tone == CardTone.DANGER -> Brush.verticalGradient(listOf(Color(0xFF26101B), Color(0xFF14080F)))
        else -> Brush.verticalGradient(listOf(CyberCardTop, CyberCardBottom))
    }

    val borderBrush = when {
        active || tone == CardTone.OK -> Brush.verticalGradient(listOf(CyberOk.copy(alpha = 0.7f), CyberOk.copy(alpha = 0.25f)))
        tone == CardTone.ACCENT -> Brush.verticalGradient(listOf(CyberAccent.copy(alpha = 0.75f), CyberAccent.copy(alpha = 0.25f)))
        tone == CardTone.DANGER -> Brush.verticalGradient(listOf(CyberDanger.copy(alpha = 0.7f), CyberDanger.copy(alpha = 0.25f)))
        else -> Brush.verticalGradient(listOf(CyberCardBorderGlow.copy(alpha = 0.45f), CyberCardBorder))
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .then(clickModifier)
            .padding(18.dp),
        content = content
    )
}

/**
 * Luminous Status Pill Badge
 * Supports breathing glow animation on active states (e.g. 'Fortress Live', 'Airtight Lock', 'Healthy').
 */
@Composable
fun StatusPillBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.OK,
    icon: ImageVector? = null,
    breathing: Boolean = false,
    small: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
    val alphaAnim by if (breathing) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.60f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val (bg, border, textClr) = when (tone) {
        BadgeTone.OK -> Triple(CyberOkSoft, CyberOk.copy(alpha = 0.6f), CyberOkGlow)
        BadgeTone.WARN -> Triple(CyberWarnSoft, CyberWarn.copy(alpha = 0.6f), CyberWarn)
        BadgeTone.DANGER -> Triple(CyberDangerSoft, CyberDanger.copy(alpha = 0.6f), CyberDanger)
        BadgeTone.ACCENT -> Triple(CyberAccentSoft, CyberAccent.copy(alpha = 0.6f), CyberAccentGlow)
        BadgeTone.IDLE -> Triple(CyberCardSurface, CyberCardBorder, CyberTextSub)
    }

    val shape = RoundedCornerShape(14.dp)
    val padH = if (small) 8.dp else 12.dp
    val padV = if (small) 3.dp else 5.dp
    val fontSz = if (small) 10.5.sp else 12.sp

    Row(
        modifier = modifier
            .alpha(alphaAnim)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textClr,
                modifier = Modifier.size(if (small) 11.dp else 13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(if (small) 5.dp else 6.dp)
                    .clip(CircleShape)
                    .background(textClr)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = label,
            color = textClr,
            fontSize = fontSz,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
    }
}

/**
 * 2x2 Bento Stat Card Component
 * Crisp tabular white number, purple squircle icon well in top right,
 * and cool muted slate-gray label.
 */
@Composable
fun BentoStatCard(
    value: Int,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(
        modifier = modifier
            .clip(shape)
            .background(CyberCard)
            .border(1.dp, CyberCardBorder, shape)
            .then(clickModifier)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "$value",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(CyberAccentWell)
                    .border(1.dp, CyberAccent.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            color = CyberTextSub,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Modern Action Button (Primary gradient, Secondary outline, Ghost)
 */
@Composable
fun Btn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: BtnKind = BtnKind.PRIMARY,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)

    val (bgBrush, borderColor, textClr) = when (kind) {
        BtnKind.PRIMARY -> Triple(
            Brush.horizontalGradient(listOf(CyberAccent, CyberAccentGlow)),
            null,
            Color.White
        )
        BtnKind.SECONDARY -> Triple(
            Brush.horizontalGradient(listOf(CyberCardSurface, CyberCardSurface)),
            CyberCardBorder,
            Color.White
        )
        BtnKind.GHOST -> Triple(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
            CyberCardBorder,
            CyberTextSub
        )
        BtnKind.DANGER -> Triple(
            Brush.horizontalGradient(listOf(CyberDanger, Color(0xFFB91C1C))),
            null,
            Color.White
        )
    }

    val borderModifier = if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) bgBrush else Brush.horizontalGradient(listOf(CyberCardSurface, CyberCardSurface)))
            .then(borderModifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
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
                    tint = if (enabled) textClr else CyberTextSub,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                color = if (enabled) textClr else CyberTextSub,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * CyberSwitch with smooth spring animation and sleek violet track
 */
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

/**
 * Countdown delay lock bar
 */
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
            .border(1.dp, CyberAccent.copy(alpha = 0.45f), shape)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberAccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CyberIcons.Lock,
                    contentDescription = null,
                    tint = CyberAccentGlow,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unlock timer active",
                    color = CyberText,
                    fontSize = 13.5.sp,
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

/**
 * Top App Header matching the React Native design
 */
@Composable
fun TopHeader(
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF0F1528), Color(0xFF0A0E1A))))
            .border(1.dp, Brush.horizontalGradient(listOf(Color(0x457C3AED), Color(0x157C3AED))), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF16122C))
                    .border(1.5.dp, CyberAccent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "Blocker Icon",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.width(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Block",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    text = "er",
                    color = Color(0xFFA78BFA),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                )
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp, bottom = 8.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isLive) CyberOk else CyberTextSub)
                )
            }
        }

        StatusPillBadge(
            label = if (isLive) "Fortress Live" else "Protection Paused",
            tone = if (isLive) BadgeTone.OK else BadgeTone.IDLE,
            breathing = isLive,
            icon = if (isLive) CyberIcons.Check else null
        )
    }
}

/**
 * Section Header Title
 */
@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = CyberText,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp
            )
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = CyberTextSub,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}
