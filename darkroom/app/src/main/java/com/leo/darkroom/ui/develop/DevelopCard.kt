package com.leo.darkroom.ui.develop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.card.CardLayout
import com.leo.darkroom.card.CardPalette
import com.leo.darkroom.card.ChemicalMaskBitmap
import com.leo.darkroom.card.CardSpec
import com.leo.darkroom.develop.DevelopFx
import com.leo.darkroom.develop.DevelopMode
import com.leo.darkroom.develop.DevelopSpec
import com.leo.darkroom.develop.DevelopVisual
import com.leo.darkroom.develop.RevealField
import kotlin.math.roundToInt

/**
 * 显影台/成片页的 Compose 卡片渲染器（it-001 AC4 三处渲染一致的预览端）：
 * 布局取自 [CardLayout.solve]，色彩矩阵取自 [DevelopSpec.colorMatrix]——
 * 与 android.graphics 的 [com.leo.darkroom.card.PhotoCardPainter]（导出/视频端）共享同一套真源。
 *
 * 分层结构（对应 PhotoCardPainter 绘制顺序）：
 * 相纸底+文字（底层 Canvas）→ 照片区（离屏层：模糊+调色 Image / 晕开 / 暗角 / 颗粒）。
 */
@Composable
fun DevelopCard(
    photo: Bitmap?,
    spec: CardSpec,
    mode: DevelopMode,
    progress: Float,
    cardWidthPx: Float,
    modifier: Modifier = Modifier,
    grain: Bitmap? = null,
) {
    val visual = DevelopSpec.visualAt(mode, progress)
    val layout = remember(cardWidthPx, mode) { CardLayout.solve(cardWidthPx, mode = mode) }
    // 卡面印字配色与导出端共用 CardPalette（it-007 AC3：预览与成片不许错配）
    val palette = remember(mode) { CardPalette.forMode(mode) }
    val textMeasurer = rememberTextMeasurer()
    val revealMask = remember(photo, mode, RevealField.bucket(mode.reveal, visual.reveal)) {
        photo?.let { ChemicalMaskBitmap.forPhoto(mode.reveal, it, visual.reveal) }
    }
    val revealMaskImage = remember(revealMask) { revealMask?.asImageBitmap() }

    // px→dp 显式换算（布局 scope 不提供 Density receiver）
    val density = LocalDensity.current.density
    val cardWidthDp = Dp(cardWidthPx / density)
    val cardHeightDp = Dp(layout.height / density)
    val grainBrush = remember(grain) {
        grain?.let {
            ShaderBrush(
                android.graphics.BitmapShader(
                    it, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT,
                ),
            )
        }
    }

    Box(modifier.size(cardWidthDp, cardHeightDp)) {
        // —— 底层：卡面 + 页脚文字 ——
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = Color(palette.paper))
            // 极轻纸纹：压在纸面与印字之下（it-007 M2，与 PhotoCardPainter 同参数）
            grainBrush?.let {
                drawRect(brush = it, alpha = 0.05f, blendMode = BlendMode.Overlay)
            }
            drawTexts(layout, spec, textMeasurer, palette)
        }

        // —— 照片区（离屏层，颗粒 Overlay 只对照片内容生效）——
        if (photo != null && visual.imageAlpha > 0.01f) {
            Box(
                Modifier
                    .offset { IntOffset(layout.photo.left.toInt(), layout.photo.top.toInt()) }
                    .size(Dp(layout.photo.width / density), Dp(layout.photo.height / density))
                    .background(Color(palette.hairline))
                    .clip(androidx.compose.ui.graphics.RectangleShape)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                PhotoImage(photo, visual, layout.photo.width, Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    revealMaskImage?.let { mask ->
                        drawImage(
                            image = mask,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                    drawVignette(visual)
                    grainBrush?.let { drawGrain(it, visual) }
                    // it-008 过程动效：药液气泡 + 前沿湿光（纯 progress 驱动，85% 后自动归零）
                    if (DevelopFx.bubblesFor(mode)) {
                        drawWetBand(mode, progress, visual.reveal)
                        drawBubbles(progress)
                    }
                }
            }
        }

        // 胶片齿孔（几何真源在 CardLayout.sprocketHoles）
        val holes = remember(layout) { CardLayout.sprocketHoles(layout) }
        if (holes.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = cardWidthPx * 0.008f
                for (hole in holes) {
                    drawRoundRect(
                        color = Color(CardPalette.FILM_HOLE),
                        topLeft = Offset(hole.left, hole.top),
                        size = Size(hole.width, hole.height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
        }

        // 成像区边界的极细分界（画在照片之上，与导出端同参数）
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                color = Color(palette.hairline),
                topLeft = Offset(layout.photo.left, layout.photo.top),
                size = Size(layout.photo.width, layout.photo.height),
                style = Stroke(width = (cardWidthPx * 0.0015f).coerceAtLeast(1f)),
            )
            // 深色卡面在深色页底上补一圈外框，否则整张卡会糊进背景
            if (mode != DevelopMode.POLAROID) {
                drawRect(
                    color = Color(palette.hairline),
                    topLeft = Offset.Zero,
                    size = Size(cardWidthPx, layout.height),
                    style = Stroke(width = (cardWidthPx * 0.0018f).coerceAtLeast(1f)),
                )
            }
        }
    }
}

@Composable
private fun PhotoImage(photo: Bitmap, visual: DevelopVisual, photoShortSidePx: Float, modifier: Modifier) {
    val imageBitmap = remember(photo) { photo.asImageBitmap() }
    // 模糊半径 = 相对短边比例 × 照片区短边像素（与 PhotoCardPainter.blurPx 同式）
    val blurRadiusPx = visual.blurFraction * photoShortSidePx
    val effect = remember(visual, photoShortSidePx) {
        // android.graphics.RenderEffect 无 createColorMatrixEffect（javap 实证），色彩走 ColorFilter 变体
        val colorFx = android.graphics.RenderEffect.createColorFilterEffect(
            android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix(DevelopSpec.colorMatrix(visual)),
            ),
        )
        if (blurRadiusPx > 0.5f) {
            android.graphics.RenderEffect.createChainEffect(
                colorFx,
                android.graphics.RenderEffect.createBlurEffect(
                    blurRadiusPx, blurRadiusPx, android.graphics.Shader.TileMode.CLAMP,
                ),
            ).asComposeRenderEffect()
        } else {
            colorFx.asComposeRenderEffect()
        }
    }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.graphicsLayer {
            alpha = visual.imageAlpha
            renderEffect = effect
        },
    )
}

