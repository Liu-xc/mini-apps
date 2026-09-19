package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item

/**
 * 生图文案（策略模式，specs/04-architecture.md）：模板可整体替换。
 * 输入为当前选中的单品（按槽位顺序）与穿搭风格标签。
 */
fun interface PromptTemplate {
    fun render(lines: List<String>, styleTags: List<String>): String
}

object DefaultPromptTemplate : PromptTemplate {
    override fun render(lines: List<String>, styleTags: List<String>): String = buildString {
        append("请根据以下服装单品生成一张真人穿搭效果图")
        if (styleTags.isNotEmpty()) append("（").append(styleTags.joinToString("·")).append("风格）")
        append("：\n")
        lines.forEach { append("- ").append(it).append('\n') }
        append("单品实物图见附图，请保持颜色与款式一致，生成自然站姿的全身穿搭照。")
    }
}

class BuildOutfitPrompt(private val template: PromptTemplate = DefaultPromptTemplate) {
    /** 单品行：如「上装：白色 宽松棉质牛津衬衫」 */
    fun itemLine(item: Item): String = buildString {
        append(item.category.label).append("：")
        if (item.color.isNotBlank()) append(item.color).append(' ')
        append(item.name)
        if (item.desc.isNotBlank()) append("（").append(item.desc).append("）")
    }

    operator fun invoke(items: List<Item>, styleTags: List<String> = emptyList()): String =
        template.render(items.map(::itemLine), styleTags)
}
