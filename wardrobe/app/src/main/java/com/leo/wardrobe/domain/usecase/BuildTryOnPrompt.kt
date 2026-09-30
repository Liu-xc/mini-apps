package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item

/**
 * it-077 · AI 试衣提示词组装：W7 生成 sheet 与顾问对话流工具共用。
 * 纯函数可单测；单品行复用 [BuildOutfitPrompt.itemLine] 的口径（品类：颜色 名称（描述））。
 */
object BuildTryOnPrompt {

    /**
     * @param items 参与穿搭的单品（参考图按此顺序拼装，文案同步列举）
     * @param scene 场景描述（空 = 不限场景）；对话流可传顾问总结的场景/风格 hint
     * @param personNote 生图文案的「人物描述」（PrefsStore 全局记忆，可为空）
     */
    fun build(items: List<Item>, scene: String, personNote: String): String = buildString {
        append("让第一张参考图（人物）换上后续参考图中的衣物，生成一张真人全身穿搭效果图。")
        if (items.isNotEmpty()) {
            append("\n衣物清单：")
            append(items.joinToString("；") { BuildOutfitPrompt().itemLine(it) })
            append("。")
        }
        append("\n严格保持每件衣物的版型、颜色、图案与材质细节和参考图一致，不要替换或混入清单之外的衣物。")
        if (personNote.isNotBlank()) append("\n人物描述：").append(personNote)
        if (scene.isNotBlank()) append("\n场景：").append(scene)
        append("\n构图：全身出镜、姿态自然、光照真实。")
    }
}
