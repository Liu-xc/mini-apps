package com.leo.darkroom.card

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.WeakHashMap
import com.leo.darkroom.develop.DevelopMode
import com.leo.darkroom.develop.DevelopSpec
import com.leo.darkroom.develop.DevelopVisual
import com.leo.darkroom.develop.RevealField
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
        /** Neutral light paper and graphite print; fixed across interface themes. */
        val Default = CardPalette(
            paper = 0xFFFBFBFA.toInt(),
            ink = 0xFF191919.toInt(),
            inkFaint = 0xFF626262.toInt(),
            accent = 0xFF414141.toInt(),
            hairline = 0xFFDEDEDC.toInt(),
        )

        /** 胶片齿孔：物理穿孔恒为近黑，不随界面主题翻色 */
        const val FILM_HOLE = 0xFF0E0E0E.toInt()

        /**
         * 模式对应的卡面印字配色（it-007 M3）：拍立得是浅相纸，
         * 数码/胶片是深机身与片基，文字翻成浅色——预览与导出共用，杜绝错配。
         * it-010：拍立得的相纸框型由 [frame] 分派（[FrameStyle]），其余模式无相纸概念。
         */
        fun forMode(mode: DevelopMode, frame: FrameStyle = FrameStyle.CLASSIC): CardPalette =
            when (mode) {
                DevelopMode.POLAROID -> forFrame(frame)
                DevelopMode.DIGITAL -> CardPalette(
                    paper = 0xFF1A1A1A.toInt(),
                    ink = 0xFFF2F2F0.toInt(),
                    inkFaint = 0xFFB9B9B6.toInt(),
                    accent = 0xFFD0D0CE.toInt(),
                    hairline = 0xFF3A3A3A.toInt(),
                )
                DevelopMode.FILM -> CardPalette(
                    paper = 0xFF232323.toInt(),
                    ink = 0xFFF2F2F0.toInt(),
                    inkFaint = 0xFFB9B9B6.toInt(),
                    accent = 0xFFD0D0CE.toInt(),
                    hairline = 0xFF3A3A3A.toInt(),
                )
            }

        /** 四款相纸（it-010）：印字对比均 ≥ 打印可读线；成片是实物，不随界面主题翻色 */
        fun forFrame(frame: FrameStyle): CardPalette = when (frame) {
            FrameStyle.CLASSIC -> Default
            FrameStyle.CREAM -> CardPalette(
                paper = 0xFFF4EEE2.toInt(),
                ink = 0xFF3B3428.toInt(),
                inkFaint = 0xFF8A7F6C.toInt(),
                accent = 0xFF6E6350.toInt(),
                hairline = 0xFFE4DAC7.toInt(),
            )
            FrameStyle.NOIR -> CardPalette(
                paper = 0xFF141414.toInt(),
                ink = 0xFFF2F2F0.toInt(),
                inkFaint = 0xFFB9B9B6.toInt(),
                accent = 0xFFD0D0CE.toInt(),
                hairline = 0xFF3A3A3A.toInt(),
            )
            FrameStyle.SLATE -> CardPalette(
                // it-010 走查修正：纸面加深一档，与经典白在缩略上拉开明度差
                paper = 0xFFD4D4CE.toInt(),
                ink = 0xFF262624.toInt(),
                inkFaint = 0xFF66665F.toInt(),
                accent = 0xFF46463F.toInt(),
                hairline = 0xFFBEBEB8.toInt(),
            )
        }
    }
}

/**
 * 成片卡面的唯一 android.graphics 渲染器：位图导出与视频逐帧共用（it-001 AC4）。
 * 布局一律取自 [CardLayout.solve]（单一布局真源）；色彩一律取自 [DevelopSpec.colorMatrix]
 * （与 Compose 预览管线共享同一映射函数）。
 */
object PhotoCardPainter {

    private val backdropCache = WeakHashMap<Bitmap, Bitmap>()
    private val glowCache = WeakHashMap<Bitmap, Bitmap>()

