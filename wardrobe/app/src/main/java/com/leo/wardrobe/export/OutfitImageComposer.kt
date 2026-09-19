package com.leo.wardrobe.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.leo.wardrobe.data.image.ImageFileStore
import com.leo.wardrobe.domain.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/**
 * 合成图（specs W6）：选中单品 2 列网格拼图，白底 + 品类标签。
 */
class OutfitImageComposer(private val imageStore: ImageFileStore) {

    suspend fun compose(items: List<Item>): Bitmap? = withContext(Dispatchers.Default) {
        val entries = items.mapNotNull { item ->
            imageStore.decode(item.imageFile)?.let { item to it }
        }
        if (entries.isEmpty()) return@withContext null
        render(entries)
    }

    /** 合成并写入 export 目录（复制/分享用），返回文件 */
    suspend fun composeToExportFile(items: List<Item>): File? {
        val bitmap = compose(items) ?: return null
        val out = File(imageStore.exportDir(), "outfit_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return out
    }

    private fun render(entries: List<Pair<Item, Bitmap>>): Bitmap {
        val cols = 2
        val rows = ceil(entries.size / cols.toDouble()).toInt()
        val w = cols * CELL + (cols + 1) * PADDING
        val h = rows * (CELL + LABEL_HEIGHT + PADDING) + PADDING

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }

        entries.forEachIndexed { index, (item, photo) ->
            val col = index % cols
            val row = index / cols
            val left = PADDING + col * (CELL + PADDING)
            val top = PADDING + row * (CELL + LABEL_HEIGHT + PADDING)

            // 等比缩放居中（letterbox）
            val scale = min(CELL.toFloat() / photo.width, CELL.toFloat() / photo.height)
            val dw = photo.width * scale
            val dh = photo.height * scale
            val dst = RectF(
                left + (CELL - dw) / 2f,
                top + (CELL - dh) / 2f,
                left + (CELL + dw) / 2f,
                top + (CELL + dh) / 2f,
            )
            canvas.drawBitmap(photo, Rect(0, 0, photo.width, photo.height), dst, photoPaint)

            val label = "${item.category.label} · ${item.name.take(10)}"
            canvas.drawText(
                label,
                left + CELL / 2f,
                top + CELL + 44f,
                labelPaint,
            )
        }
        return bitmap
    }

    companion object {
        const val CELL = 512
        const val LABEL_HEIGHT = 64
        const val PADDING = 24
    }
}
