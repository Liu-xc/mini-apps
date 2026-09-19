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

/** 堂食/外卖/自做三类统一为 Place 一个实体（ADR-007） */
@Serializable
enum class PlaceKind { RESTAURANT, TAKEOUT, HOME }

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
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val located: Boolean get() = location != null
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
