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
import com.leo.wardrobe.data.image.ImageFileStore
import com.leo.wardrobe.domain.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 合成图（it-002 重构，specs W6）：
 * 纵向长图——按槽位顺序从上到下排列单品照片（品类·名称标签条），
 * 底部绘制完整 Prompt，并写入 JPEG EXIF（UserComment + ImageDescription）。
 */
class OutfitImageComposer(private val imageStore: ImageFileStore) {

    /** 生成并写入 export 目录，返回 JPEG 文件（含 EXIF prompt） */
    suspend fun composeToExportFile(items: List<Item>, prompt: String): File? =
        withContext(Dispatchers.IO) {
            val bitmap = renderLong(items, prompt) ?: return@withContext null
            val out = File(imageStore.exportDir(), "outfit_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bitmap.recycle()
            // Prompt 元数据：XMP（UTF-8，见 JpegXmp 决策说明）
            runCatching { JpegXmp.embedPrompt(out, prompt) }
            out
        }

    private suspend fun renderLong(items: List<Item>, prompt: String): Bitmap? {
        val entries = items.mapNotNull { item ->
            imageStore.decode(item.imageFile)?.let { item to it }
        }
        if (entries.isEmpty()) return null

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
        }
        val promptPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            textSize = 34f
        }

        val contentW = WIDTH - 2 * PAD
        val promptLayout = StaticLayout.Builder
            .obtain(prompt, 0, prompt.length, promptPaint, contentW - 2 * PROMPT_PAD)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1f)
            .build()

        // 预计算高度
        var height = PAD
        val photoRects = ArrayList<RectF>(entries.size)
        entries.forEach { (item, photo) ->
            val scale = contentW.toFloat() / photo.width
            val ph = (photo.height * scale).toInt().coerceAtMost(MAX_CELL_HEIGHT)
            photoRects += RectF(PAD.toFloat(), (height + LABEL_H).toFloat(), (WIDTH - PAD).toFloat(), (height + LABEL_H + ph).toFloat())
            height += LABEL_H + ph + GAP
        }
        val promptBlockH = promptLayout.height + 2 * PROMPT_PAD
        height += promptBlockH + PAD

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        entries.forEachIndexed { index, (item, photo) ->
            val top = photoRects[index]
            canvas.drawText(
                "${item.category.label} · ${item.name.take(14)}" +
                    (item.color.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                PAD.toFloat(),
                top.top - 18f,
                labelPaint,
            )
            canvas.drawBitmap(
                photo,
                Rect(0, 0, photo.width, photo.height),
                top,
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }

        // 底部 Prompt 区：浅米底 + 细边框 + 自动换行文字
        val blockTop = height - PAD - promptBlockH
        val blockRect = RectF(
            PAD.toFloat(), blockTop.toFloat(),
            (WIDTH - PAD).toFloat(), (blockTop + promptBlockH).toFloat(),
        )
        val blockPaint = Paint().apply { color = 0xFFFAF7F2.toInt() }
        canvas.drawRoundRect(blockRect, 18f, 18f, blockPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8E2D9.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(blockRect, 18f, 18f, borderPaint)
        canvas.save()
        canvas.translate((PAD + PROMPT_PAD).toFloat(), (blockTop + PROMPT_PAD).toFloat())
        promptLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    companion object {
        const val WIDTH = 1024
        const val PAD = 32
        const val GAP = 22
        const val LABEL_H = 56
        const val PROMPT_PAD = 26
        /** 单品照片最大高度，防超长图 */
        const val MAX_CELL_HEIGHT = 1400
        const val QUALITY = 90
    }
}
