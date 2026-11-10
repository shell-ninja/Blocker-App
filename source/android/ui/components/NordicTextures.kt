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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.blocker.ui.theme.NordicColors

/**
 * Multi-layer procedural Royal Obsidian background:
 * a) Base Layer: Deep Obsidian (0xFF070512).
 * b) Ambient Royal Purple Glow Mesh:
 *    - Top-right amethyst radial glow (center at 85% X, 12% Y, color 0x407C3AED fading to transparent)
 *    - Bottom-left indigo-violet radial glow (center at 15% X, 85% Y, color 0x304C1D95 fading to transparent)
 * c) Crisp Geometric & Topographic Pattern:
 *    - Isometric hexagonal shield lattice + flowing topographic contour waves using 1.2.dp strokes in 0xFF36266E (alpha = 0.65f)
 *    - Glowing intersection nodes/dots in 0xFF8B5CF6 (alpha = 0.45f).
 */
fun Modifier.royalObsidianTexture(): Modifier = this.drawWithCache {
    val baseColor = Color(0xFF070512)
    val glowAmethyst = Color(0x407C3AED)
    val glowIndigo = Color(0x304C1D95)

    val latticeColor = Color(0xFF36266E).copy(alpha = 0.65f)
    val dotColor = Color(0xFF8B5CF6).copy(alpha = 0.45f)
    val latticeStroke = Stroke(width = 1.2.dp.toPx())
    val dotRadius = 2.2.dp.toPx()

    val w = size.width
    val h = size.height

    // Radial gradients
    val amethystGlowBrush = Brush.radialGradient(
        colors = listOf(glowAmethyst, Color.Transparent),
        center = Offset(w * 0.85f, h * 0.12f),
        radius = (w * 0.75f).coerceAtLeast(100f)
    )

    val indigoGlowBrush = Brush.radialGradient(
        colors = listOf(glowIndigo, Color.Transparent),
        center = Offset(w * 0.15f, h * 0.85f),
        radius = (w * 0.75f).coerceAtLeast(100f)
    )

    // Flowing Topographic Contour Waves
    val contourWaveRatios = listOf(0.10f, 0.22f, 0.38f, 0.52f, 0.68f, 0.82f, 0.94f)
    val contourPaths = contourWaveRatios.map { ratio ->
        val yBase = h * ratio
        Path().apply {
            moveTo(-20f, yBase)
            cubicTo(
                w * 0.28f, yBase - 24.dp.toPx(),
                w * 0.68f, yBase + 28.dp.toPx(),
                w + 20f, yBase - 8.dp.toPx()
            )
        }
    }

    // Isometric Hexagonal Shield Lattice Paths
    val hexRadius = 32.dp.toPx()
    val hexPaths = mutableListOf<Path>()
    val hexCenters = mutableListOf<Offset>()

    val colSpacing = hexRadius * 1.732f
    val rowSpacing = hexRadius * 1.5f

    var row = 0
    var y = 20.dp.toPx()
    while (y <= h + hexRadius) {
        val xOffset = if (row % 2 == 1) colSpacing / 2f else 0f
        var x = xOffset
        while (x <= w + hexRadius) {
            val center = Offset(x, y)
            hexCenters.add(center)
            val p = Path()
            for (i in 0 until 6) {
                val angleRad = Math.toRadians((60.0 * i - 30.0))
                val px = center.x + hexRadius * Math.cos(angleRad).toFloat()
                val py = center.y + hexRadius * Math.sin(angleRad).toFloat()
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
            hexPaths.add(p)
            x += colSpacing
        }
        y += rowSpacing
        row++
    }

    onDrawBehind {
        // 1. Base Layer: Deep Obsidian
        drawRect(baseColor)

        // 2. Ambient Royal Purple Glow Mesh
        drawRect(brush = amethystGlowBrush)
        drawRect(brush = indigoGlowBrush)

        // 3. Isometric Hex Lattice
        for (hp in hexPaths) {
            drawPath(hp, latticeColor, style = latticeStroke)
        }

        // 4. Flowing Topographic Contour Waves
        for (cp in contourPaths) {
            drawPath(cp, latticeColor, style = latticeStroke)
        }

        // 5. Glowing Intersection Nodes / Dots
        for (hc in hexCenters) {
            drawCircle(dotColor, dotRadius, hc)
        }
    }
}

/**
 * Full-screen container with procedural Royal Obsidian background textures.
 */
@Composable
fun RoyalObsidianBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .royalObsidianTexture(),
        content = content
    )
}

