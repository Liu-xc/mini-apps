package com.leo.wardrobe.ui.recap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.leo.wardrobe.domain.usecase.WardrobeRecapStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/**
 * 「年度衣橱」长图（it-018 阶段B，ADR-018）：android.graphics 直绘（与 eats 年度食光/
 * wardrobe OutfitImageComposer 同管线），版式 = 封面(角色) → 三大数字 → 最百搭 TOP3 →
 * 品类分布 → 成品图墙(≤9) → 结尾。按当前角色出图；无成品图时图墙整段跳过
 * （闲置等负面清单只进 W9 回顾页，长图保持「晒」的属性）。
 */
class WardrobeRecapLongImage(
    private val stats: WardrobeRecapStats,
    private val rangeLabel: String,
    private val generatedAt: String,
    private val photoFileOf: (String) -> File?,
) {

    /** 渲染并写入 export 目录（JPEG），返回文件 */
    suspend fun renderTo(dir: File): File? = withContext(Dispatchers.IO) {
        val bitmap = render() ?: return@withContext null
        runCatching {
            dir.mkdirs()
            val out = File(dir, "wardrobe_recap_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            out
        }.getOrNull().also { bitmap.recycle() }
    }

    private suspend fun render(): Bitmap = withContext(Dispatchers.IO) {
        val photos = stats.photoFiles.mapNotNull { name -> decodeSmall(photoFileOf(name)) }
        val showTop = stats.topVersatile.isNotEmpty()
        val showWall = photos.isNotEmpty()

        var h = PAD + COVER_H + NUMS_H
        if (showTop) h += TITLE_H + stats.topVersatile.size * TOP_ROW_H
        h += CATEGORY_H
        if (showWall) h += TITLE_H + rowsOf(photos.size) * (CELL + CELL_GAP)
        h += FOOTER_H + PAD

        val bitmap = Bitmap.createBitmap(WIDTH, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(BG) }
        var y = PAD

        // 封面
        val cy = y + COVER_H / 2f
        center(canvas, stats.personEmoji, WIDTH / 2f, cy - 150f, emojiPaint)
        center(canvas, "${stats.personName}的年度衣橱", WIDTH / 2f, cy + 6f, titlePaint)
        center(canvas, "wardrobe · $rangeLabel", WIDTH / 2f, cy + 116f, faintPaint)
        y += COVER_H

        // 三大数字
        val nums = listOf(
            "${stats.itemCount}" to "单品",
            "${stats.outfitCount}" to "穿搭套",
            "${stats.wearCount}" to "打卡",
        )
        val ncy = y + NUMS_H / 2f
        nums.forEachIndexed { i, (value, label) ->
            val cx = WIDTH * (i + 0.5f) / 3f
            center(canvas, value, cx, ncy - 46f, numPaint)
            center(canvas, label, cx, ncy + 66f, faintPaint)
            if (i < 2) {
                val x = WIDTH * (i + 1) / 3f
                canvas.drawLine(x, y + 60f, x, y + NUMS_H - 60f, hairPaint)
            }
        }
        y += NUMS_H

        // 最百搭 TOP3
        if (showTop) {
            y = sectionTitle(canvas, y, "最百搭 TOP3")
            stats.topVersatile.forEachIndexed { i, t ->
                val rankBg = Paint().apply { color = ACCENT; isAntiAlias = true }
                canvas.drawCircle(PAD + 34f, y + 60f, 34f, rankBg)
                center(canvas, "${i + 1}", PAD + 34f, y + 60f, rankTextPaint)
                canvas.drawText(t.item.name.take(12), PAD + 84f, y + 78f, itemPaint)
                canvas.drawText(
                    "进过 ${t.outfitCount} 套 · 穿 ${t.wearCount} 次",
                    PAD + 84f, y + 148f, faintPaint,
                )
                canvas.drawLine(PAD.toFloat(), y + TOP_ROW_H - 28f, (WIDTH - PAD).toFloat(), y + TOP_ROW_H - 28f, hairPaint)
                y += TOP_ROW_H
            }
        }

        // 品类分布
        y = sectionTitle(canvas, y, "品类分布")
        val total = stats.categoryCounts.values.sum().coerceAtLeast(1)
        val contentW = WIDTH - 2 * PAD
        var x = PAD.toFloat()
        stats.categoryCounts.filterValues { it > 0 }.forEach { (_, count) ->
            val w = contentW * count / total.toFloat()
            val rect = RectF(x, y.toFloat(), x + w - 8f, y.toFloat() + BAR_H)
            canvas.drawRoundRect(rect, 20f, 20f, Paint().apply { color = BAR })
            x += w
        }
        center(canvas, "共 ${stats.itemCount} 件", WIDTH / 2f, y + BAR_H + 72f, faintPaint)
        // 品类图例：长图脱离 App 单独传播，分布需自解释（评审 P1 修复）
        val legend = stats.categoryCounts.filterValues { it > 0 }
            .entries.joinToString(" · ") { "${it.key.label} ${it.value}" }
        center(canvas, legend, WIDTH / 2f, y + BAR_H + 140f, faintPaintSmall)
        y += BAR_H + 178

        // 成品图墙
        if (showWall) {
            y = sectionTitle(canvas, y, "今年上身的搭配")
            photos.forEachIndexed { i, bmp ->
                val row = i / 3
                val col = i % 3
                val left = PAD + col * (CELL + CELL_GAP)
                val cellTop = y + row * (CELL + CELL_GAP)
                val rect = RectF(left.toFloat(), cellTop.toFloat(), left + CELL.toFloat(), cellTop + CELL.toFloat())
                canvas.drawRoundRect(rect, 20f, 20f, Paint().apply { color = 0xFFF0F4EE.toInt() })
                val scale = min(rect.width() / bmp.width, rect.height() / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                canvas.drawBitmap(
                    bmp, Rect(0, 0, bmp.width, bmp.height),
                    RectF(rect.centerX() - dw / 2f, rect.centerY() - dh / 2f, rect.centerX() + dw / 2f, rect.centerY() + dh / 2f),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
            }
            y += rowsOf(photos.size) * (CELL + CELL_GAP)
        }

        // 结尾
        center(canvas, "明年，更好穿搭", WIDTH / 2f, y + 120f, titlePaint)
        center(canvas, "wardrobe · 生成于 $generatedAt", WIDTH / 2f, y + 230f, faintPaint)
        bitmap
    }

    private fun sectionTitle(canvas: Canvas, top: Int, text: String): Int {
        val y = top + TITLE_H / 2f
        canvas.drawRoundRect(RectF(PAD.toFloat(), y - 34f, PAD + 12f, y + 34f), 6f, 6f, Paint().apply { color = ACCENT })
        canvas.drawText(text, PAD + 44f, y + 22f, sectionPaint)
        return top + TITLE_H
    }

    private fun rowsOf(n: Int) = ceil(n / 3.0).toInt().coerceAtLeast(1)

    private fun center(canvas: Canvas, text: String, x: Float, y: Float, paint: TextPaint) {
        // 水平居中 = 左对齐绘制点左移半宽；垂直居中 = fontMetrics 修正
        canvas.drawText(text, x - paint.measureText(text) / 2f, y - (paint.fontMetrics.bottom + paint.fontMetrics.top) / 2f, paint)
    }

    private fun decodeSmall(file: File?): Bitmap? {
        file ?: return null
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= CELL) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        const val WIDTH = 1080
        const val PAD = 72
        const val COVER_H = 560
        const val NUMS_H = 400
        const val TITLE_H = 160
        const val TOP_ROW_H = 220
        const val BAR_H = 96
        const val CATEGORY_H = TITLE_H + BAR_H + 178
        const val CELL = 288
        const val CELL_GAP = 36
        const val FOOTER_H = 320
        const val QUALITY = 90

        const val BG = 0xFFF5F8F2.toInt()
        const val INK = 0xFF1D2620.toInt()
        const val FAINT = 0xFF808D82.toInt()
        const val ACCENT = 0xFF429E68.toInt()
        const val BAR = 0xFFDCE6D8.toInt()
        const val HAIR = 0xFFE3EBE0.toInt()

        private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 92f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        private val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 116f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 58f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val itemPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 56f }
        private val faintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = FAINT; textSize = 44f }
        private val faintPaintSmall = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = FAINT; textSize = 36f }
        private val rankTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = 44f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 130f }
        private val hairPaint = Paint().apply { color = HAIR; strokeWidth = 3f }
    }
}
