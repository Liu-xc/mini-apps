package com.leo.darkroom.card

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.leo.darkroom.develop.DevelopSpec
import com.leo.darkroom.develop.DevelopVisual
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 卡面配色（Int 色值，Compose 主题与 android.graphics 渲染器的桥） */
data class CardPalette(
    val paper: Int,
    val ink: Int,
    val inkFaint: Int,
    val accent: Int,
    val hairline: Int,
) {
    companion object {
        /** 相纸恒定色（昼夜不翻色，见 05-design-system 个性声明） */
        val Default = CardPalette(
            paper = 0xFFFDFCF6.toInt(),
            ink = 0xFF27231A.toInt(),
            inkFaint = 0xFF8A8375.toInt(),
            accent = 0xFFC05A1A.toInt(),
            hairline = 0xFFE8E3D5.toInt(),
        )
    }
}

/**
 * 成片卡面的唯一 android.graphics 渲染器：位图导出与视频逐帧共用（it-001 AC4）。
 * 布局一律取自 [CardLayout.solve]（单一布局真源）；色彩一律取自 [DevelopSpec.colorMatrix]
 * （与 Compose 预览管线共享同一映射函数）。
 */
object PhotoCardPainter {

    /** 潜影遮罩色：未显影区域的灰绿相纸底 */
    private const val MASK_COLOR = 0xFF262B22.toInt()

    /** 视频底（暗房桌面）：相纸白卡在其上有实体感 */
    const val VIDEO_BG = 0xFF201F19.toInt()

    fun paint(
        canvas: Canvas,
        cardWidthPx: Float,
        photo: Bitmap?,
        spec: CardSpec,
        visual: DevelopVisual,
        palette: CardPalette,
        grain: Bitmap?,
    ) {
        val layout = CardLayout.solve(cardWidthPx)
        val paper = Paint().apply { color = palette.paper }
        canvas.drawRect(0f, 0f, cardWidthPx, layout.height, paper)

        val photoRect = RectF(layout.photo.left, layout.photo.top, layout.photo.right, layout.photo.bottom)
        // 相片衬底（潜影期透过低 alpha 隐约可见的灰绿底）
        canvas.drawRect(photoRect, Paint().apply { color = palette.hairline })
        if (photo != null && visual.imageAlpha > 0.01f) {
            drawDevelopPhoto(canvas, photo, photoRect, visual, grain)
        }
        drawTexts(canvas, layout, spec, palette)
    }

    /** 一张含完整卡面的成片位图（导出/分享用） */
    fun renderCard(
        photo: Bitmap,
        spec: CardSpec,
        palette: CardPalette,
        grain: Bitmap?,
        visual: DevelopVisual = DevelopSpec.visualAt(1f),
        widthPx: Int = 1200,
    ): Bitmap {
        val height = (CardLayout.solve(widthPx.toFloat()).height).roundToInt()
        val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        paint(Canvas(bmp), widthPx.toFloat(), photo, spec, visual, palette, grain)
        return bmp
    }

    /**
     * 显影中的照片：中心裁切 → 色彩矩阵 → 中心向外晕开遮罩 → 暗角 → 颗粒。
     * 模糊用「缩小再放大」近似（化学扩散感、全 Canvas 一致，ADR-004）。
     */
    private fun drawDevelopPhoto(
        canvas: Canvas,
        photo: Bitmap,
        rect: RectF,
        visual: DevelopVisual,
        grain: Bitmap?,
    ) {
        val saveCount = canvas.saveLayer(rect, null)
        canvas.clipRect(rect)

        val src = downscaleBlurred(photo, visual.blurFraction)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (visual.imageAlpha * 255f).roundToInt().coerceIn(0, 255)
            colorFilter = ColorMatrixColorFilter(ColorMatrix(DevelopSpec.colorMatrix(visual)))
        }
        drawCenterCrop(canvas, src, rect, paint)

