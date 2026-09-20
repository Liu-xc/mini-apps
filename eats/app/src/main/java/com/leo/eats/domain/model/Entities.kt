package com.leo.eats.domain.model

import kotlinx.serialization.Serializable

/** 标签预设（specs/03-data-model.md）：仅影响输入体验，数据里标签是普通字符串。 */
object TagPresets {
    val flavor = listOf("辣", "清淡", "香菜", "重油", "甜")
    val cuisine = listOf("火锅", "烧烤", "日料", "快餐", "面食", "家常菜")
    val scene = listOf("聚餐", "一人食", "夜宵", "加班")

    /** 表单里快速点选的顺序 */
    val quickPicks: List<String> = flavor + cuisine + scene
}

fun newId(): String = java.util.UUID.randomUUID().toString()

/** 堂食/外卖/自做三类统一为 Place 一个实体（ADR-007）；kind 语义泛化见 ADR-012（显示文案按分类适配） */
@Serializable
enum class PlaceKind { RESTAURANT, TAKEOUT, HOME }

/**
 * 一级分类：吃/喝/玩（it-008，ADR-012）。
 * kind 从「怎么吃」泛化为「在哪进行」，本枚举承担「做什么」；默认 EAT，
 * 旧 JSON 缺字段反序列化为 EAT，零迁移（同 wardrobe it-017 先例）。
 */
@Serializable
enum class PlaceCategory {
    EAT, DRINK, PLAY;

    /** 分类短名（chips / 行内「吃·堂食」组合） */
    val shortLabel: String
        get() = when (this) {
            EAT -> "吃"
            DRINK -> "喝"
            PLAY -> "玩"
        }
}

/** kind 在某分类下的显示文案：堂食/外卖/自做 → 堂食·外送·自调 → 出门/在家（PLAY 无外送） */
fun PlaceKind.labelIn(category: PlaceCategory): String = when (category) {
    PlaceCategory.EAT -> when (this) {
        PlaceKind.RESTAURANT -> "堂食"
        PlaceKind.TAKEOUT -> "外卖"
        PlaceKind.HOME -> "自做"
    }
    PlaceCategory.DRINK -> when (this) {
        PlaceKind.RESTAURANT -> "堂食"
        PlaceKind.TAKEOUT -> "外送"
        PlaceKind.HOME -> "自调"
    }
    PlaceCategory.PLAY -> when (this) {
        PlaceKind.RESTAURANT -> "出门"
        PlaceKind.TAKEOUT -> "外送"
        PlaceKind.HOME -> "在家"
    }
}

/** 某分类下 kind 的合法选项（ADR-012：PLAY+TAKEOUT 为无效组合，录入不提供） */
val PlaceCategory.kindOptions: List<PlaceKind>
    get() = when (this) {
        PlaceCategory.PLAY -> listOf(PlaceKind.RESTAURANT, PlaceKind.HOME)
        else -> PlaceKind.entries.toList()
    }

@Serializable
data class GeoLoc(val lat: Double, val lng: Double)

@Serializable
data class PlaceLink(val url: String, val label: String = "") {
    /** 来源徽标展示期派生（ADR-009），不落盘 */
    val source: LinkSource get() = LinkSource.detect(url)
}

/**
 * 链接来源识别（ADR-009）：域名映射常量，纯函数可 JVM 单测。
 * 识别规则升级后旧数据直接受益。
 */
enum class LinkSource(val label: String) {
    MEITUAN("美团"),
    DIANPING("大众点评"),
    OTHER("链接"),
    ;

    companion object {
        private val MEITUAN_HOSTS = listOf("meituan.com", "meituan.net")
        private val DIANPING_HOSTS = listOf("dianping.com", "dianping.cn", "meishi.com")

        fun detect(url: String): LinkSource {
            val u = url.trim().lowercase()
            return when {
                MEITUAN_HOSTS.any { it in u } -> MEITUAN
                DIANPING_HOSTS.any { it in u } -> DIANPING
                else -> OTHER
            }
        }
    }
}

@Serializable
data class Place(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val cuisine: String = "",
    val location: GeoLoc? = null,
    val address: String = "",
    /** 主观综合评分 1–5，可空 */
    val rating: Int? = null,
    val tags: List<String> = emptyList(),
    /** 门面/菜品照片文件名（相对 images/） */
    val photos: List<String> = emptyList(),
    /** 外部链接（美团/点评分享链接等，ADR-009） */
    val links: List<PlaceLink> = emptyList(),
    val notes: String = "",
    /** 一级分类（it-008），默认吃；置于既有字段之后保证旧位置调用兼容 */
    val category: PlaceCategory = PlaceCategory.EAT,
    /** 种草时间（it-008）：非空 = 愿望条目（想去/想吃还没去），记一笔后自动拔草 */
    val wishlistedAt: Long? = null,
    /** 计划去的时间（it-008 阶段C，仅愿望条目提供入口），可空 */
    val planAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val located: Boolean get() = location != null
    val isWish: Boolean get() = wishlistedAt != null
}

@Serializable
data class Visit(
    val id: String,
    val placeId: String,
    /** 吃的时间（默认创建时刻，可改——补记昨天的吃） */
    val at: Long,
    val rating: Int? = null,
    val cost: Double? = null,
    val text: String = "",
    val photos: List<String> = emptyList(),
    /** 记录创建时间，与 at 区分 */
    val createdAt: Long = 0L,
)

/** 数据快照根（SSOT），即 eats.json 的结构 */
@Serializable
data class EatsData(
    val schemaVersion: Int = 1,
    val places: List<Place> = emptyList(),
    val visits: List<Visit> = emptyList(),
) {
    val isEmpty: Boolean get() = places.isEmpty()
}
