package com.leo.wardrobe.export

/** 历史演示人台只是占位素材，不是可提供给生图模型的人物参考。 */
fun usablePersonReference(file: String?): String? =
    file?.takeIf { it.isNotBlank() && it.substringAfterLast('/') != "person-ref.png" }
