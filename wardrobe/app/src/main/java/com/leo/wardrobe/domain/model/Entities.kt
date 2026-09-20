package com.leo.wardrobe.domain.model

import kotlinx.serialization.Serializable

/** 品类标签预设（specs/03-data-model.md）：仅影响输入体验，数据里标签是普通字符串。 */
object TagPresets {
    val style = listOf("通勤", "休闲", "运动", "约会", "度假", "正式", "简约", "复古")
    val season = listOf("春", "夏", "秋", "冬", "早春", "早秋")
    val occasion = listOf("上班", "出游", "居家", "聚会")

    /** 表单里快速点选的顺序 */
    val quickPicks: List<String> = style + season + occasion
}

fun newId(): String = java.util.UUID.randomUUID().toString()

/** it-019：混入搭配槽位的愿望单品 id 前缀（伪 Item，不落 Outfit/wearLogs） */
const val WISH_SLOT_PREFIX = "wish:"

/** 槽位成员是否为愿望单品（伪 Item id 判定） */
val Item.isWishSlot: Boolean get() = id.startsWith(WISH_SLOT_PREFIX)

@Serializable
data class Person(
    val id: String,
    val name: String,
    val emoji: String = "🙂",
    /** it-017 形象参考照（可选，全身照/头像均可）：导出长图顶部附给生图 Agent */
    val refImageFile: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class Item(
    val id: String,
    val personId: String,
    val category: WardrobeCategory,
    val name: String,
    val color: String = "",
    val desc: String = "",
    /** 相对 images/ 目录的文件名 */
    val imageFile: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class OutfitImage(
    val file: String,
    val addedAt: Long = 0L,
)

@Serializable
data class Outfit(
    val id: String,
    val personId: String,
    val itemIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val effectImages: List<OutfitImage> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
enum class NoteParent { ITEM, OUTFIT }

@Serializable
data class Note(
    val id: String,
    val parentType: NoteParent,
    val parentId: String,
    val text: String,
    val createdAt: Long = 0L,
)

/** 穿搭打卡（it-018 阶段A）：某套穿搭在某个时刻真的上身了；同日多套允许（换装） */
@Serializable
data class WearLog(
    val id: String,
    val personId: String,
    val outfitId: String,
    /** 穿着时刻（默认打卡时刻，可改当日） */
    val at: Long,
    val createdAt: Long = 0L,
)

/**
 * 想买单品（it-019，ADR-018）：种草了还没买的衣服/鞋/包。
 * 与正式 Item 的差别：照片可选（商品截图即可）、有购买链接与价格、购入后转正回链。
 */
@Serializable
data class WishItem(
    val id: String,
    val personId: String,
    val category: WardrobeCategory,
    val name: String,
    val color: String = "",
    val desc: String = "",
    /** 心理价位/标价（元），可空 */
    val price: Double? = null,
    /** 商品链接（淘宝/京东/得物分享链接等），展示域名 + ACTION_VIEW */
    val url: String = "",
    /** 商品图文件名（相对 images/，可选——无图用品类占位插画） */
    val imageFile: String? = null,
    val tags: List<String> = emptyList(),
    /** 购入时间；null = 未购 */
    val purchasedAt: Long? = null,
    /** 转正后指向的正式 Item id（留档回溯） */
    val purchasedItemId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val purchased: Boolean get() = purchasedAt != null

    /**
     * 槽位适配（it-019）：以 [WISH_SLOT_PREFIX] 前缀的伪 Item 混入 W1 槽位 pager，
     * 下游（长图/文案/拼贴）统一按 Item 处理、以 isWishSlot 判定；id 剥前缀即愿望单品 id。
     */
    fun asSlotItem(): Item = Item(
        id = WISH_SLOT_PREFIX + id,
        personId = personId,
        category = category,
        name = name,
        color = color,
        desc = desc,
        imageFile = imageFile.orEmpty(),
        tags = tags,
    )
}

/**
 * 心愿穿搭（it-019，ADR-018）：含愿望单品的组合，可存盘反复预览、逐件买齐后一键升级为正式 Outfit。
 * 不变量：wishItemIds 至少一件（纯已有件组合应存正式 Outfit）。
 */
@Serializable
data class WishOutfit(
    val id: String,
    val personId: String,
    /** 已有单品部分 */
    val itemIds: List<String> = emptyList(),
    /** 愿望单品部分 */
    val wishItemIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** 上身预览效果图（生图回录，值对象与 OutfitImage 同构） */
    val previewImages: List<OutfitImage> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** 数据快照根（SSOT），即 wardrobe.json 的结构 */
@Serializable
data class WardrobeData(
    val schemaVersion: Int = 1,
    val persons: List<Person> = emptyList(),
    val items: List<Item> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
    val notes: List<Note> = emptyList(),
    /** it-018：旧 JSON 缺字段反序列化为空表，向后兼容不 bump schemaVersion */
    val wearLogs: List<WearLog> = emptyList(),
    /** it-019：心愿域（想买单品 + 心愿穿搭），同上向后兼容 */
    val wishItems: List<WishItem> = emptyList(),
    val wishOutfits: List<WishOutfit> = emptyList(),
) {
    val isEmpty: Boolean get() = persons.isEmpty() && items.isEmpty() && outfits.isEmpty()
}