        drawReveal(canvas, rect, visual)
        drawVignette(canvas, rect, visual)
        if (grain != null && visual.grain > 0.02f) {
            drawGrain(canvas, rect, grain, visual.grain)
        }
        canvas.restoreToCount(saveCount)
    }

    /**
     * 模糊：缩到约 1/8~1/30 再拉伸回原大（双线性滤波）。
     * blurFraction 0.045（最糊）→ factor 0.035；≤0.002（定影）→ 直接原样绘制。
     */
    private fun downscaleBlurred(photo: Bitmap, blurFraction: Float): Bitmap {
        if (blurFraction <= 0.002f) return photo
        val factor = (0.035f + (0.045f - blurFraction).coerceAtLeast(0f) / 0.045f * 0.085f).coerceIn(0.03f, 0.95f)
        val w = (photo.width * factor).roundToInt().coerceAtLeast(8)
        val h = (photo.height * factor).roundToInt().coerceAtLeast(8)
        return Bitmap.createScaledBitmap(photo, w, h, true)
    }

    private fun drawCenterCrop(canvas: Canvas, src: Bitmap, rect: RectF, paint: Paint) {
        val scale = maxOf(rect.width() / src.width, rect.height() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val left = rect.centerX() - dw / 2f
        val top = rect.centerY() - dh / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), paint)
    }

    /** 中心向外晕开：潜影遮罩上挖一个柔边圆孔（中心透明→边缘收到潜影遮罩色，radius 外 clamp 遮罩） */
    private fun drawReveal(canvas: Canvas, rect: RectF, visual: DevelopVisual) {
        val reveal = visual.reveal.coerceIn(0.03f, 1f)
        if (reveal >= 0.999f) return
        val radius = hypot(rect.width(), rect.height()) / 2f * reveal
        val edgeStart = (reveal * 0.70f).coerceIn(0f, 0.92f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                rect.centerX(), rect.centerY(), radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, MASK_COLOR),
                floatArrayOf(0f, edgeStart, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, paint)
    }

    private fun drawVignette(canvas: Canvas, rect: RectF, visual: DevelopVisual) {
        if (visual.vignette <= 0.02f) return
        val radius = hypot(rect.width(), rect.height()) / 2f * 1.08f
        val strength = (0.55f * visual.vignette * 255f).roundToInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                rect.centerX(), rect.centerY(), radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(strength, 8, 10, 8)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, paint)
    }

    private fun drawGrain(canvas: Canvas, rect: RectF, grain: Bitmap, strength: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (strength * 150f).roundToInt().coerceIn(0, 200)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        canvas.drawRect(rect, paint)
    }

    private fun drawTexts(canvas: Canvas, layout: CardLayout, spec: CardSpec, palette: CardPalette) {
        // 手写标题：衬线斜体，左对齐，超长省略
        val title = spec.title.trim()
        if (title.isNotEmpty()) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.ink
                textSize = layout.titleSize
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            val line = TextUtils.ellipsize(title, tp, layout.title.width, TextUtils.TruncateAt.END)
            val baseline = layout.title.top + layout.title.height * 0.80f
            canvas.drawText(line.toString(), layout.title.left, baseline, tp)
        }

        // 日期章：等宽粗体橙色，右对齐；超域宽先缩字号（it-002 O3 两域互斥）
        val date = spec.dateText.trim()
        if (date.isNotEmpty()) {
            val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent
                textSize = layout.stampSize
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                letterSpacing = 0.06f
            }
            var w = sp.measureText(date)
            if (w > layout.stamp.width && w > 0f) {
                sp.textSize = layout.stampSize * (layout.stamp.width / w)
                w = sp.measureText(date)
            }
            val baseline = layout.stamp.top + layout.stamp.height * 0.82f
            canvas.drawText(date, layout.stamp.right - w, baseline, sp)
        }

        // 卡脚水印
        if (spec.showWatermark) {
            val wp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.inkFaint
                textSize = layout.watermarkSize
                letterSpacing = 0.22f
            }
            val text = "显影 DARKROOM"
            val w = wp.measureText(text)
            val baseline = layout.watermark.centerY + layout.watermarkSize * 0.35f
            canvas.drawText(text, layout.watermark.right - w, baseline, wp)
        }
    }
}
