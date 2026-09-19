package com.leo.wardrobe.domain.model

/**
 * 品类枚举（specs/03-data-model.md），ordinal 即搭配页槽位顺序。
 */
enum class WardrobeCategory(val label: String) {
    TOP("上装"),
    OUTERWEAR("外套"),
    BOTTOM("下装"),
    DRESS("连衣裙"),
    SHOES("鞋"),
    BAG("包"),
    HAT("帽子"),
    ACCESSORY("其他配饰");

    companion object {
        fun fromLabel(label: String): WardrobeCategory =
            entries.firstOrNull { it.label == label } ?: ACCESSORY
    }
}
