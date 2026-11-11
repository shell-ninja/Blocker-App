package com.blocker.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blocker.ui.theme.NordicColors

/**
 * Full-screen Deep Obsidian background backed by assets/bg-pattern.jpg at low opacity
 * plus a subtle dark purple vignette scrim so foreground cards pop with depth.
 */
@Composable
fun RoyalObsidianBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val bgBitmap = remember(context) {
        runCatching {
            context.assets.open("bg-pattern.jpg").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070512))
    ) {
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.16f
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x55070512),
                            Color(0x22070512),
                            Color(0x66070512)
                        )
                    )
                )
        )
        content()
    }
}

/** Transparent pass-through scaffold container (MainActivity renders RoyalObsidianBackground). */
@Composable
fun NordicTexturedScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        content = content
    )
}

fun Modifier.nordicBackgroundTexture(): Modifier = this.background(Color(0xFF070512))

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
