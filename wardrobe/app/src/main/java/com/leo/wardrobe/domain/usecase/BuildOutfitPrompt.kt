package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item

/** Prompt 维度预设（it-002）：表单式单选，可不选；定义见 specs/03-data-model.md */
data class PromptDimension(val key: String, val label: String, val options: List<String>)

object PromptPresets {
    val SCENE = PromptDimension("scene", "场景", listOf("城市街头", "办公室", "咖啡馆", "公园", "海边", "居家", "商场", "校园"))
    val MOOD = PromptDimension("mood", "氛围", listOf("通勤简约", "休闲随性", "优雅正式", "街头潮流", "温柔知性", "度假轻松", "运动活力"))
    val SEASON = PromptDimension("season", "季节", listOf("早春", "春", "夏", "早秋", "秋", "冬"))
    val LIGHT = PromptDimension("light", "光线", listOf("自然日光", "黄昏暖光", "夜晚霓虹", "阴天柔光", "室内灯光"))
    val SHOT = PromptDimension("shot", "构图", listOf("全身照", "半身照", "全身+环境远景", "街拍视角"))

    val dimensions = listOf(SCENE, MOOD, SEASON, LIGHT, SHOT)
}

/** 文案模板（策略模式，可整体替换） */
fun interface PromptTemplate {
    fun render(
        dimensionLine: String,
        personNote: String,
        itemLines: List<String>,
        includeItems: Boolean,
    ): String
}

object DefaultPromptTemplate : PromptTemplate {
    override fun render(
        dimensionLine: String,
        personNote: String,
        itemLines: List<String>,
        includeItems: Boolean,
    ): String = buildString {
        append("请根据这张长图生成一张真人穿搭效果图：长图顶部为生成要求，下方按人体位置摆放服装单品（各格底部标注品类）。")
        if (dimensionLine.isNotBlank()) append('\n').append(dimensionLine).append('。')
        if (personNote.isNotBlank()) append("\n人物描述：").append(personNote)
        if (includeItems && itemLines.isNotEmpty()) {
            append("\n单品：").append(itemLines.joinToString("；"))
        }
        append("\n要求：画面包含完整的人物穿搭与环境场景信息，保持每件单品的颜色与款式一致，自然真实。")
    }
}

/**
 * 生图文案组装（it-002 重构）：
 * 维度选择 + 人物描述 → 引导句；includeItems 控制是否附带单品清单
 * （长图通道不需要清单，照片标签已承载；纯文本通道附带）。
 */
class BuildOutfitPrompt(private val template: PromptTemplate = DefaultPromptTemplate) {

    /** 单品行：如「上装：白色 宽松棉质牛津衬衫」 */
    fun itemLine(item: Item): String = buildString {
        append(item.category.label).append("：")
        if (item.color.isNotBlank()) append(item.color).append(' ')
        append(item.name)
        if (item.desc.isNotBlank()) append("（").append(item.desc).append("）")
    }

    /** 维度行：如「场景：城市街头；氛围：通勤简约」（只含已选维度，按预设顺序） */
    fun dimensionLine(selections: Map<String, String>): String =
        PromptPresets.dimensions.mapNotNull { d ->
            selections[d.key]?.takeIf { it.isNotBlank() }?.let { "${d.label}：$it" }
        }.joinToString("；")

    operator fun invoke(
        items: List<Item>,
        selections: Map<String, String> = emptyMap(),
        personNote: String = "",
        includeItems: Boolean = true,
    ): String = template.render(
        dimensionLine = dimensionLine(selections),
        personNote = personNote,
        itemLines = items.map(::itemLine),
        includeItems = includeItems,
    )
}
