package com.leo.eats.data.mock

import com.leo.eats.domain.model.EatsData
import com.leo.eats.domain.model.GeoLoc
import com.leo.eats.domain.model.Place
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.model.PlaceLink
import com.leo.eats.domain.model.Visit

/**
 * 演示种子数据（it-006）：三类 Place 全覆盖、字段齐全（评分/标签/链接/坐标/笔记），
 * Visit 分布在近 60 天。时间按「当前时刻 − N 天」相对生成——任何时候进入演示模式
 * 数据都「新鲜」，适合测试体验与 AI 走查。id 全确定，便于走查断言。
 */
object MockEatsData {

    fun create(now: Long = System.currentTimeMillis()): EatsData {
        fun ago(days: Int) = now - days * 86_400_000L

        val places = listOf(
            Place(
                "pl1", "巷子深火锅", PlaceKind.RESTAURANT, "火锅",
                GeoLoc(31.2304, 121.4737), "某某路 12 号", 4,
                listOf("辣", "重口味", "聚餐"),
                links = listOf(
                    PlaceLink("https://h5.waimai.meituan.com/awp/h5/block/default/abc", "双人套餐"),
                    PlaceLink("https://m.dianping.com/shoplist/ch10/r111", "店铺主页"),
                ),
                notes = "排队严重，错峰去；毛肚和虾滑必点",
                createdAt = ago(120), updatedAt = ago(3),
            ),
            Place(
                "pl2", "老王烧烤", PlaceKind.RESTAURANT, "烧烤",
                GeoLoc(31.2271, 121.4698), "某支路 7 号", 4,
                listOf("辣", "夜宵", "聚餐"),
                links = listOf(PlaceLink("https://m.dianping.com/shop/456123", "")),
                notes = "只开晚上，烤茄子一绝",
                createdAt = ago(90), updatedAt = ago(6),
            ),
            Place(
                "pl3", "鮨 · 日料亭", PlaceKind.RESTAURANT, "日料",
                GeoLoc(31.2331, 121.4701), "某江路 5 号 2F", 5,
                listOf("日料", "清淡", "约会"),
                links = listOf(PlaceLink("https://m.dianping.com/shop/987654", "")),
                notes = "周五有板前位，提前一天订",
                createdAt = ago(30), updatedAt = ago(12),
            ),
            Place(
                "pl4", "柳州螺蛳粉", PlaceKind.RESTAURANT, "面食",
                GeoLoc(31.2277, 121.4715), "某城路 66 号", 4,
                listOf("辣", "一人食"),
                notes = "加双份腐竹，多酸笋",
                createdAt = ago(45), updatedAt = ago(10),
            ),
            Place(
                "pl5", "沙县小吃", PlaceKind.RESTAURANT, "快餐",
                GeoLoc(31.2289, 121.4752), "某南路 88 号", 3,
                listOf("清淡", "便宜"),
                notes = "蒸饺不错，馄饨一般",
                createdAt = ago(200), updatedAt = ago(7),
            ),
            Place(
                "pl6", "猪脚饭", PlaceKind.TAKEOUT, "快餐",
                GeoLoc(31.2296, 121.4768), "", 4,
                listOf("一人食"),
                links = listOf(PlaceLink("https://i.meituan.com/awp/h5/def/pig", "招牌套餐")),
                notes = "外卖 30 分钟必达，卤蛋加一个",
                createdAt = ago(80), updatedAt = ago(1),
            ),
            Place(
                "pl7", "麦当劳", PlaceKind.TAKEOUT, "快餐",
                GeoLoc(31.2318, 121.4699), "某大道 1 号", 3,
                listOf("一人食", "夜宵"),
                notes = "",
                createdAt = ago(220), updatedAt = ago(2),
            ),
            Place(
                "pl8", "川湘居", PlaceKind.TAKEOUT, "湘菜",
                GeoLoc(31.2325, 121.4741), "", 4,
                listOf("辣", "加班", "重油"),
                links = listOf(PlaceLink("https://i.meituan.com/awp/h5/def/sxcj", "小炒黄牛肉")),
                notes = "加班救命店，下饭",
                createdAt = ago(60), updatedAt = ago(4),
            ),
            Place(
                "pl9", "番茄炒蛋", PlaceKind.HOME, "家常菜", location = null, address = "", rating = null,
                tags = listOf("清淡"),
                notes = "10 分钟搞定，下饭神器",
                createdAt = ago(300), updatedAt = ago(14),
            ),
            Place(
                "pl10", "红烧排骨", PlaceKind.HOME, "家常菜", location = null, address = "", rating = 4,
                tags = listOf(),
                notes = "冰糖炒糖色是关键",
                createdAt = ago(150), updatedAt = ago(21),
            ),
            Place(
                "pl11", "香煎鸡胸藜麦饭", PlaceKind.HOME, "轻食", location = null, address = "", rating = 3,
                tags = listOf("一人食", "清淡"),
                notes = "减脂期常客",
                createdAt = ago(100), updatedAt = ago(30),
            ),
        )

        val visits = listOf(
            Visit("v1", "pl1", ago(3), 5, 128.0, "毛肚绝了，虾滑也新鲜", createdAt = ago(3)),
            Visit("v2", "pl1", ago(17), 4, 99.0, "错峰去不用排队", createdAt = ago(17)),
            Visit("v3", "pl1", ago(41), 4, 108.0, "服务比上次好", createdAt = ago(41)),
            Visit("v4", "pl2", ago(6), 5, 76.0, "烤茄子+烤韭菜，夜宵天花板", createdAt = ago(6)),
            Visit("v5", "pl2", ago(25), 4, 65.0, "人多点烤串要靠抢", createdAt = ago(25)),
            Visit("v6", "pl3", ago(12), 5, 388.0, "板前位值得，赤贝太鲜了", createdAt = ago(12)),
            Visit("v7", "pl3", ago(26), 5, 356.0, "周五人少一点", createdAt = ago(26)),
            Visit("v8", "pl4", ago(10), 4, 22.0, "腐竹双份满足", createdAt = ago(10)),
            Visit("v9", "pl4", ago(33), 4, 20.0, "汤粉比干粉好", createdAt = ago(33)),
            Visit("v10", "pl5", ago(7), 3, 15.0, "便宜大碗，快速解决", createdAt = ago(7)),
            Visit("v11", "pl6", ago(1), 4, 28.0, "深夜猪脚饭，快乐", createdAt = ago(1)),
            Visit("v12", "pl6", ago(9), 4, 26.0, "卤蛋加一个，性价比高", createdAt = ago(9)),
            Visit("v13", "pl6", ago(28), 5, 30.0, "雨天送得飞快", createdAt = ago(28)),
            Visit("v14", "pl7", ago(2), 3, 35.0, "麦麦夜宵，板烧套餐", createdAt = ago(2)),
            Visit("v15", "pl8", ago(4), 4, 42.0, "小炒黄牛肉下三碗饭", createdAt = ago(4)),
            Visit("v16", "pl8", ago(19), 4, 39.0, "加班到九点的救赎", createdAt = ago(19)),
            Visit("v17", "pl9", ago(5), 4, 8.0, "多放糖，正宗家常味", createdAt = ago(5)),
            Visit("v18", "pl10", ago(21), 4, 32.0, "糖色这次炒得刚好", createdAt = ago(21)),
            Visit("v19", "pl11", ago(35), 3, 18.0, "减脂第 N 天", createdAt = ago(35)),
        )

        return EatsData(places = places, visits = visits)
    }
}
