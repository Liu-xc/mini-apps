package com.leo.wardrobe.ui.chat

import com.leo.libs.agent.tool.ToolRegistry
import com.leo.libs.agent.tool.ToolResult
import com.leo.libs.agent.tool.jsonSchema
import com.leo.libs.agent.tool.toolRegistry
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.model.itemsOf
import com.leo.wardrobe.domain.model.outfitsContaining
import com.leo.wardrobe.domain.model.outfitsOf
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * it-041 US-41b：注册给模型的只读衣橱工具——全部从 repo 快照读（零写路径），
 * 数据经工具结果回喂进模型上下文（与导出长图给生图 Agent 同性质）。
 */
fun wardrobeTools(
    data: () -> WardrobeData,
    personId: () -> String?,
): ToolRegistry = toolRegistry {

    tool(
        name = "search_items",
        description = "按品类/关键词/标签搜索当前角色的衣橱单品，返回精简列表",
        parameters = jsonSchema {
            string("category", "品类：上装/外套/下装/连衣裙/鞋/包/帽子/其他配饰", required = false)
            string("keyword", "名称、颜色或描述里的关键词", required = false)
            string("tag", "标签，如 通勤/休闲", required = false)
        },
    ) { args ->
        val pid = personId() ?: return@tool ToolResult.error("当前没有角色")
        val category = args["category"]?.jsonPrimitive?.contentOrNull
            ?.let { v -> WardrobeCategory.entries.find { it.label == v || it.name.equals(v, true) } }
        val keyword = args["keyword"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val tag = args["tag"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

        val hits = data().itemsOf(pid)
            .asSequence()
            .filter { category == null || it.category == category }
            .filter { tag.isEmpty() || tag in it.tags }
            .filter {
                keyword.isEmpty() ||
                    it.name.contains(keyword, true) ||
                    it.color.contains(keyword, true) ||
                    it.desc.contains(keyword, true)
            }
            .toList()
        if (hits.isEmpty()) ToolResult.ok("没有匹配的单品（可换条件再查）")
        else ToolResult.ok(
            hits.take(20).joinToString("\n") { item ->
                buildString {
                    append("· ${item.name}（${item.category.label}，${item.color.ifBlank { "未记颜色" }}")
                    if (item.tags.isNotEmpty()) append("，标签：${item.tags.joinToString("/")}")
                    append("）")
                }
            } + if (hits.size > 20) "\n…共 ${hits.size} 件" else "\n共 ${hits.size} 件",
        )
    }

    tool(
        name = "search_outfits",
        description = "查询已保存的穿搭；可按关键词（标签/单品名）或指定单品反查",
        parameters = jsonSchema {
            string("keyword", "标签或单品名关键词", required = false)
            string("itemId", "只看包含该单品 id 的穿搭", required = false)
        },
    ) { args ->
        val pid = personId() ?: return@tool ToolResult.error("当前没有角色")
        val keyword = args["keyword"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val itemId = args["itemId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val snapshot = data()

        var hits = snapshot.outfitsOf(pid)
        if (itemId.isNotEmpty()) hits = hits.filter { itemId in it.itemIds }
        if (keyword.isNotEmpty()) {
            hits = hits.filter { outfit ->
                outfit.tags.any { it.contains(keyword, true) } ||
                    outfit.itemIds.any { id ->
                        snapshot.items.find { it.id == id }
                            ?.let { it.name.contains(keyword, true) || it.color.contains(keyword, true) } == true
                    }
            }
        }
        if (hits.isEmpty()) ToolResult.ok("没有匹配的穿搭")
        else ToolResult.ok(
            hits.sortedByDescending { it.updatedAt }.take(10).joinToString("\n") { outfit ->
                val names = outfit.itemIds.mapNotNull { id -> snapshot.items.find { it.id == id }?.name }
                val done = if (outfit.effectImages.isNotEmpty()) "有成品图" else "无成品图"
                "· 穿搭[${names.joinToString(" + ").ifBlank { "空" }}]（${done}" +
                    (if (outfit.tags.isNotEmpty()) "，标签：${outfit.tags.joinToString("/")}" else "") + "）"
            },
        )
    }

    tool(
        name = "wear_stats",
        description = "穿着统计：总打卡、近30天出勤、闲置（≥90天未穿或从未穿）单品清单",
        parameters = jsonSchema {},
    ) { _ ->
        val pid = personId() ?: return@tool ToolResult.error("当前没有角色")
        val snapshot = data()
        val logs = snapshot.wearLogs.filter { it.personId == pid }
        val now = System.currentTimeMillis()
        val recent30 = logs.count { now - it.at <= 30L * 24 * 3600 * 1000 }
        val wornItemIds = snapshot.outfits.filter { o ->
            o.personId == pid && logs.any { it.outfitId == o.id }
        }.flatMap { it.itemIds }.toSet()
        val idle = snapshot.itemsOf(pid).filter { it.id !in wornItemIds }
        val idleNamed = idle.take(10).joinToString("、") { it.name }
        ToolResult.ok(
            buildString {
                append("总打卡 ${logs.size} 次；近 30 天 ${recent30} 次。")
                append("衣橱共 ${snapshot.itemsOf(pid).size} 件，从未进入已穿穿搭的 ${idle.size} 件")
                if (idleNamed.isNotEmpty()) append("：$idleNamed")
                if (idle.size > 10) append(" 等")
                append("。")
            },
        )
    }

    tool(
        name = "current_person",
        description = "查看当前角色名与形象参考照是否已设置",
        parameters = jsonSchema {},
    ) { _ ->
        val snapshot = data()
        val pid = personId() ?: return@tool ToolResult.error("当前没有角色")
        val person = snapshot.persons.find { it.id == pid } ?: return@tool ToolResult.error("角色不存在")
        ToolResult.ok(
            "当前角色：${person.name}；形象参考照：${if (person.refImageFile != null) "已设置" else "未设置"}",
        )
    }
}
