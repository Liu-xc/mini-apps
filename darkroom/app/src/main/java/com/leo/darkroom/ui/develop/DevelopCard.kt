package com.leo.darkroom.ui.develop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.darkroom.card.CardLayout
import com.leo.darkroom.card.CardSpec
import com.leo.darkroom.develop.DevelopSpec
import com.leo.darkroom.develop.DevelopVisual
import com.leo.darkroom.ui.theme.editorialColors
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
    progress: Float,
    cardWidthPx: Float,
    modifier: Modifier = Modifier,
    grain: Bitmap? = null,
) {
    val visual = DevelopSpec.visualAt(progress)
    val layout = remember(cardWidthPx) { CardLayout.solve(cardWidthPx) }
    val colors = editorialColors()
    val textMeasurer = rememberTextMeasurer()

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
        // —— 底层：相纸 + 页脚文字 ——
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = colors.cardPaper)
            drawTexts(layout, spec, textMeasurer)
        }

        // —— 照片区（离屏层，颗粒 Overlay 只对照片内容生效）——
        if (photo != null && visual.imageAlpha > 0.01f) {
            Box(
                Modifier
                    .offset { IntOffset(layout.photo.left.toInt(), layout.photo.top.toInt()) }
                    .size(Dp(layout.photo.width / density), Dp(layout.photo.height / density))
                    .clip(androidx.compose.ui.graphics.RectangleShape)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                PhotoImage(photo, visual, layout.photo.width, Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    drawReveal(visual)
                    drawVignette(visual)
                    grainBrush?.let { drawGrain(it, visual) }
                }
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

/** 中心向外晕开：潜影遮罩上挖柔边圆孔（局部坐标 = 照片区坐标系）
 *  渐变自中心：0..edgeStart 全透明（照片显出）→ 边缘收到潜影遮罩色；radius 外 clamp 遮罩 */
private fun DrawScope.drawReveal(visual: DevelopVisual) {
    if (visual.reveal >= 0.999f) return
    val reveal = visual.reveal.coerceIn(0.03f, 1f)
    val radius = (kotlin.math.hypot(size.width, size.height) / 2f) * reveal
    val edgeStart = (reveal * 0.70f).coerceIn(0f, 0.92f)
    val brush = ShaderBrush(
        android.graphics.RadialGradient(
            size.width / 2f, size.height / 2f, radius,
            intArrayOf(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                0xFF262B22.toInt(),
            ),
            floatArrayOf(0f, edgeStart, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        ),
    )
    drawRect(brush)
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
                android.graphics.Color.argb(strength, 8, 10, 8),
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

private fun DrawScope.drawTexts(
    layout: CardLayout,
    spec: CardSpec,
    textMeasurer: TextMeasurer,
) {
    // 手写标题：衬线斜体，超长省略（与 native ellipsize 对表）
    val title = spec.title.trim()
    if (title.isNotEmpty()) {
        val style = TextStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = layout.titleSize.toSp(),
            color = Color(0xFF27231A),
        )
        val result = textMeasurer.measure(
            text = title,
            style = style,
            constraints = Constraints(maxWidth = layout.title.width.toInt()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val baselineTarget = layout.title.top + layout.title.height * 0.80f
        drawText(result, topLeft = Offset(layout.title.left, baselineTarget - result.firstBaseline))
    }

    // 日期章：等宽粗体橙色，右对齐；超域宽先缩字号（it-002 O3 两域互斥）
    val date = spec.dateText.trim()
    if (date.isNotEmpty()) {
        var style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = layout.stampSize.toSp(),
            letterSpacing = 0.06.sp,
            color = Color(0xFFC05A1A),
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
            fontSize = layout.watermarkSize.toSp(),
            letterSpacing = 0.22.sp,
            color = Color(0xFF8A8375),
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
