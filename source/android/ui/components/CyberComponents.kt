package com.blocker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.R
import com.blocker.BlockerStore
import com.blocker.ui.theme.NordicColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Reusable press-scale tactile click modifier using InteractionSource + spring physics.
 * Scales elements to 0.96f when pressed and springs back to 1.0f on release.
 */
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Breathing / pulsing purple aura halo ring behind active protection shield.
 * Scales from 1.0f to 1.18f with alpha fading 0.55f -> 0.0f over 2000ms.
 */
@Composable
fun PulsingHaloShield(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!isActive) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            content()
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "halo_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_alpha"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(
                    width = 1.5.dp,
                    color = NordicColors.AccentPrimary.copy(alpha = alpha),
                    shape = RoundedCornerShape(14.dp)
                )
        )
        content()
    }
}

/**
 * Animated rolling numbers for countdown timer digits & counters.
 * Digits smoothly roll and fade vertically on change.
 */
@Composable
fun RollingCounterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(
        color = NordicColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { idx, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                },
                label = "digit_$idx"
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    style = style
                )
            }
        }
    }
}

enum class CardTone {
    DEFAULT, OK, ACCENT, DANGER, WARN
}

enum class BadgeTone {
    OK, WARN, DANGER, ACCENT, IDLE
}

enum class BtnKind {
    PRIMARY, SECONDARY, GHOST, DANGER
}

/**
 * Flagship Dark Nordic Bento Card Surface.
 * Elevated Nordic Slate (0xFF1C1F2B), 20.dp corner radius, 1.dp structural border (0xFF2A2E3D),
 * and subtle 1.dp top rim highlight (0xFF3A4054).
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
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    val borderColor = when {
        active || tone == CardTone.OK -> NordicColors.AccentPrimary
        tone == CardTone.DANGER -> NordicColors.AlertMuted
        tone == CardTone.WARN -> Color(0xFFEBCB8B)
        else -> NordicColors.HairlineBorder
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(if (active) NordicColors.CardSurfaceHero else NordicColors.CardSurface)
            .drawWithCache {
                val highlightY = 1.dp.toPx()
                val highlightStartX = 20.dp.toPx()
                val highlightEndX = size.width - 20.dp.toPx()
                onDrawBehind {
                    drawLine(
                        color = NordicColors.TopEdgeHighlight,
                        start = Offset(highlightStartX, highlightY),
                        end = Offset(highlightEndX, highlightY),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .border(1.dp, borderColor, shape)
            .then(clickModifier)
            .padding(18.dp),
        content = content
    )
}

/**
 * Flagship Nordic Status Pill Badge.
 * Active states feature solid Nordic Light Gray / Ice White pill with Dark Nordic ink.
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
            targetValue = 0.70f,
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
        BadgeTone.OK -> Triple(NordicColors.AccentPrimary, NordicColors.AccentPrimary, NordicColors.OnAccentPrimary)
        BadgeTone.WARN -> Triple(Color(0x33EBCB8B), Color(0x66EBCB8B), Color(0xFFEBCB8B))
        BadgeTone.DANGER -> Triple(Color(0x33BF616A), Color(0x88BF616A), NordicColors.AlertMuted)
        BadgeTone.ACCENT -> Triple(NordicColors.AccentPrimary, NordicColors.AccentPrimary, NordicColors.OnAccentPrimary)
        BadgeTone.IDLE -> Triple(NordicColors.CardSurface, NordicColors.HairlineBorder, NordicColors.TextSecondary)
    }

    val shape = RoundedCornerShape(12.dp)
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
        } else if (tone != BadgeTone.OK && tone != BadgeTone.ACCENT) {
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
 * Flagship Bento Stat Card with Light/Regular Tabular Numerals.
 */
@Composable
fun BentoStatCard(
    value: Int,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(20.dp)
    val clickModifier = if (onClick != null) Modifier.pressScaleClickable(onClick = onClick) else Modifier

    Column(
        modifier = modifier
            .clip(shape)
            .background(NordicColors.CardSurface)
            .border(1.dp, NordicColors.HairlineBorder, shape)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .then(clickModifier)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            RollingCounterText(
                text = "$value",
                style = TextStyle(
                    color = NordicColors.TextPrimary,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Normal,
                    fontFeatureSettings = "tnum"
                )
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NordicColors.CardSurfaceHero)
                    .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NordicColors.AccentPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            color = NordicColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Flagship Nordic Button (Primary solid light-gray, Secondary slate outline, Ghost, Danger).
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

    val (bgColor, borderColor, textClr) = when (kind) {
        BtnKind.PRIMARY -> Triple(NordicColors.AccentPrimary, null, NordicColors.OnAccentPrimary)
        BtnKind.SECONDARY -> Triple(NordicColors.CardSurface, NordicColors.HairlineBorder, NordicColors.TextPrimary)
        BtnKind.GHOST -> Triple(Color.Transparent, NordicColors.HairlineBorder, NordicColors.TextSecondary)
        BtnKind.DANGER -> Triple(NordicColors.AlertMuted, null, NordicColors.TextPrimary)
    }

    val borderModifier = if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) bgColor else NordicColors.CardSurface)
            .then(borderModifier)
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
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
                    tint = if (enabled) textClr else NordicColors.TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                color = if (enabled) textClr else NordicColors.TextSecondary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * CyberSwitch mapped directly to flagship NordicSwitch.
 */
