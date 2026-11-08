package com.blocker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.blocker.ui.theme.NordicColors

/**
 * Procedural background texture applying:
 * 1. Base matte slate fill (0xFF171923).
 * 2. Crisp 1.dp dot grid spaced every 24.dp (0xFF252A3A, alpha = 0.55f).
 * 3. 5 smooth cubic Bezier topographic contour waves in top 40% (0xFF232838, alpha = 0.45f).
 */
fun Modifier.nordicBackgroundTexture(): Modifier = this.drawWithCache {
    val spacing = 24.dp.toPx()
    val dotRadius = 0.5.dp.toPx()
    val dotColor = Color(0xFF1D163B).copy(alpha = 0.65f)
    val contourColor = Color(0xFF231A47).copy(alpha = 0.50f)
    val contourStroke = Stroke(width = 1.dp.toPx())

    val top40 = size.height * 0.40f
    val waveOffsets = listOf(0.12f, 0.28f, 0.48f, 0.68f, 0.88f)

    val contourPaths = waveOffsets.map { ratio ->
        val yBase = top40 * ratio
        Path().apply {
            moveTo(0f, yBase)
            cubicTo(
                size.width * 0.28f, yBase - 18.dp.toPx(),
                size.width * 0.68f, yBase + 22.dp.toPx(),
                size.width, yBase - 6.dp.toPx()
            )
        }
    }

    onDrawBehind {
        // Base fill
        drawRect(NordicColors.CanvasBg)

        // Architectural Dot-Matrix Grid
        var x = 0f
        while (x <= size.width) {
            var y = 0f
            while (y <= size.height) {
                drawCircle(dotColor, dotRadius, Offset(x, y))
                y += spacing
            }
            x += spacing
        }

        // Topographic Contour Waves
        for (path in contourPaths) {
            drawPath(path, contourColor, style = contourStroke)
        }
    }
}

/**
 * Full-screen container with procedural Dark Nordic textures.
 */
@Composable
fun NordicTexturedScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .nordicBackgroundTexture(),
        content = content
    )
}

/**
 * Flagship Hero Card with procedural monochrome botanical palm-leaf / architectural frond watermark
 * rendered across the right side in layered slate tones (0xFF1F2433, 0xFF262C3F, 0xFF2E354B).
 * Clipped to RoundedCornerShape(24.dp) with 1.dp HairlineBorder and top rim highlight.
 */
