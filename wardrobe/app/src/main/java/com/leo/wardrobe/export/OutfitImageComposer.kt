package com.leo.wardrobe.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.leo.wardrobe.data.image.ImageEditStore
import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.isWishSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 合成图（it-017 修订，specs W6）：
 * 结构 = Prompt（顶部，同时写入 EXIF/XMP meta）→ 形象参考照（可选，标注条）→
 * 人体比例拼贴（与 W1 搭配页/BodyCollage 同构：帽行 / 上身行=外套|上装|连衣裙 /
 * 腿行=包|下装|配饰 / 鞋行，照片 Fit 不变形、格底标签条标注品类）。
 * 修订动机（Leo 走查反馈）：原全宽竖堆长图近万 px 太长；按真人比例紧凑化后约 1/3。
 */
class OutfitImageComposer(private val imageStore: ImageEditStore) {

    /** 生成并写入 export 目录，返回 JPEG 文件（含 EXIF prompt）；refPhotoFile 非空时其照片置顶（it-017） */
    suspend fun composeToExportFile(items: List<Item>, prompt: String, refPhotoFile: String? = null): File? =
        withContext(Dispatchers.IO) {
            val refPhoto = refPhotoFile?.let { imageStore.decode(it) }
            val bitmap = renderLong(items, prompt, refPhoto) ?: return@withContext null
            val out = File(imageStore.exportDir(), "outfit_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bitmap.recycle()
            refPhoto?.recycle()
            // Prompt 元数据：XMP（UTF-8，见 JpegXmp 决策说明）
            runCatching { JpegXmp.embedPrompt(out, prompt) }
            out
        }

    /** 拼贴格：一张单品照片 + 底部品类标签条 */
    private class Cell(val photo: Bitmap, val label: String, val rect: RectF)

    private suspend fun renderLong(items: List<Item>, prompt: String, refPhoto: Bitmap?): Bitmap? {
        if (items.isEmpty()) return null

        val promptLayout = promptLayout(prompt)
        val refBlockH = if (refPhoto != null) refDrawHeight(refPhoto) + REF_LABEL_H + 14 else 0
        val cells = buildCells(items)
        if (cells.isEmpty()) return null

        // 预计算总高
        var height = PAD + promptLayout.height + 2 * PROMPT_PAD + GAP
        height += refBlockH
        height += cells.maxOf { it.rect.bottom }.toInt() + PAD

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        // 1. Prompt 置顶（Leo：先读指令再看图；meta 里同步带一份）
        drawPromptBlock(canvas, promptLayout, PAD.toFloat())

        // 2. 参考照
        if (refPhoto != null) drawRefPhoto(canvas, refPhoto, PAD + promptLayout.height + 2 * PROMPT_PAD + GAP)

        // 3. 人体比例拼贴
        val cellsTop = PAD + promptLayout.height + 2 * PROMPT_PAD + GAP + refBlockH
        canvas.translate(0f, cellsTop.toFloat())
        cells.forEach { drawCell(canvas, it) }
        canvas.translate(0f, -cellsTop.toFloat())

        return bitmap
    }

    // ---- 人体比例布局（与 W1/BodyCollage 同构：帽 / 上身行 / 腿行 / 鞋行）----

    private suspend fun buildCells(items: List<Item>): List<Cell> {
        val byCat = items.groupBy { it.category }
        fun first(c: WardrobeCategory) = byCat[c]?.firstOrNull()

        val hat = first(WardrobeCategory.HAT)
        val torso = listOf(WardrobeCategory.OUTERWEAR, WardrobeCategory.TOP, WardrobeCategory.DRESS)
            .mapNotNull { first(it) }
        val bottom = first(WardrobeCategory.BOTTOM)
        val bag = first(WardrobeCategory.BAG)
        val acc = first(WardrobeCategory.ACCESSORY)
        val shoes = first(WardrobeCategory.SHOES)

        val cells = ArrayList<Cell>()
        val cw = WIDTH - 2 * PAD
        var y = 0f

        // 帽行：顶部居中小卡（W1 fillMaxWidth(0.34), aspect 1）
        hat?.let {
            val w = cw * 0.34f
            cells += Cell(photo(it), label(it), RectF((WIDTH - w) / 2f, y, (WIDTH + w) / 2f, y + w))
            y += w + GAP
        }

        // 上身行：外套 | 上装 | 连衣裙（有啥放啥，同高；单件时居中收窄防超高）
        if (torso.isNotEmpty()) {
            val n = torso.size
            val w = if (n == 1) cw * 0.55f else (cw - (n - 1) * GAP).toFloat() / n
            val h = minOf(w / TORSO_ASPECT, TORSO_MAX_H)
            var x = (WIDTH - (n * w + (n - 1) * GAP)) / 2f
            torso.forEach {
                cells += Cell(photo(it), label(it), RectF(x, y, x + w, y + h))
                x += w + GAP
            }
            y += h + GAP
        }

        // 腿行：包(矮挂) | 下装(窄长主体) | 配饰(矮挂)；无下装时挂件并排居中
        if (bottom != null || bag != null || acc != null) {
            if (bottom == null) {
                val pendants = listOfNotNull(bag, acc)
                val w = cw * 0.24f
                val h = w / PENDANT_ASPECT
                val total = pendants.size * w + (pendants.size - 1) * GAP
                var x = (WIDTH - total) / 2f
                pendants.forEach {
                    cells += Cell(photo(it), label(it), RectF(x, y, x + w, y + h))
                    x += w + GAP
                }
                y += h + GAP
            } else {
                val side = if (bag != null || acc != null) cw * 0.24f else 0f
                val midW = if (side > 0f) cw - 2 * side - 2 * GAP else cw * 0.55f
                val rowH = midW / LEG_ASPECT
                val rowY = y
                if (side > 0f) {
                    bag?.let {
                        val h = rowH * 0.62f
                        cells += Cell(photo(it), label(it), RectF(PAD.toFloat(), rowY + (rowH - h) / 2f, PAD + side, rowY + (rowH + h) / 2f))
                    }
                }
                val midX = if (side > 0f) PAD + side + GAP else (WIDTH - midW) / 2f
                cells += Cell(photo(bottom), label(bottom), RectF(midX, rowY, midX + midW, rowY + rowH))
                if (side > 0f) {
                    acc?.let {
                        val h = rowH * 0.62f
                        cells += Cell(photo(it), label(it), RectF(WIDTH - PAD - side, rowY + (rowH - h) / 2f, WIDTH - PAD.toFloat(), rowY + (rowH + h) / 2f))
                    }
                }
                y += rowH + GAP
            }
        }

        // 鞋行：底部扁平通栏（W1 fillMaxWidth(0.58), aspect 2.6）
        shoes?.let {
            val w = cw * 0.62f
            val h = w / SHOES_ASPECT
            cells += Cell(photo(it), label(it), RectF((WIDTH - w) / 2f, y, (WIDTH + w) / 2f, y + h))
            y += h + GAP
        }

        return cells
    }

    private suspend fun photo(item: Item): Bitmap = imageStore.decode(item.imageFile) ?: FALLBACK

    private fun label(item: Item): String = buildString {
        if (item.isWishSlot) append("🌟想买 · ") // it-019：愿望单品标注
        append(item.category.label)
        append(" · ")
        append(item.name.take(14))
        if (item.color.isNotBlank()) append(" · ").append(item.color.take(8))
    }

    // ---- 绘制 ----

    private fun drawCell(canvas: Canvas, cell: Cell) {
        val r = cell.rect
        val bg = Paint().apply { color = 0xFFF7F3EC.toInt() }
        canvas.drawRoundRect(r, 14f, 14f, bg)
        // 照片 Fit 居中（不变形）
        val inset = 8f
        val box = RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset - CELL_LABEL_H)
        val scale = minOf(box.width() / cell.photo.width, box.height() / cell.photo.height)
        val dw = cell.photo.width * scale
        val dh = cell.photo.height * scale
        val l = box.centerX() - dw / 2f
        val t = box.centerY() - dh / 2f
        canvas.drawBitmap(cell.photo, null, RectF(l, t, l + dw, t + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        // 底部品类标签条
        val labelRect = RectF(r.left, r.bottom - CELL_LABEL_H, r.right, r.bottom)
        canvas.drawRect(labelRect, Paint().apply { color = 0xE6FFFFFF.toInt() })
        canvas.drawText(
            cell.label,
            labelRect.left + 14f,
            labelRect.centerY() - (labelPaint.fontMetrics.bottom + labelPaint.fontMetrics.top) / 2f,
            labelPaint,
        )
    }

    private fun drawRefPhoto(canvas: Canvas, refPhoto: Bitmap, top: Int) {
        val contentW = WIDTH - 2 * PAD
        val scale = minOf(contentW.toFloat() / refPhoto.width, REF_MAX_HEIGHT.toFloat() / refPhoto.height)
        val dw = refPhoto.width * scale
        val dh = refPhoto.height * scale
        val left = (WIDTH - dw) / 2f
        canvas.drawBitmap(
            refPhoto,
            Rect(0, 0, refPhoto.width, refPhoto.height),
            RectF(left, top.toFloat(), left + dw, top + dh),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        // 标注条：给 AI 的语义锚点
        val text = "本人形象参考 · 五官身形以此为准"
        val w = refLabelPaint.measureText(text) + 2 * REF_LABEL_PAD_X
        val rect = RectF((WIDTH - w) / 2f, top + dh + 14f, (WIDTH + w) / 2f, top + dh + 14f + REF_LABEL_H)
        canvas.drawRoundRect(rect, 16f, 16f, Paint().apply { color = 0xFFFAF3E6.toInt() })
        canvas.drawText(
            text,
            rect.left + REF_LABEL_PAD_X,
            rect.centerY() - (refLabelPaint.fontMetrics.bottom + refLabelPaint.fontMetrics.top) / 2f,
            refLabelPaint,
        )
    }

    private fun refDrawHeight(refPhoto: Bitmap): Int {
        val contentW = WIDTH - 2 * PAD
        val scale = minOf(contentW.toFloat() / refPhoto.width, REF_MAX_HEIGHT.toFloat() / refPhoto.height)
        return (refPhoto.height * scale).toInt().coerceAtLeast(1)
    }

    private fun drawPromptBlock(canvas: Canvas, promptLayout: StaticLayout, top: Float) {
        val blockH = promptLayout.height + 2 * PROMPT_PAD
        val rect = RectF(PAD.toFloat(), top, (WIDTH - PAD).toFloat(), top + blockH)
        canvas.drawRoundRect(rect, 18f, 18f, Paint().apply { color = 0xFFFAF7F2.toInt() })
        canvas.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8E2D9.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        })
        canvas.save()
        canvas.translate((PAD + PROMPT_PAD).toFloat(), top + PROMPT_PAD)
        promptLayout.draw(canvas)
        canvas.restore()
    }

    private fun promptLayout(prompt: String): StaticLayout = StaticLayout.Builder
        .obtain(prompt, 0, prompt.length, promptPaint, WIDTH - 2 * PAD - 2 * PROMPT_PAD)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(10f, 1f)
        .build()

    private val promptPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A1A1A.toInt()
        textSize = 34f
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
    }
    private val refLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A6D3B.toInt()
        textSize = 32f
    }

    companion object {
        const val WIDTH = 1024
        const val PAD = 32
        const val GAP = 22
        const val PROMPT_PAD = 26
        /** it-017 参考照显示最大高度与标注条高度/内边距 */
        const val REF_MAX_HEIGHT = 1100
        const val REF_LABEL_H = 60
        const val REF_LABEL_PAD_X = 28f
        /** 拼贴格比例（与 W1 OutfitSlot/BodyCollage 同构） */
        const val TORSO_ASPECT = 0.78f
        const val TORSO_MAX_H = 760f
        const val LEG_ASPECT = 0.6f
        const val PENDANT_ASPECT = 0.85f
        const val SHOES_ASPECT = 2.6f
        const val CELL_LABEL_H = 44f
        const val QUALITY = 90

        private val FALLBACK = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    }
}
