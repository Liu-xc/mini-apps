package com.leo.wardrobe.data.mock

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.OutfitImage
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.WearLog
import com.leo.wardrobe.domain.model.WishItem
import com.leo.wardrobe.domain.model.WishOutfit

/**
 * 演示种子数据（it-015）：两个角色（覆盖角色隔离）、17 件衣物（八品类全覆盖，
 * 照片为 assets/mock 内置 thiings 素材）、5 套组合、3 条笔记。
 * 时间按「当前时刻 − N 天」相对生成，任何时候进入演示模式数据都「新鲜」；id 全确定，便于走查断言。
 */
object MockWardrobeData {

    private const val P1 = "p1"
    private const val P2 = "p2"

    fun create(now: Long = System.currentTimeMillis()): WardrobeData {
        fun ago(days: Int) = now - days * 86_400_000L

        fun item(
            id: String, personId: String, category: WardrobeCategory, name: String,
            color: String, desc: String, imageFile: String, tags: List<String>,
            createdAgo: Int, updatedAgo: Int,
        ) = Item(
            id = id, personId = personId, category = category, name = name, color = color,
            desc = desc, imageFile = imageFile, tags = tags,
            createdAt = ago(createdAgo), updatedAt = ago(updatedAgo),
        )

        val persons = listOf(
            Person(P1, "我", "👨", createdAt = ago(60)),
            Person(P2, "小满", "👧", createdAt = ago(58)),
        )

        val items = listOf(
            item("it1", P1, WardrobeCategory.TOP, "棉质T恤", "深绿", "纯色圆领短袖", "demo_ScKZwuDUTUPRm5ljOa5KSKzyJB3BNB.webp", listOf("通勤", "简约"), createdAgo = 59, updatedAgo = 1),
            item("it2", P1, WardrobeCategory.TOP, "星球衣", "橙红", "白圈红星条纹袖", "demo_TyCXC2E7A9NTewf9im9kE67V4Mv7Rl.webp", listOf("运动"), createdAgo = 58, updatedAgo = 2),
            item("it3", P1, WardrobeCategory.TOP, "牛津纺衬衫", "浅蓝", "纽扣翻领长袖", "demo_jNFNutQXA079LwSjmfwJeFsid37vkh.webp", listOf("通勤", "早秋"), createdAgo = 57, updatedAgo = 3),
            item("it4", P1, WardrobeCategory.OUTERWEAR, "羽绒服", "黑色", "立领拉链短款", "demo_GGEzZp2bzlrsQLZ4zt9vfa6dvCCDP0.webp", listOf("冬", "通勤"), createdAgo = 56, updatedAgo = 4),
            item("it5", P1, WardrobeCategory.OUTERWEAR, "飞行夹克", "棕色", "羊羔毛领飞行员", "demo_nrcvXJr22VXWX9PBtXNZ5UrplwAfhj.webp", listOf("休闲", "早秋"), createdAgo = 55, updatedAgo = 5),
            item("it6", P1, WardrobeCategory.BOTTOM, "直筒牛仔裤", "浅蓝", "卷边直筒微弹", "demo_DULdzV3uEdmGmIzORCmUox0QGw8W1z.webp", listOf("休闲"), createdAgo = 54, updatedAgo = 6),
            item("it7", P1, WardrobeCategory.BOTTOM, "工装裤", "军绿", "多口袋直筒", "demo_fo8wyXqFVEaRo4JUQTTR1vEzfBT0OK.webp", listOf("休闲", "运动"), createdAgo = 53, updatedAgo = 7),
            item("it8", P1, WardrobeCategory.DRESS, "碎花连衣裙", "米白", "泡泡袖系带", "demo_sjnsZFCzT6VnSOqmWOg9YE53KR68pi.webp", listOf("约会"), createdAgo = 52, updatedAgo = 8),
            item("it9", P1, WardrobeCategory.DRESS, "背带裙", "丹宁蓝", "泡泡袖背带款", "demo_OndHuVEwtwfTcGKfNBaBNFawZVaKLh.webp", listOf("度假"), createdAgo = 51, updatedAgo = 9),
            item("it10", P1, WardrobeCategory.SHOES, "板鞋", "红白拼色", "藏青底轻便款", "demo_llBJrXGTGW8fvXFYtSynrQ6nWHbhKo.webp", listOf("通勤", "运动"), createdAgo = 50, updatedAgo = 10),
            item("it11", P1, WardrobeCategory.SHOES, "高帮帆布鞋", "红色", "高帮系带", "demo_rwia1MUv5NLXZz5qQFGKX7nT2gKxwA.webp", listOf("休闲"), createdAgo = 49, updatedAgo = 11),
            item("it12", P1, WardrobeCategory.BAG, "手提包", "橙棕", "银扣通勤大容量", "demo_OAYK6HWr7j9IxNH5mk3vkntjQCJnwc.webp", listOf("通勤", "简约"), createdAgo = 48, updatedAgo = 12),
            item("it13", P2, WardrobeCategory.BAG, "双肩包", "蓝色", "棕色饰边轻量", "demo_3JqmFhoX4wlSJ2F8hEg2ljSfxraUPO.webp", listOf("运动", "通勤"), createdAgo = 47, updatedAgo = 13),
            item("it14", P2, WardrobeCategory.HAT, "宽檐礼帽", "卡其", "深色帽带", "demo_R2D8r79JBRWYLJGB18GgsUjN2s4Dzb.webp", listOf("度假"), createdAgo = 46, updatedAgo = 14),
            item("it15", P2, WardrobeCategory.HAT, "渔夫帽", "米色", "软檐", "demo_xOofjqlFRH8A5h5dmCd1XPd7wNbLi7.webp", listOf("休闲", "度假"), createdAgo = 45, updatedAgo = 15),
            item("it16", P2, WardrobeCategory.ACCESSORY, "太阳镜", "黑框", "UV400", "demo_5SFKX0yeQoj1PbNM2ObWNi902QoUeO.webp", listOf("度假", "简约"), createdAgo = 44, updatedAgo = 16),
            item("it17", P2, WardrobeCategory.ACCESSORY, "心形项链", "金色", "粗链吊坠", "demo_IzpAiEPUhmyIp69cv7LNwmlPKqrltx.webp", listOf("约会"), createdAgo = 43, updatedAgo = 17),
        )

        fun outfit(
            id: String, personId: String, itemIds: List<String>, tags: List<String>, createdAgo: Int,
            effectFiles: List<String> = emptyList(),
        ) = Outfit(
            id, personId, itemIds, tags,
            // it-018：成品图种子让「年度衣橱长图」的图墙分区有数据可演示
            effectImages = effectFiles.map { OutfitImage(it, ago(createdAgo / 2)) },
            createdAt = ago(createdAgo), updatedAt = ago(createdAgo),
        )

        val outfits = listOf(
            // it-015 修订（Leo 反馈）：穿搭须符合季节常识——o1 改长袖衬衫配羽绒服（原短袖+羽绒服冲突）；
            // 上身件数不必凑满：o3 = 夹克+连衣裙两件，o5 仅帽+项链的配饰组合
            outfit("o1", P1, listOf("it3", "it4", "it6", "it10", "it12"), listOf("通勤", "秋冬"), 30,
                effectFiles = listOf("demo_ScKZwuDUTUPRm5ljOa5KSKzyJB3BNB.webp", "demo_jNFNutQXA079LwSjmfwJeFsid37vkh.webp")),
            outfit("o2", P1, listOf("it2", "it7", "it11"), listOf("运动", "夏"), 25,
                effectFiles = listOf("demo_TyCXC2E7A9NTewf9im9kE67V4Mv7Rl.webp")),
            outfit("o3", P1, listOf("it5", "it9", "it10"), listOf("休闲", "度假"), 20),
            outfit("o4", P2, listOf("it13", "it14", "it16"), listOf("通勤"), 15,
                effectFiles = listOf("demo_3JqmFhoX4wlSJ2F8hEg2ljSfxraUPO.webp")),
            outfit("o5", P2, listOf("it15", "it17"), listOf("约会", "度假"), 10),
        )

        val notes = listOf(
            Note("n1", NoteParent.ITEM, "it1", "领口有点松，洗完要平铺晾", ago(12)),
            Note("n2", NoteParent.OUTFIT, "o1", "周一穿着开会，舒服得体", ago(8)),
            Note("n3", NoteParent.OUTFIT, "o3", "海边度假穿这套出片", ago(5)),
        )

        // it-018 打卡种子：o1/o2 常穿（o1 出勤最高），o4 少量，o5/o3 挂着（o3 出闲置演示）
        fun wear(id: String, personId: String, outfitId: String, daysAgo: Int) =
            WearLog(id, personId, outfitId, ago(daysAgo), ago(daysAgo))
        val wearLogs = listOf(
            wear("w1", P1, "o1", 1), wear("w2", P1, "o1", 4), wear("w3", P1, "o1", 9),
            wear("w4", P1, "o1", 16), wear("w5", P1, "o1", 24),
            wear("w6", P1, "o2", 2), wear("w7", P1, "o2", 6), wear("w8", P1, "o2", 18),
            wear("w9", P1, "o3", 21),
            wear("w10", P2, "o4", 3), wear("w11", P2, "o4", 8),
            wear("w12", P2, "o5", 12),
        )

        // it-019 心愿种子：3 件想买（含已购 1 件演示转正留档）+ 2 套心愿穿搭（1 套已买齐可升级）
        fun wish(
            id: String, personId: String, category: WardrobeCategory, name: String,
            color: String, desc: String, price: Double?, tags: List<String>,
            createdAgo: Int, purchased: Boolean = false,
        ) = WishItem(
            id = id, personId = personId, category = category, name = name, color = color,
            desc = desc, price = price, url = "https://item.taobao.com/item.htm?id=$id",
            imageFile = null, tags = tags,
            purchasedAt = if (purchased) ago(2) else null,
            purchasedItemId = if (purchased) "it3" else null,
            createdAt = ago(createdAgo), updatedAt = ago(createdAgo),
        )
        val wishItems = listOf(
            wish("wi1", P1, WardrobeCategory.OUTERWEAR, "燕麦色双面呢大衣", "燕麦", "落肩廓形中长款", 899.0, listOf("约会", "冬"), 3),
            wish("wi2", P1, WardrobeCategory.SHOES, "切尔西靴", "黑色", "圆头粗跟短靴", 1200.0, listOf("通勤", "冬"), 1),
            wish("wi3", P1, WardrobeCategory.BAG, "帆布托特包", "米白", "大容量磁扣", 199.0, listOf("通勤"), 9, purchased = true),
        )
        val wishOutfits = listOf(
            // 约会战袍：已有连衣裙+板鞋，还差大衣（未买齐）
            WishOutfit("wo1", P1, itemIds = listOf("it8", "it10"), wishItemIds = listOf("wi1"), tags = listOf("约会", "冬"), createdAt = ago(3), updatedAt = ago(3)),
            // 通勤一套：已买齐（wi3 已购 → itemIds），可一键升级
            WishOutfit("wo2", P1, itemIds = listOf("it1", "it6", "it12"), wishItemIds = emptyList(), tags = listOf("通勤"), createdAt = ago(8), updatedAt = ago(2)),
        )

        return WardrobeData(
            persons = persons, items = items, outfits = outfits, notes = notes,
            wearLogs = wearLogs, wishItems = wishItems, wishOutfits = wishOutfits,
        )
    }
}
