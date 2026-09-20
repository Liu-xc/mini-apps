package com.leo.eats.ui.recap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.usecase.RecapStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/**
 * 「年度食光」长图（it-007，ADR-012）：android.graphics 直绘（与 wardrobe OutfitImageComposer
 * 同管线思路），版式 = 封面 → 三大数字 → 最爱 TOP3 → 类型占比 → 月度节奏 → 照片墙(≤9) → 结尾。
 * 视觉方向：奶油底 + 主题绿衬线标题；空数据区块整段跳过。
 */
class RecapLongImage(
    private val stats: RecapStats,
    private val rangeLabel: String,
    private val generatedAt: String,
    private val photoFileOf: suspend (String) -> File?,
) {

    suspend fun render(): Bitmap = withContext(Dispatchers.IO) {
        val photos = stats.photoFiles.mapNotNull { decodeSmall(it) }
        val top3 = stats.topPlaces.take(3)
        val showTop3 = top3.isNotEmpty() && stats.hasData
        val showKind = stats.hasData
        val showWall = photos.isNotEmpty()

        var h = PAD + COVER_H
        if (stats.hasData) h += NUMS_H
        if (showTop3) h += TITLE_H + top3.size * TOP_ROW_H
        if (showKind) h += KIND_H
        // 月度区块实际高 = TITLE_H + MONTHS_H（sectionTitle 返回已含 TITLE_H，预计算须对齐）
        if (stats.hasData) h += TITLE_H + MONTHS_H
        if (showWall) h += TITLE_H + rowsOf(photos.size) * (CELL + CELL_GAP)
        h += FOOTER_H + PAD

        val bitmap = Bitmap.createBitmap(WIDTH, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(BG) }
        var y = PAD

        y += drawCover(canvas, y) + 0
        if (stats.hasData) y = drawNums(canvas, y)
        if (showTop3) y = drawTop3(canvas, y, top3, photos = emptyList())
        if (showKind) y = drawKindRatio(canvas, y)
        if (stats.hasData) y = drawMonths(canvas, y)
        if (showWall) y = drawWall(canvas, y, photos)
        drawFooter(canvas, y)
        bitmap
    }

    // ---- 区块 ----

    private fun drawCover(canvas: Canvas, top: Int): Int {
        val cy = top + COVER_H / 2f
        center(canvas, rangeLabel, WIDTH / 2f, cy - 130f, yearPaint)
        center(canvas, "我的年度食光", WIDTH / 2f, cy + 10f, titlePaint)
        center(canvas, "eats · 年度报告", WIDTH / 2f, cy + 120f, faintPaint)
        return COVER_H
    }

    private fun drawNums(canvas: Canvas, top: Int): Int {
        val cy = top + NUMS_H / 2f
        val cols = listOf(
            "${stats.totalVisits}" to "顿",
            (stats.totalCost?.let { "¥${trimNum(it)}" } ?: "—") to "花费",
            "${stats.placesVisited}" to "家",
        )
        cols.forEachIndexed { i, pair ->
            val value: String = pair.first; val label: String = pair.second
            val cx = WIDTH * (i + 0.5f) / 3f
            center(canvas, value, cx, cy - 46f, if (value.length > 6) numPaintSmall else numPaint)
            center(canvas, label, cx, cy + 66f, faintPaint)
            if (i < 2) {
                val x = WIDTH * (i + 1) / 3f
                canvas.drawLine(x, top + 60f, x, top + NUMS_H - 60f, hairPaint)
            }
        }
        return top + NUMS_H
    }

    private fun drawTop3(canvas: Canvas, top: Int, top3: List<com.leo.eats.domain.usecase.RecapTopPlace>, photos: List<Bitmap>): Int {
        var y = sectionTitle(canvas, top, "最爱 TOP3")
        top3.forEachIndexed { i, t ->
            center(canvas, "${i + 1}", PAD + 34f, y + TOP_ROW_H / 2f - 40f, rankPaint)
            val name = t.place.name.take(12)
            canvas.drawText(name, PAD + 84f, y + 78f, itemPaint)
            val sub = buildString {
                append("${t.count} 次")
                t.avgRating?.let { append(" · 均分 ${"%.1f".format(it)}") }
            }
            canvas.drawText(sub, PAD + 84f, y + 148f, faintPaint)
            canvas.drawLine(PAD.toFloat(), y + TOP_ROW_H - 28f, (WIDTH - PAD).toFloat(), y + TOP_ROW_H - 28f, hairPaint)
            y += TOP_ROW_H
        }
        return y
    }

    private fun drawKindRatio(canvas: Canvas, top: Int): Int {
        var y = sectionTitle(canvas, top, "类型占比")
        val total = stats.kindCounts.values.sum().coerceAtLeast(1)
        val contentW = WIDTH - 2 * PAD
        val order = listOf(
            PlaceKind.RESTAURANT to "堂食",
            PlaceKind.TAKEOUT to "外卖",
            PlaceKind.HOME to "自做",
        )
        var x = PAD.toFloat()
        order.forEach { (kind, label) ->
            val w = contentW * (stats.kindCounts[kind] ?: 0) / total.toFloat()
            if (w > 0f) {
                val rect = RectF(x, y.toFloat(), x + w - 8f, y.toFloat() + BAR_H)
                canvas.drawRoundRect(rect, 20f, 20f, Paint().apply { color = kindColor(kind) })
                x += w
            }
        }
        var lx = PAD.toFloat()
        order.forEach { (kind, label) ->
            val text = "$label ${"%.0f".format((stats.kindCounts[kind] ?: 0) * 100f / total)}%"
            canvas.drawText(text, lx, y + BAR_H + 72f, faintPaint)
            lx += faintPaint.measureText(text) + 56f
        }
        return y + BAR_H + 110
    }

    private fun drawMonths(canvas: Canvas, top: Int): Int {
        var y = sectionTitle(canvas, top, "月度节奏")
        val months = stats.monthly
        val max = months.maxOf { it.count }.coerceAtLeast(1)
        val contentW = WIDTH - 2 * PAD
        val gap = 24f
        val bw = (contentW - gap * 11) / 12f
        val baseY = y + MONTHS_H - 130f
        val peak = months.indices.maxBy { months[it].count }
        months.forEachIndexed { i, m ->
            val bh = if (m.count == 0) 14f else 14f + 300f * m.count / max
            val rect = RectF(PAD + i * (bw + gap), baseY - bh, PAD + i * (bw + gap) + bw, baseY)
            canvas.drawRoundRect(rect, 14f, 14f, Paint().apply { color = if (i == peak) ACCENT else BAR })
            if (i == 0 || i == 5 || i == 11) {
                center(canvas, m.label, rect.centerX(), baseY + 56f, faintPaintSmall)
            }
        }
        return y + MONTHS_H
    }

    private fun drawWall(canvas: Canvas, top: Int, photos: List<Bitmap>): Int {
        var y = sectionTitle(canvas, top, "这一年拍下的")
        val contentW = WIDTH - 2 * PAD
        val gap = CELL_GAP.toFloat()
        photos.forEachIndexed { i, bmp ->
            val row = i / 3
            val col = i % 3
            val left = PAD + col * (CELL + gap)
            val cellTop = y + row * (CELL + gap)
            val rect = RectF(left, cellTop.toFloat(), left + CELL, cellTop + CELL.toFloat())
            val bg = RectF(rect)
            canvas.drawRoundRect(bg, 20f, 20f, Paint().apply { color = 0xFFF0F3EC.toInt() })
            val scale = min(rect.width() / bmp.width, rect.height() / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = RectF(
                rect.centerX() - dw / 2f, rect.centerY() - dh / 2f,
                rect.centerX() + dw / 2f, rect.centerY() + dh / 2f,
            )
            canvas.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return y + rowsOf(photos.size) * (CELL + CELL_GAP)
    }

    private fun drawFooter(canvas: Canvas, top: Int) {
        center(canvas, "吃好喝好，来年继续", WIDTH / 2f, top + 120f, titlePaint)
        center(canvas, "eats · 生成于 $generatedAt", WIDTH / 2f, top + 230f, faintPaint)
    }

    private fun sectionTitle(canvas: Canvas, top: Int, text: String): Int {
        val y = top + TITLE_H / 2f
        canvas.drawRoundRect(RectF(PAD.toFloat(), y - 34f, PAD + 12f, y + 34f), 6f, 6f, Paint().apply { color = ACCENT })
        canvas.drawText(text, PAD + 44f, y + 22f, sectionPaint)
        return top + TITLE_H
    }

    // ---- 工具 ----

    private fun rowsOf(n: Int) = ceil(n / 3.0).toInt().coerceAtLeast(1)

    private fun center(canvas: Canvas, text: String, x: Float, y: Float, paint: TextPaint) {
        // 水平居中 = 左对齐绘制点左移半宽；垂直居中 = fontMetrics 修正
        canvas.drawText(text, x - paint.measureText(text) / 2f, y - (paint.fontMetrics.bottom + paint.fontMetrics.top) / 2f, paint)
    }

    private fun trimNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

    private suspend fun decodeSmall(name: String): Bitmap? {
        val file = photoFileOf(name)?.takeIf { it.exists() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= CELL) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun kindColor(kind: PlaceKind): Int = when (kind) {
        PlaceKind.RESTAURANT -> 0xFFD25446.toInt()
        PlaceKind.TAKEOUT -> 0xFFDA9A2B.toInt()
        PlaceKind.HOME -> 0xFF4C9E5F.toInt()
    }

    companion object {
        const val WIDTH = 1080
        const val PAD = 72
        const val COVER_H = 560
        const val NUMS_H = 400
        const val TITLE_H = 160
        const val TOP_ROW_H = 220
        const val BAR_H = 96
        const val KIND_H = TITLE_H + BAR_H + 110
        const val MONTHS_H = 560
        const val CELL = 288
        const val CELL_GAP = 36
        const val FOOTER_H = 320

        const val BG = 0xFFF7F5EE.toInt()
        const val INK = 0xFF1E2822.toInt()
        const val FAINT = 0xFF84907F.toInt()
        const val ACCENT = 0xFF3FA265.toInt()
        const val BAR = 0xFFDCE6D8.toInt()
        const val HAIR = 0xFFE2ECDF.toInt()

        private val yearPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT; textSize = 190f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 92f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        private val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 116f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val numPaintSmall = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 84f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 58f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val itemPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 56f }
        private val faintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = FAINT; textSize = 44f }
        private val faintPaintSmall = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = FAINT; textSize = 34f }
        private val rankPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT; textSize = 44f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val hairPaint = Paint().apply { color = HAIR; strokeWidth = 3f }
    }
}
