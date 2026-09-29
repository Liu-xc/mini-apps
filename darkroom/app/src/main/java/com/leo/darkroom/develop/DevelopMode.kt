package com.leo.darkroom.develop

/**
 * 显现方式（it-007 M3 建立；it-008 修正 CHEMICAL 语义）：三种模式各自的显现形状，共用同一套噪声与桶缓存。
 * - [CHEMICAL] 拍立得：白浊阻光层均匀消散——整张画面从白里逐步浮现（真实观感，无方向前沿）
 * - [BLOCKS] 数码相机：网格块逐格点亮（低分辨率加载感）
 * - [SWEEP] 胶片：从左向右的冲洗推进带
 */
enum class RevealKind { CHEMICAL, BLOCKS, SWEEP }

/** 出纸方式（每套动画与机器形态对应） */
enum class EjectStyle { SLOT_RISE, SCREEN_WAKE, FILM_WIND }

/**
 * 显影模式（it-007 US-14）：一次显影会话的完整身份——阶段名、陪伴文案、
 * 显现方式、出纸方式与导出起手时长。卡面几何由 `CardLayout.solve(mode, …)` 承担，
 * 影调曲线由 `DevelopSpec.visualAt(mode, …)` 承担，本枚举只持有「说哪种话」。
 */
enum class DevelopMode(
    val label: String,
    val note: String,
    /** 三个阶段名，顺序对应 [DevelopPhase] */
    val stages: List<String>,
    /** 四句陪伴文案：潜影 / 浮现 / 定影中 / 定影完成 */
    val copies: List<String>,
    val reveal: RevealKind,
    val eject: EjectStyle,
    /** 导出视频起手空白段（毫秒） */
    val leadMs: Long,
) {
    POLAROID(
        label = "拍立得",
        note = "相纸从滚轴压出",
        stages = listOf("潜影", "浮现", "定影"),
        copies = listOf(
            "先让这一刻安静一会儿",
            "让回忆慢慢浮出来",
            "光影正在慢慢定住",
            "这一刻，已经好好留下",
        ),
        reveal = RevealKind.CHEMICAL,
        eject = EjectStyle.SLOT_RISE,
        leadMs = 500L,
    ),
    DIGITAL(
        label = "数码相机",
        note = "像回放一张直出片",
        stages = listOf("取景", "曝光", "成像"),
        copies = listOf(
            "先对上这一格",
            "光正在被记下来",
            "画面正在落定",
            "这一格，已经存下",
        ),
        reveal = RevealKind.BLOCKS,
        eject = EjectStyle.SCREEN_WAKE,
        leadMs = 500L,
    ),
    FILM(
        label = "胶片",
        note = "从片盒里卷出一格",
        stages = listOf("感光", "显影", "定影"),
        copies = listOf(
            "卤化银先记住它",
            "药液正在浸透",
            "水洗之后就稳了",
            "这一格，冲出来了",
        ),
        reveal = RevealKind.SWEEP,
        eject = EjectStyle.FILM_WIND,
        leadMs = 800L,
    ),
    ;

    fun stageLabel(phase: DevelopPhase): String = stages[phase.ordinal]

    fun copyAt(progress: Float): String = when {
        progress < DevelopSpec.LATENT_END -> copies[0]
        progress < DevelopSpec.EMERGING_END -> copies[1]
        progress < 1f -> copies[2]
        else -> copies[3]
    }

    companion object {
        fun fromName(name: String?): DevelopMode =
            entries.firstOrNull { it.name == name } ?: POLAROID
    }
}
