package com.blocker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.blocker.ui.theme.NordicColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Brand Top Bar:
 * - Left 4-dot geometric vector icon
 * - Centered "P R A C H I R" in wide-tracked uppercase (13.sp, letterSpacing = 0.36.em, FontWeight.SemiBold, 0xFFF2F4F8)
 * - Right shield/status vector icon
 */
@Composable
fun BrandTopBar(
    isProtected: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left 4-dot geometric vector icon (2x2 grid)
        Canvas(modifier = Modifier.size(16.dp)) {
            val dotRadius = 1.5.dp.toPx()
            val color = NordicColors.AccentPrimary
            drawCircle(color, dotRadius, Offset(3.dp.toPx(), 3.dp.toPx()))
            drawCircle(color, dotRadius, Offset(13.dp.toPx(), 3.dp.toPx()))
            drawCircle(color, dotRadius, Offset(3.dp.toPx(), 13.dp.toPx()))
            drawCircle(color, dotRadius, Offset(13.dp.toPx(), 13.dp.toPx()))
        }

        // Centered "B L O C K E R" wide-tracked branding
        Text(
            text = "B L O C K E R",
            color = NordicColors.TextPrimary,
            fontSize = 13.sp,
            letterSpacing = 0.36.em,
            fontWeight = FontWeight.SemiBold
        )

        // Right shield/status vector icon
        Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = "Status",
            tint = if (isProtected) NordicColors.AccentPrimary else NordicColors.AlertMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Hero Numerals: 38.sp to 44.sp Light/Regular tabular numbers (fontFeatureSettings = "tnum")
 * with clean 12.sp 0xFF7E869A caption underneath.
 */
@Composable
fun HeroNumeral(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = NordicColors.TextPrimary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Normal,
                style = TextStyle(fontFeatureSettings = "tnum"),
                lineHeight = 44.sp
            )
            if (unit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    color = NordicColors.TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = caption,
            color = NordicColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Solid-to-Dotted Circular Arc Gauge (NordicArcGauge):
 * Draws a 2.5.dp solid 0xFFECEFF4 arc for active progress ending in a hollow 5.dp 0xFFECEFF4
 * circle node, transitioning seamlessly into evenly spaced 2.dp circular dots in 0xFF3B4154.
 */
@Composable
fun NordicArcGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    val p = progress.coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "arc_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.5.dp.toPx()
            val dotSpacing = 7.dp.toPx()
            val dotDash = 2.dp.toPx()
            val dottedEffect = PathEffect.dashPathEffect(floatArrayOf(dotDash, dotSpacing), 0f)

            val pad = strokeWidth * 2.5f
            val arcSize = Size(this.size.width - pad * 2, this.size.height - pad * 2)
            val arcOffset = Offset(pad, pad)

            val startAngle = 140f
            val totalSweep = 260f
            val activeSweep = totalSweep * p
            val remainingSweep = totalSweep - activeSweep

            // Inactive dotted arc
            if (remainingSweep > 0f) {
                drawArc(
                    color = NordicColors.TrackMuted,
                    startAngle = startAngle + activeSweep,
                    sweepAngle = remainingSweep,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = dottedEffect)
                )
            }

            // Active solid arc
            if (activeSweep > 0f) {
                drawArc(
                    color = NordicColors.AccentPrimary,
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Hollow circle node at the tip of progress with pulsing halo dot
                val radius = arcSize.width / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val tipAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
                val tipX = (center.x + radius * cos(tipAngleRad)).toFloat()
                val tipY = (center.y + radius * sin(tipAngleRad)).toFloat()

                // Pulsing accent halo ring at the tip node
                drawCircle(
                    color = NordicColors.AccentPrimary.copy(alpha = pulseAlpha),
                    radius = 4.dp.toPx() * pulseScale,
                    center = Offset(tipX, tipY)
                )

                // Inner fill matching hero card surface
                drawCircle(
                    color = NordicColors.CardSurfaceHero,
                    radius = 3.dp.toPx(),
                    center = Offset(tipX, tipY)
                )
                // Outer ring node (AccentSecondary soft lavender)
                drawCircle(
                    color = NordicColors.AccentSecondary,
                    radius = 3.dp.toPx(),
                    center = Offset(tipX, tipY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        if (content != null) {
            content()
        }
    }
}

/**
 * Solid-to-Dotted Linear Progress Bar:
 * Draws a 2.5.dp solid 0xFFECEFF4 line for elapsed progress transitioning into
 * spaced 2.dp 0xFF3B4154 dots up to the right-aligned target value.
 */
@Composable
fun NordicLinearBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val p = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val y = size.height / 2f
        val w = size.width
        val activeW = w * p
        val strokeWidth = 2.5.dp.toPx()

        // Active solid line
        if (activeW > 0f) {
            drawLine(
                color = NordicColors.AccentPrimary,
                start = Offset(0f, y),
                end = Offset(activeW, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Remaining dotted line
        if (activeW < w) {
            val dotDash = 2.dp.toPx()
            val dotSpacing = 6.dp.toPx()
            val dottedEffect = PathEffect.dashPathEffect(floatArrayOf(dotDash, dotSpacing), 0f)
            drawLine(
                color = NordicColors.TrackMuted,
                start = Offset(activeW, y),
                end = Offset(w, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dottedEffect
            )
        }
    }
}

/**
 * Home Dotted Wave Activity Graph:
 * 7-day protection curve ("M  T  W  T  F  S  S") drawn with spaced 2.5.dp dots along
 * a smooth Bezier curve, featuring a highlighted ring node and a crisp 0xFFECEFF4 pill
 * callout tag (with 0xFF171923 text) on the active day.
 */
@Composable
fun HomeDottedWaveActivityGraph(
    modifier: Modifier = Modifier,
    activeDayIndex: Int = 6 // 0..6 (default Sunday)
) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    // Normalized sample points for the smooth 7-day curve
    val pointsRatio = listOf(0.35f, 0.65f, 0.45f, 0.80f, 0.60f, 0.90f, 1.0f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height - 18.dp.toPx() // clearance for callout pill
                val baselineY = h + 6.dp.toPx()

                val stepX = w / (days.size - 1)
                val coordinates = pointsRatio.mapIndexed { idx, ratio ->
                    val x = idx * stepX
                    val y = baselineY - (ratio * h * 0.75f)
                    Offset(x, y)
                }

                // Build smooth Bezier path
                val wavePath = Path().apply {
                    moveTo(coordinates[0].x, coordinates[0].y)
                    for (i in 0 until coordinates.size - 1) {
                        val p0 = coordinates[i]
                        val p1 = coordinates[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                val dottedEffect = PathEffect.dashPathEffect(
                    floatArrayOf(2.5.dp.toPx(), 6.5.dp.toPx()), 0f
                )

                // Dotted Bezier wave
                drawPath(
                    path = wavePath,
                    color = NordicColors.TrackMuted,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = dottedEffect)
                )

                // Active day ring node
                val activeCoord = coordinates[activeDayIndex.coerceIn(0, coordinates.size - 1)]

                // Solid wave up to active point
                val activeWavePath = Path().apply {
                    moveTo(coordinates[0].x, coordinates[0].y)
                    for (i in 0 until activeDayIndex) {
                        val p0 = coordinates[i]
                        val p1 = coordinates[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }
                drawPath(
                    path = activeWavePath,
                    color = NordicColors.AccentPrimary,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = dottedEffect)
                )

                // Highlighted ring node
                drawCircle(
                    color = NordicColors.CardSurface,
                    radius = 5.dp.toPx(),
                    center = activeCoord
                )
                drawCircle(
                    color = NordicColors.AccentPrimary,
                    radius = 5.dp.toPx(),
                    center = activeCoord,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Floating 0xFFECEFF4 pill callout tag with 0xFF171923 text on the active day
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NordicColors.AccentPrimary)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "ACTIVE",
                    color = NordicColors.OnAccentPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Day labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEachIndexed { idx, day ->
                val isActive = idx == activeDayIndex
                Text(
                    text = day,
                    color = if (isActive) NordicColors.TextPrimary else NordicColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Custom Nordic Capsule Switch (NordicSwitch):
 * 48.dp x 26.dp pill toggle:
 * - When ON: 0xFF8B5CF6 with 0xFFFFFFFF thumb
 * - When OFF: 0xFF0D0A1C with 0xFF2E2552 border and 0xFF9E94C4 thumb
 */
@Composable
fun NordicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = tween(durationMillis = 150),
        label = "switch_thumb"
    )

    val capsuleShape = RoundedCornerShape(13.dp)

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 26.dp)
            .clip(capsuleShape)
            .background(if (checked) NordicColors.AccentPrimary else Color(0xFF0D0A1C))
            .then(
                if (!checked) Modifier.border(1.dp, NordicColors.TrackMuted, capsuleShape)
                else Modifier
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 1.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) NordicColors.OnAccentPrimary else NordicColors.TextSecondary)
        )
    }
}
