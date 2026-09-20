package com.leo.wardrobe.data.mock

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent
import com.leo.wardrobe.domain.model.Outfit
import com.leo.wardrobe.domain.model.Person
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData

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

        fun outfit(id: String, personId: String, itemIds: List<String>, tags: List<String>, createdAgo: Int) =
            Outfit(id, personId, itemIds, tags, effectImages = emptyList(), createdAt = ago(createdAgo), updatedAt = ago(createdAgo))

        val outfits = listOf(
            outfit("o1", P1, listOf("it1", "it4", "it6", "it10", "it12"), listOf("通勤", "早秋"), 30),
            outfit("o2", P1, listOf("it2", "it7", "it11"), listOf("运动"), 25),
            outfit("o3", P1, listOf("it5", "it9", "it10"), listOf("休闲", "度假"), 20),
            outfit("o4", P2, listOf("it13", "it14", "it16"), listOf("通勤"), 15),
            outfit("o5", P2, listOf("it15", "it17"), listOf("约会", "度假"), 10),
        )

        val notes = listOf(
            Note("n1", NoteParent.ITEM, "it1", "领口有点松，洗完要平铺晾", ago(12)),
            Note("n2", NoteParent.OUTFIT, "o1", "周一穿着开会，舒服得体", ago(8)),
            Note("n3", NoteParent.OUTFIT, "o3", "海边度假穿这套出片", ago(5)),
        )

        return WardrobeData(persons = persons, items = items, outfits = outfits, notes = notes)
    }
}