@Composable
fun CyberSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    NordicSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled
    )
}

/**
 * Countdown delay lock bar with live ticker and NordicLinearBar.
 */
@Composable
fun LockBar(
    pendingUntil: Long,
    guardOpenUntil: Long = 0L,
    nowMillis: Long = 0L,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingUntil <= 0L && guardOpenUntil <= 0L) return

    var localNow by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(pendingUntil, guardOpenUntil) {
        while (isActive) {
            localNow = System.currentTimeMillis()
            delay(500L)
        }
    }

    val currentNow = if (nowMillis > 0L) nowMillis else localNow

    val isGuardOpen = guardOpenUntil > 0L && currentNow < guardOpenUntil
    val isPendingReady = pendingUntil > 0L && currentNow >= pendingUntil
    val isReady = isGuardOpen || isPendingReady

    val shape = RoundedCornerShape(18.dp)

    if (isReady) {
        val guardRemainingMs = if (guardOpenUntil > 0L) (guardOpenUntil - currentNow).coerceAtLeast(0L) else 0L
        val guardMins = (guardRemainingMs / 1000L) / 60L
        val guardSecs = (guardRemainingMs / 1000L) % 60L
        val windowText = if (guardRemainingMs > 0L) {
            String.format("%02dm %02ds remaining to confirm", guardMins, guardSecs)
        } else {
            "Ready to turn off"
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(NordicColors.CardSurface)
                .border(1.dp, NordicColors.SuccessMuted, shape)
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
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
                        .background(NordicColors.CardSurfaceHero)
                        .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CyberIcons.Check,
                        contentDescription = null,
                        tint = NordicColors.SuccessMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Timer Complete",
                        color = NordicColors.TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = windowText,
                        color = NordicColors.SuccessMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "The waiting period has finished. Confirm below to turn off protection, or cancel to keep your fortress locked.",
                color = NordicColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Btn(
                    label = "Keep Active",
                    onClick = onCancel,
                    kind = BtnKind.GHOST,
                    modifier = Modifier.weight(1f)
                )
                Btn(
                    label = "Confirm & Turn Off",
                    onClick = onConfirm,
                    kind = BtnKind.DANGER,
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    } else {
        val remainingMs = (pendingUntil - currentNow).coerceAtLeast(0L)
        val remainingSec = remainingMs / 1000L
        val hours = remainingSec / 3600L
        val minutes = (remainingSec % 3600L) / 60L
        val seconds = remainingSec % 60L

        val timeStr = if (hours > 0) {
            String.format("%02dh %02dm %02ds", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        val totalSpan = if (BlockerStore.isTimerDebug()) BlockerStore.resolveUnlockDelayDurationMs().toFloat() else 86_400_000f
        val progress = (1f - (remainingMs.toFloat() / totalSpan)).coerceIn(0f, 1f)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(NordicColors.CardSurface)
                .border(1.dp, NordicColors.HairlineBorder, shape)
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
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
                        .background(NordicColors.CardSurfaceHero)
                        .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CyberIcons.Lock,
                        contentDescription = null,
                        tint = NordicColors.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Protection Delay Active",
                        color = NordicColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RollingCounterText(
                            text = timeStr,
                            style = TextStyle(
                                color = NordicColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "remaining",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = NordicColors.AlertMuted)
                ) {
                    Text(text = "Cancel", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Signature Nordic Linear Bar
            NordicLinearBar(progress = progress)

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Strict waiting period active. Protection remains fully engaged until countdown completes and you confirm.",
                color = NordicColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * Top App Header matching the Obsidian & Royal Purple Blocker design.
 */
@Composable
fun TopHeader(
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    BrandTopBar(
        isProtected = isLive,
        modifier = modifier
    )
}

/**
 * Section Header Title.
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
                    tint = NordicColors.AccentPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = NordicColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = NordicColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
