package com.leo.darkroom.card

/**
 * 卡面布局的纯数学解——单一定位真源：
 * Compose 实时预览与 android.graphics 导出/视频渲染器共用同一套坐标（it-001 AC4）。
 * 全部字段不依赖 android.*, JVM 单测直接覆盖。
 */
data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class CardLayout(
    val width: Float,
    val height: Float,
    /** 相纸区（方形） */
    val photo: FRect,
    /** 手写标题行 */
    val title: FRect,
    /** 日期章（右对齐） */
    val stamp: FRect,
    /** 卡脚水印（右对齐微缩行） */
    val watermark: FRect,
    /** 手写标题字号（px） */
    val titleSize: Float,
    /** 日期章字号（px） */
    val stampSize: Float,
    /** 水印字号（px） */
    val watermarkSize: Float,
) {
    /** 经典拍立得整卡高宽比 = 1.20（86×104mm 实物比例） */
    val aspect: Float get() = height / width

    companion object {
        /** 等宽白边（相对卡宽）——对齐实物 86mm 卡的 3.5mm 边 */
        const val SIDE_FR = 0.05f

        /** 照片下方留白（相对卡宽）——下边更宽，整卡高宽比 = 1.20 经典拍立得 */
        const val BOTTOM_FR = 0.25f

        /** 整卡高宽比 = 1 + BOTTOM_FR - SIDE_FR = 1.20 */
        const val ASPECT = 1f + BOTTOM_FR - SIDE_FR

        /** 手写标题域右界（相对卡宽）——必须 ≤ [STAMP_LEFT_FR]，两域互斥不重叠 */
        const val TITLE_RIGHT_FR = 0.52f

        /** 日期章域左界（相对卡宽）——与标题域留 0.02w 天沟 */
        const val STAMP_LEFT_FR = 0.54f

        /**
         * 逐字段解布局；宽度是唯一输入（高度由比例推导）。
         * [maxH] = 可用高度上限（it-002 O3）：显影台 weight 槽按高度反解宽度，
         * 保证整卡（含签名区）永不出容器；导出端不传则保持原纯宽度行为。
         */
        fun solve(width: Float, maxH: Float = Float.MAX_VALUE): CardLayout {
            require(width > 0f) { "card width must be positive" }
            val w = if (maxH.isFinite() && maxH > 0f) minOf(width, maxH / ASPECT) else width
            val side = w * SIDE_FR
            val bottom = w * BOTTOM_FR
            val photoSize = w - side * 2f
            val height = photoSize + side + bottom
            val photo = FRect(side, side, side + photoSize, side + photoSize)
            val footerTop = photo.bottom
            val footerH = height - footerTop

            val title = FRect(
                left = side * 1.5f,
                top = footerTop + footerH * 0.10f,
                right = w * TITLE_RIGHT_FR,
                bottom = footerTop + footerH * 0.66f,
            )
            val stamp = FRect(
                left = w * STAMP_LEFT_FR,
                top = footerTop + footerH * 0.13f,
                right = w - side * 1.5f,
                bottom = footerTop + footerH * 0.67f,
            )
            val watermark = FRect(
                left = side * 1.5f,
                top = footerTop + footerH * 0.72f,
                right = w - side * 1.5f,
                bottom = height - footerH * 0.08f,
            )
            return CardLayout(
                width = w,
                height = height,
                photo = photo,
                title = title,
                stamp = stamp,
                watermark = watermark,
                titleSize = footerH * 0.36f,
                stampSize = footerH * 0.30f,
                watermarkSize = w * 0.024f,
            )
        }
    }
}