/** Legacy alias pointing directly to RoyalObsidianBackground. */
@Composable
fun NordicTexturedScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    RoyalObsidianBackground(
        modifier = modifier,
        content = content
    )
}

fun Modifier.nordicBackgroundTexture(): Modifier = this.royalObsidianTexture()

/**
 * Flagship Hero Card with high-contrast geometric & botanical crest watermark artwork
 * rendered across the right quadrant in textured amethyst and royal violet tones (0xFF4C3596 & 0xFF6D28D9 at alpha = 0.55f).
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
                val crestColorLayer1 = Color(0xFF4C3596).copy(alpha = 0.55f)
                val crestColorLayer2 = Color(0xFF6D28D9).copy(alpha = 0.55f)
                val stemColor = Color(0xFF8B5CF6).copy(alpha = 0.45f)

                val w = size.width
                val h = size.height

                // Stem curve extending from bottom right towards top center-right
                val stemPath = Path().apply {
                    moveTo(w * 0.98f, h * 0.98f)
                    cubicTo(w * 0.92f, h * 0.65f, w * 0.85f, h * 0.35f, w * 0.72f, h * 0.08f)
                }

                // High-contrast botanical frond / crest petals branching out
                val frondPathsLayer1 = listOf(
                    Path().apply {
                        moveTo(w * 0.95f, h * 0.85f)
                        cubicTo(w * 0.78f, h * 0.82f, w * 0.66f, h * 0.72f, w * 0.58f, h * 0.68f)
                        cubicTo(w * 0.70f, h * 0.76f, w * 0.86f, h * 0.82f, w * 0.94f, h * 0.86f)
                        close()
                    },
                    Path().apply {
                        moveTo(w * 0.90f, h * 0.65f)
                        cubicTo(w * 0.70f, h * 0.58f, w * 0.56f, h * 0.46f, w * 0.48f, h * 0.40f)
                        cubicTo(w * 0.62f, h * 0.50f, w * 0.78f, h * 0.60f, w * 0.89f, h * 0.66f)
                        close()
                    },
                    Path().apply {
                        moveTo(w * 0.84f, h * 0.45f)
                        cubicTo(w * 0.66f, h * 0.36f, w * 0.54f, h * 0.25f, w * 0.46f, h * 0.18f)
                        cubicTo(w * 0.60f, h * 0.28f, w * 0.74f, h * 0.38f, w * 0.83f, h * 0.46f)
                        close()
                    }
                )

                val frondPathsLayer2 = listOf(
                    Path().apply {
                        moveTo(w * 0.96f, h * 0.75f)
                        cubicTo(w * 0.80f, h * 0.70f, w * 0.70f, h * 0.60f, w * 0.64f, h * 0.55f)
                        cubicTo(w * 0.74f, h * 0.64f, w * 0.88f, h * 0.72f, w * 0.95f, h * 0.76f)
                        close()
                    },
                    Path().apply {
                        moveTo(w * 0.88f, h * 0.55f)
                        cubicTo(w * 0.72f, h * 0.46f, w * 0.60f, h * 0.35f, w * 0.52f, h * 0.28f)
                        cubicTo(w * 0.66f, h * 0.40f, w * 0.78f, h * 0.48f, w * 0.87f, h * 0.56f)
                        close()
                    },
                    Path().apply {
                        moveTo(w * 0.80f, h * 0.35f)
                        cubicTo(w * 0.64f, h * 0.24f, w * 0.55f, h * 0.14f, w * 0.48f, h * 0.06f)
                        cubicTo(w * 0.60f, h * 0.18f, w * 0.72f, h * 0.28f, w * 0.79f, h * 0.36f)
                        close()
                    }
                )

                val topHighlightY = 1.dp.toPx()
                val topHighlightStartX = 24.dp.toPx()
                val topHighlightEndX = w - 24.dp.toPx()

                onDrawBehind {
                    // Deep Charcoal Hero Surface
                    drawRect(NordicColors.CardSurfaceHero)

                    // Layer 1 Amethyst Crest Watermark
                    for (p in frondPathsLayer1) {
                        drawPath(p, crestColorLayer1)
                    }

                    // Layer 2 Royal Violet Crest Watermark
                    for (p in frondPathsLayer2) {
                        drawPath(p, crestColorLayer2)
                    }

                    // Stem stroke
                    drawPath(stemPath, stemColor, style = Stroke(width = 1.5.dp.toPx()))

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
                val tickColor = Color(0xFF36266E).copy(alpha = 0.55f)
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
