package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item

/**
 * it-077 · AI 试衣提示词组装：W7 生成 sheet 与顾问对话流工具共用。
 * 纯函数可单测；单品行复用 [BuildOutfitPrompt.itemLine] 的口径（品类：颜色 名称（描述））。
 */
object BuildTryOnPrompt {

    /**
     * @param items 参与穿搭的单品（合成到一张长图中，文案同步列举）
     * @param scene 场景描述（空 = 不限场景）；对话流可传顾问总结的场景/风格 hint
     * @param personNote 生图文案的「人物描述」（PrefsStore 全局记忆，可为空）
     * @param hasReferenceImage 本次请求是否随附参考长图（图生图模型）。
     *  it-083：纯文生图模型（Kolors/Z-Image 系 inputImages=0）拿不到长图，旧文案「根据输入的
     *  穿搭长图/拼贴」会引导模型画成衣架陈列——必须分支出一份不提图的文案，并显式禁止衣架/人台/平铺。
     */
    fun build(items: List<Item>, scene: String, personNote: String, hasReferenceImage: Boolean = true): String = buildString {
        if (hasReferenceImage) {
            append("请根据输入的穿搭参考长图生成一张真人全身穿搭效果图：模特从头到脚完整入镜，头部与所穿鞋子都必须出现在画面中。长图顶部文字是生成要求，下方是单品照片拼贴（可能挂在衣架上或平铺陈列）。")
            append("图中衣物的陈列方式只是参考素材：必须把衣架/人台上展示的衣物「穿到真人身上」，生成真人模特实际穿着的完整穿搭照片。")
            append("若图中含人物照片，以该人物作为模特参考，若没有人物则生成一位真人模特。")
            append("\n不要复制输入图里的文字、边框、白底卡片或拼贴排版，也不要原样输出参考长图；只输出模特实际穿着单品的完整穿搭照片。")
        } else {
            append("生成一张真人模特全身穿搭照片（full-body shot）：一位真实的人穿着下列衣物，从头到脚完整入镜——头部与所穿鞋子都必须出现在画面中，如同真人试衣实拍。")
        }
        if (items.isNotEmpty()) {
            append("\n衣物清单：")
            append(items.joinToString("；") { BuildOutfitPrompt().itemLine(it) })
            append("。")
        }
        append("\n严格保持每件衣物的版型、颜色、图案与材质细节")
        append(if (hasReferenceImage) "和拼贴中的单品照片一致" else "符合衣物描述")
        append("，不要替换或混入清单之外的衣物。")
        if (personNote.isNotBlank()) append("\n人物描述：").append(personNote)
        if (scene.isNotBlank()) append("\n场景：").append(scene)
        append("\n构图：全身照，从头顶到脚底完整入镜，必须包含所穿鞋子，不得裁切头部、腿部或脚部，不得拍成半身或七分身；姿态自然、光照真实。")
        append("\n负面约束：画面中不得出现衣架、人台、假人模特、平铺陈列或橱窗展示，所有衣物必须穿在真人身上。")
    }
}