@Composable
fun NordicFoliageHeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(24.dp)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(
        modifier = modifier
            .clip(cardShape)
            .drawWithCache {
                val slateLayer1 = Color(0xFF1E163D)
                val slateLayer2 = Color(0xFF281E52)
                val slateLayer3 = Color(0xFF35276B)

                val w = size.width
                val h = size.height

                // Stem curve extending from bottom right towards top center-right
                val stemPath = Path().apply {
                    moveTo(w * 0.98f, h * 0.98f)
                    cubicTo(w * 0.92f, h * 0.65f, w * 0.85f, h * 0.35f, w * 0.72f, h * 0.08f)
                }

                // Botanical frond leaflets branching out leftward & upward
                val frondPathsLayer1 = listOf(
                    Path().apply {
                        moveTo(w * 0.95f, h * 0.85f)
                        cubicTo(w * 0.80f, h * 0.82f, w * 0.70f, h * 0.74f, w * 0.62f, h * 0.70f)
                        cubicTo(w * 0.72f, h * 0.76f, w * 0.86f, h * 0.82f, w * 0.94f, h * 0.86f)
                        close()
                    },
                    Path().apply {
                        moveTo(w * 0.90f, h * 0.65f)
                        cubicTo(w * 0.72f, h * 0.60f, w * 0.60f, h * 0.48f, w * 0.52f, h * 0.42f)
                        cubicTo(w * 0.65f, h * 0.52f, w * 0.80f, h * 0.60f, w * 0.89f, h * 0.66f)
                        close()
                    },
                    Path().apply {
                        moveTo(w * 0.84f, h * 0.45f)
                        cubicTo(w * 0.68f, h * 0.38f, w * 0.58f, h * 0.28f, w * 0.50f, h * 0.20f)
                        cubicTo(w * 0.62f, h * 0.30f, w * 0.76f, h * 0.40f, w * 0.83f, h * 0.46f)
                        close()
                    }
                )

                val frondPathsLayer2 = listOf(
                    Path().apply {
                        moveTo(w * 0.96f, h * 0.75f)
                        cubicTo(w * 0.82f, h * 0.72f, w * 0.74f, h * 0.62f, w * 0.68f, h * 0.58f)
                        cubicTo(w * 0.76f, h * 0.66f, w * 0.88f, h * 0.72f, w * 0.95f, h * 0.76f)
                        close()
                    },
                    Path().apply {
                        moveTo(w * 0.88f, h * 0.55f)
                        cubicTo(w * 0.74f, h * 0.48f, w * 0.64f, h * 0.38f, w * 0.56f, h * 0.30f)
                        cubicTo(w * 0.68f, h * 0.42f, w * 0.80f, h * 0.50f, w * 0.87f, h * 0.56f)
                        close()
                    },
                    Path().apply {
                        moveTo(w * 0.80f, h * 0.35f)
                        cubicTo(w * 0.66f, h * 0.26f, w * 0.58f, h * 0.16f, w * 0.52f, h * 0.08f)
                        cubicTo(w * 0.62f, h * 0.20f, w * 0.74f, h * 0.30f, w * 0.79f, h * 0.36f)
                        close()
                    }
                )

                val topHighlightY = 1.dp.toPx()
                val topHighlightStartX = 24.dp.toPx()
                val topHighlightEndX = w - 24.dp.toPx()

                onDrawBehind {
                    // Deep Charcoal Hero Surface
                    drawRect(NordicColors.CardSurfaceHero)

                    // Layer 1 Slate Watermark
                    for (p in frondPathsLayer1) {
                        drawPath(p, slateLayer1)
                    }

                    // Layer 2 Slate Watermark
                    for (p in frondPathsLayer2) {
                        drawPath(p, slateLayer2)
                    }

                    // Stem stroke
                    drawPath(stemPath, slateLayer3, style = Stroke(width = 1.5.dp.toPx()))

                    // Top Edge Highlight along upper rim
                    drawLine(
                        color = NordicColors.TopEdgeHighlight,
                        start = Offset(topHighlightStartX, topHighlightY),
                        end = Offset(topHighlightEndX, topHighlightY),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .border(1.dp, NordicColors.HairlineBorder, cardShape)
            .then(clickModifier)
            .padding(20.dp),
        content = content
    )
}

/**
 * Secondary feature card with subtle corner crosshair ticks and 1.dp HairlineBorder.
 * Used for Secondary Feature Cards, Shield Toggles, and App Rows.
 */
@Composable
fun NordicMicroGridCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(NordicColors.CardSurface)
            .drawWithCache {
                val tickColor = Color(0xFF2B224D)
                val tickLen = 6.dp.toPx()
                val tickStroke = 1.dp.toPx()
                val pad = 10.dp.toPx()
                val w = size.width
                val h = size.height

                onDrawBehind {
                    // Top-Left corner crosshair
                    drawLine(tickColor, Offset(pad, pad), Offset(pad + tickLen, pad), tickStroke)
                    drawLine(tickColor, Offset(pad, pad), Offset(pad, pad + tickLen), tickStroke)

                    // Top-Right corner crosshair
                    drawLine(tickColor, Offset(w - pad - tickLen, pad), Offset(w - pad, pad), tickStroke)
                    drawLine(tickColor, Offset(w - pad, pad), Offset(w - pad, pad + tickLen), tickStroke)

                    // Bottom-Left corner crosshair
                    drawLine(tickColor, Offset(pad, h - pad), Offset(pad + tickLen, h - pad), tickStroke)
                    drawLine(tickColor, Offset(pad, h - pad - tickLen), Offset(pad, h - pad), tickStroke)

                    // Bottom-Right corner crosshair
                    drawLine(tickColor, Offset(w - pad - tickLen, h - pad), Offset(w - pad, h - pad), tickStroke)
                    drawLine(tickColor, Offset(w - pad, h - pad - tickLen), Offset(w - pad, h - pad), tickStroke)
                }
            }
            .border(1.dp, NordicColors.HairlineBorder, cardShape)
            .then(clickModifier)
            .padding(16.dp),
        content = content
    )
}
