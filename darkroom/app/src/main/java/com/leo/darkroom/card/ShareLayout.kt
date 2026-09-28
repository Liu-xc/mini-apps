package com.leo.darkroom.card

import com.leo.darkroom.develop.DevelopMode

enum class ShareFormat(
    val label: String,
    val ratioLabel: String,
    val width: Int,
    val height: Int,
) {
    SQUARE("方形", "1:1", 1080, 1080),
    FEED("信息流", "4:5", 1080, 1350),
    STORY("竖屏故事", "9:16", 1080, 1920),
}

/** Common social composition for still and video exports. Coordinates are in output pixels. */
data class ShareLayout(
    val width: Float,
    val height: Float,
    val card: FRect,
    val wordmark: FRect,
    val caption: FRect,
    val safeTop: Float,
    val safeBottom: Float,
) {
    companion object {
        fun solve(
            width: Float,
            height: Float,
            format: ShareFormat,
            mode: DevelopMode = DevelopMode.POLAROID,
        ): ShareLayout {
            require(width > 0f && height > 0f) { "share frame must be positive" }
            val cardWidthFraction = when (format) {
                ShareFormat.SQUARE -> 0.74f
                ShareFormat.FEED -> 0.72f
                ShareFormat.STORY -> 0.80f
            }
            // 卡面高宽比随显影模式变化（胶片是横条），构图按模式反解
            val aspect = CardLayout.aspectOf(mode)
            val cardWidth = minOf(width * cardWidthFraction, height / aspect * 0.90f)
            val cardHeight = cardWidth * aspect
            val cardLeft = (width - cardWidth) / 2f
            val cardTop = (height - cardHeight) / 2f
            val safeTop = height * 0.12f
            val safeBottom = height * 0.88f
            val wordmarkY = when (format) {
                ShareFormat.SQUARE -> height * 0.10f
                ShareFormat.FEED -> height * 0.10f
                ShareFormat.STORY -> height * 0.16f
            }
            val captionY = when (format) {
                ShareFormat.SQUARE -> height * 0.90f
                ShareFormat.FEED -> height * 0.86f
                ShareFormat.STORY -> height * 0.86f
            }
            val labelHeight = width * 0.055f
            return ShareLayout(
                width = width,
                height = height,
                card = FRect(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight),
                wordmark = FRect(
                    left = width * 0.12f,
                    top = wordmarkY - labelHeight,
                    right = width * 0.88f,
                    bottom = wordmarkY + labelHeight * 0.25f,
                ),
                caption = FRect(
                    left = width * 0.12f,
                    top = captionY - labelHeight,
                    right = width * 0.88f,
                    bottom = captionY + labelHeight * 0.25f,
                ),
                safeTop = safeTop,
                safeBottom = safeBottom,
            )
        }
    }
}