private fun DrawScope.drawVignette(visual: DevelopVisual) {
    if (visual.vignette <= 0.02f) return
    val radius = (kotlin.math.hypot(size.width, size.height) / 2f) * 1.08f
    val strength = (0.55f * visual.vignette * 255f).toInt().coerceIn(0, 255)
    val brush = ShaderBrush(
        android.graphics.RadialGradient(
            size.width / 2f, size.height / 2f, radius,
            intArrayOf(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.argb(strength, 8, 8, 8),
            ),
            floatArrayOf(0f, 0.45f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        ),
    )
    drawRect(brush)
}

private fun DrawScope.drawGrain(brush: ShaderBrush, visual: DevelopVisual) {
    if (visual.grain <= 0.02f) return
    drawRect(
        brush = brush,
        alpha = (visual.grain * 150f).coerceIn(0f, 200f) / 255f,
        blendMode = BlendMode.Overlay,
    )
}

/** 前沿湿光：随显现前沿推进的软亮带，像药水正在浸湿乳剂（it-008 M1.2） */
private fun DrawScope.drawWetBand(mode: DevelopMode, progress: Float, reveal: Float) {
    val band = DevelopFx.wetBandAt(mode, progress, reveal)
    if (band.alpha <= 0.004f) return
    val bandW = size.width * 0.22f
    val x = band.position * size.width
    val brush = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, Color.White.copy(alpha = band.alpha), Color.Transparent),
        startX = x - bandW,
        endX = x + bandW,
    )
    withTransform({ rotate(band.angleDeg, pivot = center) }) {
        val s = size.width + size.height
        drawRect(brush, topLeft = Offset(-s, -s), size = Size(s * 2f, s * 2f))
    }
}

/** 药液气泡：暗晕打底 + 白芯——白芯只在暗部可辨、暗晕在亮部可辨（it-008 终验实测） */
private fun DrawScope.drawBubbles(progress: Float) {
    for (i in 0 until DevelopFx.BUBBLE_COUNT) {
        val b = DevelopFx.bubbleAt(i, progress)
        if (b.alpha <= 0.004f) continue
        val radius = b.radius * size.width
        val center = Offset(b.x * size.width, b.y * size.height)
        drawCircle(color = Color.Black.copy(alpha = b.alpha * 0.26f), radius = radius * 1.6f, center = center)
        drawCircle(color = Color.White.copy(alpha = b.alpha), radius = radius, center = center)
    }
}

private fun DrawScope.drawTexts(
    layout: CardLayout,
    spec: CardSpec,
    textMeasurer: TextMeasurer,
    palette: CardPalette,
) {
    // Editorial work title: upright serif, at most two lines with a calm ellipsis.
    val title = spec.title.trim()
    if (title.isNotEmpty()) {
        val style = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = layout.titleSize.toSp(),
            lineHeight = (layout.titleSize * 1.1f).toSp(),
            color = Color(palette.ink),
        )
        val result = textMeasurer.measure(
            text = title,
            style = style,
            constraints = Constraints(
                maxWidth = layout.title.width.toInt(),
                maxHeight = layout.title.height.toInt(),
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val top = layout.title.top + ((layout.title.height - result.size.height).coerceAtLeast(0f) / 2f)
        drawText(result, topLeft = Offset(layout.title.left, top))
    }

    // Neutral date print: medium sans with tabular numerals; shrink before entering the title domain.
    val date = spec.dateText.trim()
    if (date.isNotEmpty()) {
        var style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
            fontSize = layout.stampSize.toSp(),
            color = Color(palette.accent),
        )
        var result = textMeasurer.measure(date, style = style, maxLines = 1)
        if (result.size.width > layout.stamp.width && result.size.width > 0) {
            style = style.copy(
                fontSize = (layout.stampSize * (layout.stamp.width / result.size.width)).toSp(),
            )
            result = textMeasurer.measure(date, style = style, maxLines = 1)
        }
        val baselineTarget = layout.stamp.top + layout.stamp.height * 0.82f
        drawText(
            result,
            topLeft = Offset(layout.stamp.right - result.size.width, baselineTarget - result.firstBaseline),
        )
    }

    // 卡脚水印
    if (spec.showWatermark) {
        val style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = layout.watermarkSize.toSp(),
            color = Color(palette.inkFaint),
        )
        val result = textMeasurer.measure("显影 DARKROOM", style = style, maxLines = 1)
        drawText(
            result,
            topLeft = Offset(
                layout.watermark.right - result.size.width,
                layout.watermark.centerY + layout.watermarkSize * 0.35f - result.firstBaseline,
            ),
        )
    }
}
