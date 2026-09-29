package com.leo.darkroom.card

import androidx.compose.runtime.Immutable

/**
 * 相纸框型（it-010 US-16）：仅拍立得模式可选——数码/胶片的卡面是机身与片基，
 * 没有相纸概念。配色真源在 [CardPalette.forFrame]。
 */
enum class FrameStyle(val label: String) {
    CLASSIC("经典白"),
    CREAM("暖米"),
    NOIR("墨框"),
    SLATE("灰板"),
    ;

    companion object {
        fun fromName(name: String?): FrameStyle =
            entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

/** 作品标题字体（it-010 US-17）：衬线=编辑式默认，黑体=现代直给 */
enum class TitleFontStyle(val label: String) {
    SERIF("衬线"),
    SANS("黑体"),
    ;

    companion object {
        fun fromName(name: String?): TitleFontStyle =
            entries.firstOrNull { it.name == name } ?: SERIF
    }
}

/** 作品标题字号（it-010 US-17）：对 [CardLayout.titleSize] 的比例，限幅防溢出 */
enum class TitleSizeOption(val label: String, val scale: Float) {
    SMALL("小", 0.85f),
    REGULAR("标准", 1.0f),
    LARGE("大", 1.16f),
    ;

    companion object {
        fun fromName(name: String?): TitleSizeOption =
            entries.firstOrNull { it.name == name } ?: REGULAR
    }
}

/**
 * 成片卡片的结构化字段（it-001 US-4，it-010 扩为创作选项组合）——
 * 预览、位图、视频由同一 spec 驱动。
 */
@Immutable
data class CardSpec(
    /** 作品标题（可空，空则只留日期） */
    val title: String = "",
    /** 日期章文案，拍立得风格「1988 07 21」式（yyyy MM dd，可改任意日期） */
    val dateText: String,
    /** 卡脚脚注（it-010 替代原水印开关）：自定义文案，空串 = 不印 */
    val footer: String = DEFAULT_FOOTER,
    /** 相纸框型（仅拍立得生效） */
    val frame: FrameStyle = FrameStyle.CLASSIC,
    /** 标题字体 */
    val titleFont: TitleFontStyle = TitleFontStyle.SERIF,
    /** 标题字号 */
    val titleSize: TitleSizeOption = TitleSizeOption.REGULAR,
) {
    companion object {
        const val DEFAULT_FOOTER = "显影 DARKROOM"

        fun today(): String = TODAY_FORMAT.get().format(java.util.Date())

        // SimpleDateFormat 非线程安全，ThreadLocal 隔离
        private val TODAY_FORMAT = object : ThreadLocal<java.text.SimpleDateFormat>() {
            override fun initialValue() =
                java.text.SimpleDateFormat("yyyy MM dd", java.util.Locale.US)
        }
    }
}
