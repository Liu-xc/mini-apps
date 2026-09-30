package com.leo.lottery.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.leo.lottery.core.Ticket
import com.leo.lottery.data.ImageExporter

/**
 * 票据截取：内容 record 进 GraphicsLayer（与预览同一渲染器，US-4.2），
 * 导出时取整层位图落相册。
 */
@Composable
fun rememberTicketCapture(): TicketCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { TicketCapture(layer) }
}

class TicketCapture internal constructor(private val layer: GraphicsLayer) {

    /** 供预览容器挂载：drawWithContent { capture.hijack(this) } 等价内容见各屏用法。 */
    val graphicsLayer: GraphicsLayer get() = layer

    /** 导出 PNG 到 Pictures/拾彩/，成功返回 true。 */
    suspend fun export(context: Context, ticket: Ticket): Boolean {
        val image: ImageBitmap = layer.toImageBitmap()
        val name = "拾彩-${ticket.game.label}-第${ticket.targetIssue}期-${ticket.take}批"
        return ImageExporter.savePng(context, image.asAndroidBitmap(), name) != null
    }
}

/** 预览容器修饰：内容先进 layer 再整体绘制，layer 始终持有最新帧。 */
fun Modifier.captureLayer(capture: TicketCapture): Modifier =
    drawWithContent {
        capture.graphicsLayer.record {
            this@drawWithContent.drawContent()
        }
        drawLayer(capture.graphicsLayer)
    }
