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

    private suspend fun renderLong(items: List<Item>, prompt: String, refPhoto: Bitmap?): Bitmap? {
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
        val refLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A6D3B.toInt()
            textSize = 32f
        }

        val contentW = WIDTH - 2 * PAD
        val promptLayout = StaticLayout.Builder
            .obtain(prompt, 0, prompt.length, promptPaint, contentW - 2 * PROMPT_PAD)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1f)
            .build()

        // 参考照显示尺寸：等比缩放（不变形），限高则缩小，水平居中（it-017）
        val refDrawW: Int
        val refDrawH: Int
        if (refPhoto != null) {
            val scale = minOf(
                contentW.toFloat() / refPhoto.width,
                REF_MAX_HEIGHT.toFloat() / refPhoto.height,
            )
            refDrawW = (refPhoto.width * scale).toInt().coerceAtLeast(1)
            refDrawH = (refPhoto.height * scale).toInt().coerceAtLeast(1)
        } else {
            refDrawW = 0; refDrawH = 0
        }
        val refBlockH = if (refPhoto != null) refDrawH + REF_LABEL_H + GAP else 0

        // 预计算高度
        var height = PAD + refBlockH
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

        // 参考照置顶：照片水平居中 + 标注条（it-017：给生图 Agent 的形象参考）
        if (refPhoto != null) {
            val refLeft = (WIDTH - refDrawW) / 2f
            canvas.drawBitmap(
                refPhoto,
                Rect(0, 0, refPhoto.width, refPhoto.height),
                RectF(refLeft, PAD.toFloat(), refLeft + refDrawW, (PAD + refDrawH).toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val labelText = "本人形象参考 · 五官身形以此为准"
            val labelW = refLabelPaint.measureText(labelText) + 2 * REF_LABEL_PAD_X
            val labelRect = RectF(
                (WIDTH - labelW) / 2f, (PAD + refDrawH + 14).toFloat(),
                (WIDTH + labelW) / 2f, (PAD + refDrawH + 14 + REF_LABEL_H - 14).toFloat(),
            )
            canvas.drawRoundRect(labelRect, 16f, 16f, Paint().apply { color = 0xFFFAF3E6.toInt() })
            canvas.drawText(
                labelText,
                labelRect.left + REF_LABEL_PAD_X,
                labelRect.centerY() + (refLabelPaint.fontMetrics.bottom - refLabelPaint.fontMetrics.top) / 2 - refLabelPaint.fontMetrics.bottom,
                refLabelPaint,
            )
        }

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
        /** it-017 参考照显示最大高度与标注条高度/内边距 */
        const val REF_MAX_HEIGHT = 1100
        const val REF_LABEL_H = 60
        const val REF_LABEL_PAD_X = 28f
        const val QUALITY = 90
    }
}
