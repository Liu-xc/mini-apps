package com.leo.darkroom.card

import com.leo.darkroom.develop.DevelopMode

/**
 * 卡面布局的纯数学解——单一定位真源：
 * Compose 实时预览与 android.graphics 导出/视频渲染器共用同一套坐标（it-001 AC4）。
 * 全部字段不依赖 android.*, JVM 单测直接覆盖。
 *
 * it-007 M3：几何随 [DevelopMode] 分叉——拍立得白框、数码全幅屏、胶片齿孔条，
 * 但「标题域右界 ≤ 日期章域左界」的互斥约束对三种形态同样成立。
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
    /** 照片区 */
    val photo: FRect,
    /** 两行以内的作品标题区 */
    val title: FRect,
    /** 日期章（右对齐） */
    val stamp: FRect,
    /** 卡脚水印（右对齐微缩行） */
    val watermark: FRect,
    /** 作品标题字号（px） */
    val titleSize: Float,
    /** 日期章字号（px） */
    val stampSize: Float,
    /** 水印字号（px） */
    val watermarkSize: Float,
    /** 上下齿孔带（仅胶片模式非空，由 [sprocketHoles] 解出孔位） */
    val sprocketTop: FRect? = null,
    val sprocketBottom: FRect? = null,
) {
    /** 整卡高宽比 */
    val aspect: Float get() = height / width

    companion object {
        /** 等宽白边（相对卡宽）——对齐实物 86mm 卡的 3.5mm 边 */
        const val SIDE_FR = 0.05f

        /** 照片下方留白（相对卡宽）——下边更宽，整卡高宽比 = 1.20 经典拍立得 */
        const val BOTTOM_FR = 0.25f

        /** 拍立得与数码整卡高宽比 = 1 + BOTTOM_FR - SIDE_FR = 1.20 */
        const val ASPECT = 1f + BOTTOM_FR - SIDE_FR

        /** 作品标题域右界（相对卡宽）——必须 ≤ [STAMP_LEFT_FR]，两域互斥不重叠 */
        const val TITLE_RIGHT_FR = 0.52f

        /** 日期章域左界（相对卡宽）——与标题域留 0.02w 天沟 */
        const val STAMP_LEFT_FR = 0.54f

        // —— 胶片形态（相对卡宽）——
        private const val FILM_SPROCKET_FR = 0.09f
        private const val FILM_INFO_FR = 0.13f
        private const val FILM_PHOTO_RATIO = 1.5f

        /** 胶片整卡高宽比：齿孔 + 3:2 片格 + 齿孔 + 片基信息带 */
        val FILM_ASPECT: Float =
            FILM_SPROCKET_FR * 2f + 1f / FILM_PHOTO_RATIO + FILM_INFO_FR

        /** 模式对应的整卡高宽比（ShareLayout 按此解构图） */
        fun aspectOf(mode: DevelopMode): Float = when (mode) {
            DevelopMode.POLAROID, DevelopMode.DIGITAL -> ASPECT
            DevelopMode.FILM -> FILM_ASPECT
        }

        /**
         * 逐字段解布局；宽度是唯一输入（高度由比例推导）。
         * [maxH] = 可用高度上限（it-002 O3）：显影台 weight 槽按高度反解宽度，
         * 保证整卡（含签名区）永不出容器；导出端不传则保持原纯宽度行为。
         */
        fun solve(
            width: Float,
            maxH: Float = Float.MAX_VALUE,
            mode: DevelopMode = DevelopMode.POLAROID,
        ): CardLayout {
            require(width > 0f) { "card width must be positive" }
            val aspect = aspectOf(mode)
            val w = if (maxH.isFinite() && maxH > 0f) minOf(width, maxH / aspect) else width
            return when (mode) {
                DevelopMode.FILM -> filmLayout(w)
                DevelopMode.POLAROID -> polaroidLayout(w)
                DevelopMode.DIGITAL -> digitalLayout(w)
            }
        }

        /** 经典拍立得：方形成像区居上，底边留白承载签名 */
        private fun polaroidLayout(w: Float): CardLayout {
            val side = w * SIDE_FR
            val bottom = w * BOTTOM_FR
            val photoSize = w - side * 2f
            val height = photoSize + side + bottom
            val photo = FRect(side, side, side + photoSize, side + photoSize)
            val footerTop = photo.bottom
            val footerH = height - footerTop

            val title = FRect(
                left = side * 1.5f,
                top = footerTop + footerH * 0.08f,
                right = w * TITLE_RIGHT_FR,
                bottom = footerTop + footerH * 0.66f,
            )
            val stamp = FRect(
                left = w * STAMP_LEFT_FR,
                top = footerTop + footerH * 0.17f,
                right = w - side * 1.5f,
                bottom = footerTop + footerH * 0.64f,
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
                titleSize = footerH * 0.205f,
                stampSize = footerH * 0.205f,
                watermarkSize = w * 0.024f,
            )
        }

        /** 数码相机：全幅无边照片 + 底部 OSD 信息带（屏幕即卡面） */
        private fun digitalLayout(w: Float): CardLayout {
            val height = w * ASPECT
            val bandH = w * 0.22f
            val bandTop = height - bandH
            val photo = FRect(0f, 0f, w, bandTop)

            val title = FRect(
                left = w * 0.05f,
                top = bandTop + bandH * 0.10f,
                right = w * 0.58f,
                bottom = bandTop + bandH * 0.48f,
            )
            val stamp = FRect(
                left = w * 0.60f,
                top = bandTop + bandH * 0.10f,
                right = w * 0.95f,
                bottom = bandTop + bandH * 0.48f,
            )
            val watermark = FRect(
                left = w * 0.05f,
                top = bandTop + bandH * 0.58f,
                right = w * 0.95f,
                bottom = height - bandH * 0.06f,
            )
            return CardLayout(
                width = w,
                height = height,
                photo = photo,
                title = title,
                stamp = stamp,
                watermark = watermark,
                titleSize = bandH * 0.30f,
                stampSize = bandH * 0.30f,
                watermarkSize = w * 0.024f,
            )
        }

        /** 胶片：上下齿孔带 + 3:2 片格 + 片基信息带 */
        private fun filmLayout(w: Float): CardLayout {
            val sprocketH = w * FILM_SPROCKET_FR
            val photoH = w / FILM_PHOTO_RATIO
            val infoH = w * FILM_INFO_FR
            val height = sprocketH * 2f + photoH + infoH

            val photo = FRect(0f, sprocketH, w, sprocketH + photoH)
            val bottomSprocketTop = photo.bottom
            val infoTop = bottomSprocketTop + sprocketH

            val title = FRect(
                left = w * 0.05f,
                top = infoTop + infoH * 0.12f,
                right = w * 0.56f,
                bottom = infoTop + infoH * 0.55f,
            )
            val stamp = FRect(
                left = w * 0.58f,
                top = infoTop + infoH * 0.12f,
                right = w * 0.95f,
                bottom = infoTop + infoH * 0.55f,
            )
            val watermark = FRect(
                left = w * 0.05f,
                top = infoTop + infoH * 0.60f,
                right = w * 0.95f,
                bottom = infoTop + infoH * 0.94f,
            )
            return CardLayout(
                width = w,
                height = height,
                photo = photo,
                title = title,
                stamp = stamp,
                watermark = watermark,
                titleSize = infoH * 0.34f,
                stampSize = infoH * 0.34f,
                watermarkSize = w * 0.026f,
                sprocketTop = FRect(0f, 0f, w, sprocketH),
                sprocketBottom = FRect(0f, bottomSprocketTop, w, infoTop),
            )
        }

        /**
         * 齿孔位（纯数学，两端渲染器共用）：14 孔等距，孔高为齿孔带的 42%。
         * 返回空列表表示该模式没有齿孔带。
         */
        fun sprocketHoles(layout: CardLayout): List<FRect> {
            val bands = listOfNotNull(layout.sprocketTop, layout.sprocketBottom)
            if (bands.isEmpty()) return emptyList()
            val holes = ArrayList<FRect>(28)
            for (band in bands) {
                val pitch = band.width / 14f
                val holeW = pitch * 0.5f
                val holeH = band.height * 0.42f
                val holeTop = band.centerY - holeH / 2f
                for (i in 0 until 13) {
                    val left = band.left + pitch * 0.25f + i * pitch
                    holes.add(FRect(left, holeTop, left + holeW, holeTop + holeH))
                }
            }
            return holes
        }
    }
}