    fun paint(
        canvas: Canvas,
        cardWidthPx: Float,
        photo: Bitmap?,
        spec: CardSpec,
        visual: DevelopVisual,
        mode: DevelopMode,
        grain: Bitmap?,
        look: PhotoLook = PhotoLook.ORIGINAL,
    ) {
        val layout = CardLayout.solve(cardWidthPx, mode = mode)
        val palette = CardPalette.forMode(mode, spec.frame)
        val paper = Paint().apply { color = palette.paper }
        canvas.drawRect(0f, 0f, cardWidthPx, layout.height, paper)
        // 相纸/片基不是纯色平涂：极轻的确定性纸纹压在纸面与印字之下（it-007 M2）
        if (grain != null) drawGrain(canvas, RectF(0f, 0f, cardWidthPx, layout.height), grain, 0.05f)

        val photoRect = RectF(layout.photo.left, layout.photo.top, layout.photo.right, layout.photo.bottom)
        // 照片衬底（未显影时透过低 alpha 隐约可见）
        canvas.drawRect(photoRect, Paint().apply { color = palette.hairline })
        if (photo != null && visual.imageAlpha > 0.01f) {
            drawDevelopPhoto(canvas, photo, photoRect, visual, grain, look, mode)
        }
        drawFilmSprockets(canvas, layout, palette)
        drawTexts(canvas, layout, spec, palette)
        // 成像区边界的极细分界（hairline）
        canvas.drawRect(
            photoRect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (cardWidthPx * 0.0015f).coerceAtLeast(1f)
                color = palette.hairline
            },
        )
        // 深色卡面（数码屏/片基）在深色页底上补一圈外框，否则整张卡会糊进背景
        if (mode != DevelopMode.POLAROID) {
            canvas.drawRect(
                0f,
                0f,
                cardWidthPx,
                layout.height,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (cardWidthPx * 0.0018f).coerceAtLeast(1f)
                    color = palette.hairline
                },
            )
        }
    }

    /** 胶片齿孔：14 孔等距穿孔（几何由 CardLayout.sprocketHoles 单一真源给出） */
    private fun drawFilmSprockets(canvas: Canvas, layout: CardLayout, palette: CardPalette) {
        val holes = CardLayout.sprocketHoles(layout)
        if (holes.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CardPalette.FILM_HOLE }
        val radius = layout.width * 0.008f
        for (hole in holes) {
            canvas.drawRoundRect(
                RectF(hole.left, hole.top, hole.right, hole.bottom),
                radius,
                radius,
                paint,
            )
        }
        // 齿孔带与片格/信息带之间的极细边界
        val rule = Paint().apply { color = palette.hairline }
        listOfNotNull(layout.sprocketTop, layout.sprocketBottom).forEach { band ->
            canvas.drawRect(0f, band.bottom - 1f, layout.width, band.bottom, rule)
        }
    }

    /** Social-ready frame; still and video exports share this exact composition. */
    fun paintShareFrame(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        photo: Bitmap,
        spec: CardSpec,
        visual: DevelopVisual,
        mode: DevelopMode,
        grain: Bitmap?,
        format: ShareFormat,
        look: PhotoLook = PhotoLook.ORIGINAL,
    ) {
        val layout = ShareLayout.solve(widthPx, heightPx, format, mode)
        // 深色卡面（数码屏/片基）要放在亮场上才分得开；相纸仍走原暗场
        val darkCard = mode != DevelopMode.POLAROID
        canvas.drawColor(if (darkCard) 0xFFF1F1EF.toInt() else 0xFF121212.toInt())

        // A muted, enlarged echo of the selected photo gives the card a physical setting.
        val backdrop = synchronized(backdropCache) {
            backdropCache[photo] ?: downscaleBlurred(photo, 0.015f).also { backdropCache[photo] = it }
        }
        val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = if (darkCard) 130 else 88
            if (look != PhotoLook.ORIGINAL) {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(look.colorMatrix()))
            }
        }
        drawCenterCrop(canvas, backdrop, RectF(0f, 0f, widthPx, heightPx), backdropPaint)
        if (darkCard) {
            canvas.drawRect(0f, 0f, widthPx, heightPx, Paint().apply { color = 0x55FBFBFA.toInt() })
        } else {
            canvas.drawRect(0f, 0f, widthPx, heightPx, Paint().apply { color = 0xA80D0D0D.toInt() })
            val vignetteRadius = hypot(widthPx, heightPx) * 0.72f
            canvas.drawRect(
                0f,
                0f,
                widthPx,
                heightPx,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        widthPx / 2f,
                        heightPx / 2f,
                        vignetteRadius,
                        intArrayOf(0x00000000, 0x66070707),
                        floatArrayOf(0.38f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }
        if (grain != null) drawGrain(canvas, RectF(0f, 0f, widthPx, heightPx), grain, 0.09f)

        val cardRect = RectF(layout.card.left, layout.card.top, layout.card.right, layout.card.bottom)
        canvas.drawRect(
            cardRect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (darkCard) 0x59000000 else 0x39000000
                maskFilter = BlurMaskFilter(layout.card.width * 0.025f, BlurMaskFilter.Blur.NORMAL)
            },
        )
        canvas.save()
        canvas.translate(layout.card.left, layout.card.top)
        paint(canvas, layout.card.width, photo, spec, visual, mode, grain, look)
        canvas.restore()

        if (spec.footer.isNotBlank() && format == ShareFormat.STORY) {
            val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (darkCard) 0xD9191919.toInt() else 0xD9FBFBFA.toInt()
                textSize = widthPx * 0.025f
                typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("DARKROOM", layout.wordmark.centerX, layout.wordmark.centerY, brandPaint)
            brandPaint.color = if (darkCard) 0xB3191919.toInt() else 0xBDFBFBFA.toInt()
            brandPaint.textSize = widthPx * 0.022f
            brandPaint.typeface = Typeface.create(Typeface.SERIF, 500, false)
            canvas.drawText("把回忆洗出来", layout.caption.centerX, layout.caption.centerY, brandPaint)
        }
    }

    fun renderShareFrame(
        photo: Bitmap,
        spec: CardSpec,
        mode: DevelopMode,
        grain: Bitmap?,
        format: ShareFormat,
        visual: DevelopVisual = DevelopSpec.visualAt(mode, 1f),
        look: PhotoLook = PhotoLook.ORIGINAL,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        paintShareFrame(
            Canvas(bitmap),
            format.width.toFloat(),
            format.height.toFloat(),
            photo,
            spec,
            visual,
            mode,
            grain,
            format,
            look,
        )
        return bitmap
    }

    /** 一张含完整卡面的成片位图（导出/分享用） */
    fun renderCard(
        photo: Bitmap,
        spec: CardSpec,
        mode: DevelopMode,
        grain: Bitmap?,
        visual: DevelopVisual = DevelopSpec.visualAt(mode, 1f),
        widthPx: Int = 1200,
        look: PhotoLook = PhotoLook.ORIGINAL,
    ): Bitmap {
        val height = CardLayout.solve(widthPx.toFloat(), mode = mode).height.roundToInt()
        val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        paint(Canvas(bmp), widthPx.toFloat(), photo, spec, visual, mode, grain, look)
        return bmp
    }

    /** Compact style-strip preview; it uses the same photo treatment as the full-size render. */
    fun renderLookThumbnail(photo: Bitmap, grain: Bitmap?, look: PhotoLook, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        drawDevelopPhoto(
            Canvas(bitmap), photo, rect, DevelopSpec.visualAt(1f), grain, look, DevelopMode.POLAROID,
        )
        return bitmap
    }

    /**
     * 显影中的照片：中心裁切 → 色彩矩阵 → 显现遮罩 → 暗角 → 颗粒。
     * 模糊用「缩小再放大」近似（ADR-004）。
     */
    private fun drawDevelopPhoto(
        canvas: Canvas,
        photo: Bitmap,
        rect: RectF,
        visual: DevelopVisual,
        grain: Bitmap?,
        look: PhotoLook,
        mode: DevelopMode,
    ) {
        val saveCount = canvas.saveLayer(rect, null)
        canvas.clipRect(rect)

        val src = downscaleBlurred(photo, visual.blurFraction)
        val visualMatrix = DevelopSpec.colorMatrix(visual)
        val combinedMatrix = if (look == PhotoLook.ORIGINAL) {
            visualMatrix
        } else {
            DevelopSpec.concat(look.colorMatrix(), visualMatrix)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (visual.imageAlpha * 255f).roundToInt().coerceIn(0, 255)
            colorFilter = ColorMatrixColorFilter(ColorMatrix(combinedMatrix))
        }
        drawCenterCrop(canvas, src, rect, paint)

        if (look.warmHighlights) {
            drawLuminanceTint(
                canvas, src, rect,
                red = 255f, green = 194f, blue = 150f,
                alphaRed = 0.22f, alphaGreen = 0.22f, alphaBlue = 0.22f, alphaOffset = -32f,
            )
        }
        if (look.cinematicSplitTone) {
            drawLuminanceTint(
                canvas, src, rect,
                red = 17f, green = 43f, blue = 67f,
                alphaRed = -0.28f * 0.213f, alphaGreen = -0.28f * 0.715f,
                alphaBlue = -0.28f * 0.072f, alphaOffset = 71.4f,
            )
        }

        // Keep the highlight bloom inside the same reveal front as the photo itself.
        if (look.glowStrength > 0f) drawSoftGlow(canvas, rect, photo, look.glowStrength)

        if (visual.reveal < 0.999f) {
            val mask = ChemicalMaskBitmap.forPhoto(mode.reveal, photo, visual.reveal)
            canvas.drawBitmap(
                mask,
                null,
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
        drawVignette(canvas, rect, visual, look.vignetteScale)
        if (grain != null && visual.grain > 0.02f) {
            drawGrain(canvas, rect, grain, visual.grain * look.grainScale)
        }
        canvas.restoreToCount(saveCount)
    }

    /** Overlay a restrained tint whose alpha follows source luminance. */
    private fun drawLuminanceTint(
        canvas: Canvas,
        source: Bitmap,
        rect: RectF,
        red: Float,
        green: Float,
        blue: Float,
        alphaRed: Float,
        alphaGreen: Float,
        alphaBlue: Float,
        alphaOffset: Float,
    ) {
        val matrix = ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, red,
                0f, 0f, 0f, 0f, green,
                0f, 0f, 0f, 0f, blue,
                alphaRed, alphaGreen, alphaBlue, 0f, alphaOffset,
            ),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        drawCenterCrop(canvas, source, rect, paint)
    }

    /**
     * 模糊：缩到小尺寸再拉伸回原大（双线性滤波，ADR-004）。
     *
     * it-007 M2：映射改为连续。原实现在 blurFraction ≤0.002 时原样绘制、
     * 略高于阈值直接跳到约 0.11 的缩放比——定影末尾会硬跳一下才变清晰；
     * 同时它无法表达「末态保留极轻柔焦」。现在 0 → 1.0（无模糊）线性到
     * 0.05 → 0.05（最糊），曲线末态 0.006 落在 0.89 附近，收尾是柔的。
     */
    private fun downscaleBlurred(photo: Bitmap, blurFraction: Float): Bitmap {
        if (blurFraction <= 0.0005f) return photo
        val factor = (1f - blurFraction * 19f).coerceIn(0.05f, 1f)
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

    private fun drawVignette(canvas: Canvas, rect: RectF, visual: DevelopVisual, lookScale: Float) {
        if (visual.vignette * lookScale <= 0.02f) return
        val radius = hypot(rect.width(), rect.height()) / 2f * 1.08f
        val strength = (0.55f * visual.vignette * lookScale * 255f).roundToInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                rect.centerX(), rect.centerY(), radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(strength, 8, 8, 8)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, paint)
    }

    /** A cached, thresholded highlight bloom keeps the soft-glow look stable across video frames. */
    private fun drawSoftGlow(canvas: Canvas, rect: RectF, photo: Bitmap, strength: Float) {
        val glow = synchronized(glowCache) {
            glowCache[photo] ?: createSoftGlow(photo).also { glowCache[photo] = it }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (strength * 255f).roundToInt().coerceIn(0, 255)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        drawCenterCrop(canvas, glow, rect, paint)
    }

    /** Threshold bright areas, then down/up-sample once to create a soft, deterministic halo. */
    private fun createSoftGlow(photo: Bitmap): Bitmap {
        val width = (photo.width * 0.24f).roundToInt().coerceAtLeast(8)
        val height = (photo.height * 0.24f).roundToInt().coerceAtLeast(8)
        val extracted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val threshold = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 0f, 255f,
                        0.213f, 0.715f, 0.072f, 0f, -150f,
                    ),
                ),
            )
        }
        Canvas(extracted).drawBitmap(photo, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), threshold)
        val blurWidth = (width / 3).coerceAtLeast(8)
        val blurHeight = (height / 3).coerceAtLeast(8)
        val blurredSmall = Bitmap.createScaledBitmap(extracted, blurWidth, blurHeight, true)
        val blurred = Bitmap.createScaledBitmap(blurredSmall, width, height, true)
        if (blurredSmall !== extracted && blurredSmall !== blurred) blurredSmall.recycle()
        if (extracted !== blurred) extracted.recycle()
        return blurred
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
        // Upright editorial title, centered vertically inside its two-line print area.
        val title = spec.title.trim()
        if (title.isNotEmpty()) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.ink
                // it-010：字体与字号可选（衬线/黑体 × 小/标准/大），字号限幅防溢出
                textSize = layout.titleSize * spec.titleSize.scale
                typeface = Typeface.create(
                    if (spec.titleFont == TitleFontStyle.SANS) Typeface.SANS_SERIF else Typeface.SERIF,
                    500,
                    false,
                )
            }
            val titleLayout = StaticLayout.Builder.obtain(
                title,
                0,
                title.length,
                tp,
                layout.title.width.toInt().coerceAtLeast(1),
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.1f)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(
                layout.title.left,
                layout.title.top + ((layout.title.height - titleLayout.height).coerceAtLeast(0f) / 2f),
            )
            titleLayout.draw(canvas)
            canvas.restore()
        }

        // Graphite date print: medium sans with tabular figures, right-aligned in its reserved domain.
        val date = spec.dateText.trim()
        if (date.isNotEmpty()) {
            val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent
                textSize = layout.stampSize
                typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
                fontFeatureSettings = "tnum"
            }
            var w = sp.measureText(date)
            if (w > layout.stamp.width && w > 0f) {
                sp.textSize = layout.stampSize * (layout.stamp.width / w)
                w = sp.measureText(date)
            }
            val baseline = layout.stamp.top + layout.stamp.height * 0.82f
            canvas.drawText(date, layout.stamp.right - w, baseline, sp)
        }

        // 卡脚脚注（it-010：自定义文案，空 = 不印）
        val footer = spec.footer.trim()
        if (footer.isNotEmpty()) {
            val wp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.inkFaint
                textSize = layout.watermarkSize
                typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
            }
            var w = wp.measureText(footer)
            if (w > layout.watermark.width && w > 0f) {
                wp.textSize = layout.watermarkSize * (layout.watermark.width / w)
                w = wp.measureText(footer)
            }
            val baseline = layout.watermark.centerY + layout.watermarkSize * 0.35f
            canvas.drawText(footer, layout.watermark.right - w, baseline, wp)
        }
    }
}
