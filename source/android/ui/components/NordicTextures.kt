package com.blocker.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blocker.ui.theme.NordicColors

object BackgroundPatternCache {
    @Volatile
    private var cachedBitmap: androidx.compose.ui.graphics.ImageBitmap? = null

    fun get(context: android.content.Context): androidx.compose.ui.graphics.ImageBitmap? {
        val existing = cachedBitmap
        if (existing != null) return existing
        return synchronized(this) {
            val current = cachedBitmap
            if (current != null) return current
            runCatching {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                context.assets.open("bg-pattern.jpg").use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                }
            }.getOrNull()?.also {
                cachedBitmap = it
            }
        }
    }
}

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
    val bgBitmap = remember(context) { BackgroundPatternCache.get(context) }
    val scrimBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color(0x55070512),
                Color(0x22070512),
                Color(0x66070512)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val dstSize = IntSize(size.width.toInt(), size.height.toInt())
                onDrawBehind {
                    drawRect(Color(0xFF070512))
                    if (bgBitmap != null) {
                        drawImage(
                            image = bgBitmap,
                            dstSize = dstSize,
                            alpha = 0.16f
                        )
                    }
                    drawRect(scrimBrush)
                }
            }
    ) {
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
 * Tactile 3D Elevated Card:
 * Hardware-accelerated graphicsLayer shadow, multi-stop deep obsidian-purple surface gradient,
 * and 1.2dp 3D beveled rim border (light top edge catching light -> deep obsidian bottom).
 * Ticks/crosshairs are removed for a modern, tactile surface.
 */
@Composable
fun Elevated3DCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val beveledRim = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFFA78BFA).copy(alpha = 0.65f),
                Color(0xFF3B2B6E).copy(alpha = 0.35f),
                Color(0xFF05030A).copy(alpha = 0.90f)
            )
        )
    }
    val surfaceGradient = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF1D153B),
                Color(0xFF120D26),
                Color(0xFF0B0818)
            )
        )
    }

    Column(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color(0xFF7C3AED)
            )
            .clip(shape)
            .background(surfaceGradient)
            .border(1.2.dp, beveledRim, shape)
            .then(clickModifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Tactile 3D Hero Card:
 * Flagship executive card with hardware elevation, radial amethyst top highlight,
 * deep gradient surface, and 1.2dp 3D beveled rim border.
 */
@Composable
fun Hero3DCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val beveledRim = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFFC4B5FD).copy(alpha = 0.80f),
                Color(0xFF4C3596).copy(alpha = 0.40f),
                Color(0xFF05030A).copy(alpha = 0.95f)
            )
        )
    }
    val surfaceGradient = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF221747),
                Color(0xFF160F33),
                Color(0xFF0D081F)
            )
        )
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                this.shadowElevation = 20.dp.toPx()
                this.shape = shape
                this.clip = false
                this.spotShadowColor = Color(0xFF8B5CF6)
                this.ambientShadowColor = Color(0xFF7C3AED)
            }
            .clip(shape)
            .background(surfaceGradient)
            .drawWithCache {
                val highlightBrush = Brush.radialGradient(
                    colors = listOf(Color(0x388B5CF6), Color.Transparent),
                    center = Offset(size.width * 0.5f, -size.height * 0.2f),
                    radius = size.width * 0.9f
                )
                onDrawBehind {
                    drawRect(highlightBrush)
                }
            }
            .border(1.2.dp, beveledRim, shape)
            .then(clickModifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Sunken 3D Well:
 * Recessed container with dark inner well fill (#080512), subtle upper shadow,
 * and bottom bevel highlight. Perfect for charts, switchers, and input fields.
 */
@Composable
fun Recessed3DWell(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val wellBorder = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF030208),
                Color(0xFF2A1F4D).copy(alpha = 0.65f)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF080512))
            .border(1.dp, wellBorder, shape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Tactile 3D Button:
 * Raised convex purple button with vibrant top rim reflection and rich gradient.
 */
@Composable
fun Tactile3DButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tactile_scale"
    )

    val buttonGrad = remember {
        Brush.verticalGradient(
            listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
        )
    }
    val topHighlight = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFFA78BFA).copy(alpha = 0.85f),
                Color(0xFF4C1D95).copy(alpha = 0.35f)
            )
        )
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.shadowElevation = if (isPressed) 4.dp.toPx() else 8.dp.toPx()
                this.shape = shape
                this.spotShadowColor = Color(0xFF7C3AED)
            }
            .clip(shape)
            .background(buttonGrad)
            .border(1.2.dp, topHighlight, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Raised Squircle Icon Pedestal:
 * 42dp tactile squircle for displaying icons with a bevel finish.
 */
@Composable
fun RaisedSquirclePedestal(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val pedestalGrad = remember {
        Brush.verticalGradient(
            listOf(Color(0xFF281D52), Color(0xFF140E2B))
        )
    }
    val pedestalBorder = remember {
        Brush.verticalGradient(
            listOf(Color(0xFF7C3AED), Color(0xFF1E133E))
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                this.shadowElevation = 6.dp.toPx()
                this.shape = shape
                this.spotShadowColor = Color(0xFF6D28D9)
            }
            .clip(shape)
            .background(pedestalGrad)
            .border(1.dp, pedestalBorder, shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Soft gradient hairline divider inside multi-row cards.
 */
@Composable
fun SoftGradientHairlineDivider(
    modifier: Modifier = Modifier
) {
    val dividerBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color(0xFF3B2B6E).copy(alpha = 0.65f),
                Color.Transparent
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(dividerBrush)
    )
}

/**
 * Legacy compatibility alias: NordicMicroGridCard now maps to Elevated3DCard
 * with corner-bracket ticks completely removed.
 */
@Composable
fun NordicMicroGridCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Elevated3DCard(
        modifier = modifier,
        onClick = onClick,
        content = content
    )
}
