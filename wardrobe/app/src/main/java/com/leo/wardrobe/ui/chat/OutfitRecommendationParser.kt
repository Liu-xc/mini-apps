package com.leo.wardrobe.ui.chat

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import com.leo.wardrobe.domain.usecase.PromptPresets

/**
 * 顾问回复里的「可视化穿搭协议」（it-054）。
 *
 * 这里只做纯文本解析，不访问衣橱、不写数据，也不决定单品是否真实存在；
 * UI 层再用当前角色的 Item 列表做精确匹配，避免把模型臆造的单品当成衣橱事实。
 */
data class OutfitRecommendationItem(
    val categoryLabel: String,
    val itemName: String,
)

data class OutfitRecommendation(
    val title: String,
    val items: List<OutfitRecommendationItem>,
    val detailMarkdown: String,
)

sealed interface AssistantReplyPart {
    data class Markdown(val text: String) : AssistantReplyPart
    data class Outfit(val value: OutfitRecommendation) : AssistantReplyPart
}

data class ParsedAssistantReply(val parts: List<AssistantReplyPart>) {
    val recommendations: List<OutfitRecommendation>
        get() = parts.filterIsInstance<AssistantReplyPart.Outfit>().map { it.value }
}

fun parseAssistantReply(markdown: String): ParsedAssistantReply {
    if (markdown.isBlank()) return ParsedAssistantReply(emptyList())

    val parts = mutableListOf<AssistantReplyPart>()
    val markdownBuffer = mutableListOf<String>()
    var candidate: Candidate? = null

    fun flushMarkdown() {
        val text = markdownBuffer.joinToString("\n").trim()
        if (text.isNotBlank()) parts += AssistantReplyPart.Markdown(text)
        markdownBuffer.clear()
    }

    fun flushCandidate() {
        val current = candidate ?: return
        candidate = null
        if (current.items.isEmpty()) {
            markdownBuffer += current.rawLines
            return
        }

        flushMarkdown()
        parts += AssistantReplyPart.Outfit(
            OutfitRecommendation(
                title = current.title,
                items = current.items.toList(),
                detailMarkdown = current.detailLines.joinToString("\n").trim(),
            ),
        )
    }

    markdown.lineSequence().forEach { line ->
        val heading = RECOMMENDATION_HEADING.matchEntire(line)?.groupValues?.get(1)
        if (heading != null) {
            flushCandidate()
            candidate = Candidate(cleanInlineMarkdown(heading))
            candidate?.rawLines?.add(line)
            return@forEach
        }

        val current = candidate
        if (current == null) {
            markdownBuffer += line
            return@forEach
        }

        val item = parseItemLine(line)
        if (item != null && current.items.size < MAX_ITEMS) {
            current.items += item
        } else {
            current.detailLines += line
        }
        current.rawLines += line
    }
    flushCandidate()
    flushMarkdown()

    return ParsedAssistantReply(parts)
}

private class Candidate(val title: String) {
    val items = mutableListOf<OutfitRecommendationItem>()
    val detailLines = mutableListOf<String>()
    val rawLines = mutableListOf<String>()
}

private const val MAX_ITEMS = 8

private val RECOMMENDATION_HEADING = Regex(
    "^##\\s+((?:第[一二三四五六七八九十百\\d]+套|方案\\s*[一二三四五六七八九十\\d]+|穿搭\\s*[一二三四五六七八九十\\d]*).*)$",
)

private val ITEM_LINE = Regex("^\\s*[-*]\\s+([^：:]+)[：:](.+?)\\s*$")

private fun parseItemLine(line: String): OutfitRecommendationItem? {
    val match = ITEM_LINE.matchEntire(line) ?: return null
    val category = cleanInlineMarkdown(match.groupValues[1]).trim()
    val itemName = cleanInlineMarkdown(match.groupValues[2]).trim().trimEnd('。', '；', ';')
    if (category.isBlank() || itemName.isBlank()) return null
    if (WardrobeCategory.entries.none { it.label == category }) return null
    return OutfitRecommendationItem(category, itemName)
}

/** 去掉卡片协议里允许出现的行内强调，保留用户看到的真实单品名。 */
fun cleanInlineMarkdown(value: String): String = value
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("(`{1,3}|\\*{1,3}|_{1,3})"), "")
    .trim()

/**
 * it-055：协议单品 → 当前角色衣橱的匹配真源（UI 层与测试共用）。
 *
 * 对不上的单品保留原文、第二项为 null，由 UI 决定灰显与不可点；
 * 不把模型虚构的名称伪装成可导航的本地条目。
 */
fun matchRecommendationItems(
    recommendation: OutfitRecommendation,
    wardrobeItems: List<Item>,
): List<Pair<OutfitRecommendationItem, Item?>> = recommendation.items.map { line ->
    line to wardrobeItems.firstOrNull { normalizeName(it.name) == normalizeName(line.itemName) }
}

private fun normalizeName(value: String): String = value
    .replace(Regex("\\s+"), "")
    .trim()

/**
 * it-056/it-057：从推荐标题与说明提取导出面板「画面设定」五维的预选值。
 *
 * system prompt 要求每套方案以「## 第 X 套 · 场景」开头，场景信息天然在标题里，
 * 「适合/理由」里还常写场合与季节。规则：
 * - 场景：标题短语或全文含预设选项则选预设（长词在前），否则回填标题「·」后
 *   的场景短语原文（≤8 字，超长截断）——AI 的自由措辞不被 8 个枚举丢弃；
 * - 氛围：放宽到选项的 ≥2 字前缀（「休闲」→「休闲随性」「通勤」→「通勤简约」）；
 * - 季节/光线/构图：维持完整枚举匹配（理由文本噪声大，不放宽）。
 * 预选只是面板初值，用户可一键改掉或取消，故不做否定句等语义级甄别。
 */
fun extractRecommendationSelections(recommendation: OutfitRecommendation): Map<String, String> {
    val text = "${recommendation.title}\n${recommendation.detailMarkdown}"
    val phrase = scenePhrase(recommendation.title)
    return PromptPresets.dimensions.mapNotNull { dim ->
        val value = when (dim.key) {
            PromptPresets.SCENE.key ->
                dim.options.firstOrNull { (phrase ?: "").contains(it) || text.contains(it) }
                    ?: phrase?.take(FREE_SCENE_MAX)
            PromptPresets.MOOD.key -> relaxedOption(text, dim.options)
            else -> dim.options.firstOrNull { text.contains(it) }
        }
        value?.let { dim.key to it }
    }.toMap()
}

/** 标题「·」后的场景短语（去掉空白）；缺失或只剩「第 X 套」序号时为 null */
private fun scenePhrase(title: String): String? {
    val after = title.substringAfter('·', "").trim()
    return after.takeIf { it.isNotBlank() && !SET_NO.matches(it) }
}

/** it-057 氛围放宽：选项的 ≥2 字前缀出现在文本即命中，取前缀最长者 */
private fun relaxedOption(text: String, options: List<String>): String? =
    options.mapNotNull { option ->
        (2..option.length)
            .map { option.substring(0, it) }
            .filter { text.contains(it) }
            .maxByOrNull { it.length }
            ?.let { hit -> hit to option }
    }.maxByOrNull { it.first.length }?.second

private const val FREE_SCENE_MAX = 8

private val SET_NO = Regex("^第[一二三四五六七八九十百\\d]+套$")
