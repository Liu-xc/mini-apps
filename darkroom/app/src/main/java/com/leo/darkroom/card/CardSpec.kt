package com.leo.darkroom.card

import androidx.compose.runtime.Immutable

/**
 * 成片卡片的结构化字段（it-001 US-4）——预览、位图、视频由同一 spec 驱动。
 */
@Immutable
data class CardSpec(
    /** 作品标题（可空，空则只留日期） */
    val title: String = "",
    /** 日期章文案，拍立得风格「1988 07 21」式（yyyy MM dd，可改任意日期） */
    val dateText: String,
    /** 卡脚水印「显影 DARKROOM」 */
    val showWatermark: Boolean = true,
) {
    companion object {
        fun today(): String = TODAY_FORMAT.get().format(java.util.Date())

        // SimpleDateFormat 非线程安全，ThreadLocal 隔离
        private val TODAY_FORMAT = object : ThreadLocal<java.text.SimpleDateFormat>() {
            override fun initialValue() =
                java.text.SimpleDateFormat("yyyy MM dd", java.util.Locale.US)
        }
    }
}
