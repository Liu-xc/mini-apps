#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""report_template.py — 「现状截图 vs 改版线框」对照式 UX 评审报告生成器。

数据驱动：只改下面 ★数据区★，结构自动生成：
  封面 → 总评/方法/共性问题表 → 分节页 → 每主题一页（结论/问题清单/左右对照/要点）
  → 落地拆分 → 附录（实测记录 + 证据截图）

布局铁律（不可改）：全程 flex 流式，禁用 position:absolute
（html2pdf-next.js 会把 absolute 强转 static，封面会塌叠）。

用法: python3 report_template.py [输出.html，默认 report.html]
之后: check-html 验证 → html2pdf-next.js 渲染 → pdf_qa + 视觉验收门
"""
import html
import os
import sys

ASSETS = "assets"  # 截图/线框所在目录（相对输出 HTML）

# ═══════════════════════ ★数据区·按评审填写★ ═══════════════════════

META = dict(
    title="衣橱 · WARDROBE",
    sub="全页面截图走查 · 问题清单 · 改版线框",
    date="2026-09-24",
    scope="14 页面/状态 · 3 素材抽查 · it-032 新语料",
    summary="对衣橱 DEBUG 演示模式（it-032 新增 36 单品/9 穿搭/4 心愿语料随包编译）"
            "实机走查 14 个页面与状态、抽查 3 张生成素材：交互实测 14 项全通过、"
            "上轮 P0 修复零复现；汇总 P0×0 / P1×19 / P2×70，并给出 7 张灰盒改版"
            "线框——蓝色数字徽标对应右侧改版要点。",
    cover_shots=["wardrobe-01-W1-match.png", "wardrobe-09-W9-review.png"],
    stats=[("14", "走查页面/状态"), ("0", "P0 级问题"), ("19", "P1 级问题"),
           ("14", "交互项实测")],
    methods=[
        ("实机截图走查", "1080×2400 模拟器装最新 debug 包（11:02 构建无源码更新），"
                    "演示模式载入 it-032 语料，14 个页面/过程态逐屏截图"),
        ("视觉评审 + 自校验", "两路评审逐页出 P0/P1/P2 清单，以 dump 标题特征自校验防错图；"
                        "关键结论再以 dump bounds（420dp 密度换算）与源码核对定案"),
        ("交互实测验证", "打卡闭环、槽位滑动、筛选计数、混入心愿 A/B 计数、"
                    "上轮 C1 崩溃场景复测；素材层加 PIL 程序化质检"),
    ],
)

# 分节：每节 (分节页标题, 英文 kicker, 截图文件名列表, 主题 app 标识)
SECTIONS = [
    dict(label="衣橱 · WARDROBE", kicker="PART 01", app="wardrobe",
         shots=["wardrobe-03-W3-closet.png", "wardrobe-08-W6-export.png"]),
]

# 主题页（每页一节）。shot=现状截图 wire=改版线框（assets/ 下文件名）
THEMES = [
    dict(app="wardrobe", no="W1", title="搭配页 · 混搭画布",
         concl="一屏拼贴结构成立，但新语料（长中文名 + 两种出图比例）把旧取舍放大："
               "6 张卡 4 张名称截断、图片适配三种样式并存，首屏识别度不足。",
         problems=[
             ("P1", "名称条 4/6 截断（橄榄绿工…/浅蓝色亚…/浅棕色…），识别信息不足"),
             ("P1", "卡图适配不统一：牛仔裤顶部留白、帽/鞋带白边、外套满铺——根因是素材两批比例混用"),
             ("P2", "计数「1/n」无语义；× 视觉符号仅 ~10dp 贴住计数（节点 48×43dp 达标）"),
             ("P2", "混入心愿开启后愿望件不在当前页时无任何「已混入」线索"),
             ("P2", "拼贴无对齐基线、间距忽大忽小，空档像对齐失败"),
         ],
         shot="wardrobe-01-W1-match.png", wire="wf-w1.png",
         points=[
             (1, "混入心愿改正文色描边 pill，对比度达标"),
             (2, "名称 maxLines=2；计数改「图 n/m」语义化"),
             (3, "× 视觉符号放大并与计数拉开（触控节点已达标）"),
             (4, "两列对齐轨道·间距吸附，消除松散空档"),
         ]),
    dict(app="wardrobe", no="W3", title="衣橱页 · 网格与筛选",
         concl="两列网格与筛选链路顺畅，「共 20 件/筛选 5 件」与语料逐一核对一致；"
               "短板集中在 18dp 图标热区、卡图邻物残片与标题截断。",
         problems=[
             ("P1", "标题「海军条纹针织 …」截断，maxLines 不足"),
             ("P1", "卡图底缘露邻物残片（高领衫深色条/条纹衫酒红条，素材裁切问题）"),
             ("P1", "⋮/📊/🌟 图标按钮实测 18×18dp（420dp 密度换算），远低于 44dp 下限"),
             ("P2", "chips 同排三种形制；「鞋」被筛选按钮硬裁无渐隐"),
         ],
         shot="wardrobe-03-W3-closet.png", wire="wf-w3.png",
         points=[
             (1, "图标按钮扩 44dp·长按 tooltip 说明语义"),
             (2, "品类 chips 统一胶囊+右缘渐隐，筛选独立"),
             (3, "标题 maxLines=2，一行 ≥9 汉字不截断"),
             (4, "图片 centerCrop·素材统一比例出图杜绝残片"),
         ]),
    dict(app="wardrobe", no="W5·W7", title="详情页 · 衣物与穿搭",
         concl="内容层级与打卡链路（本轮实测全过）都稳；页面骨架是重灾区：三段式"
               "色阶接缝贯穿详情/表单/回顾页，破坏性操作与超小热区叠加。",
         problems=[
             ("P1", "状态栏浅绿带 × 白顶栏 × 浅绿内容三段接缝（W4/W5/W7/W9 同病）"),
             ("P1", "删除垃圾桶与编辑同色并排无警示；评论删除 × 仅 14×14dp 且无确认"),
             ("P1", "标签 chips 实测高 15dp；W4 品类 chips 19dp"),
             ("P2", "评论日期 09/24 与顶栏 2026/09/24 两套格式；行距节奏头重脚轻"),
         ],
         shot="wardrobe-07b-W7-scrolled.png", wire="wf-detail.png",
         points=[
             (1, "状态栏沉浸同色·消除色阶接缝"),
             (2, "删除改警示红或收进 ⋯ 菜单，执行前确认"),
             (3, "大图 full-bleed 至卡片圆角"),
             (4, "chips 高度统一提到 44dp"),
             (5, "评论删除 44dp 热区·点按先确认"),
         ]),
    dict(app="wardrobe", no="W6", title="导出面板 · 长图与文案",
         concl="Prompt 置顶 + 人体拼贴结构（it-017 修订）成立；但拼贴标签硬截、"
               "文案区被固定动作栏拦腰裁切是两个 P1 级观感破损。",
         problems=[
             ("P1", "拼贴标签「包 · 浅棕色帆布托…」硬截无 ellipsis、「下装」标签贴边"),
             ("P1", "文案区被动作栏裁切仅露标题行，无渐隐无滚动到底提示"),
             ("P2", "人物描述（占位符式）与自定义要求（浮动标签）两套控件语言"),
             ("P2", "拼贴两列错位、行宽不一，与下方规整表单对比凌乱"),
         ],
         shot="wardrobe-08-W6-export.png", wire="wf-w6.png",
         points=[
             (1, "等宽两列·标签 ellipsis + 8dp 内边距"),
             (2, "两输入框统一占位符规格"),
             (3, "动作栏上方 24dp 安全区·文案可滚至完整"),
         ]),
    dict(app="wardrobe", no="W8", title="穿搭记录 · 时间维度缺失",
         concl="轮播 + 网格结构清楚、上轮翻页器压图已修；但作为「记录」页全页无日期，"
               "翻页胶囊实测 15dp 高是本轮最影响主流程的热区。",
         problems=[
             ("P1", "所有记录卡无日期/标题，5 套只能靠图片记忆（详情页明明有日期）"),
             ("P1", "‹1/5› 分页胶囊节点 87×40px ≈ 33×15dp，主流程翻页难稳定点中"),
             ("P2", "网格缩略风格混杂：左效果图右单品平铺，语义不一致"),
             ("P2", "「随机翻一套」与 W1「随机一套」同一动作两套文案"),
         ],
         shot="wardrobe-06-W8-records.png", wire="wf-w8.png",
         points=[
             (1, "文案与 W1 统一为「随机一套」"),
             (2, "日期角标常驻·网格补日期行"),
             (3, "分页整半边可点 44dp·卡片滑动为主"),
             (4, "缩略统一效果图·降级拼贴标注类型"),
         ]),
    dict(app="wardrobe", no="W9", title="衣橱回顾 · 数据与状态语义",
         concl="上轮「空态加去打卡」已落地；本轮暴露的是状态语义：比例条同色对不上"
               "标签、禁用/演示模式态与可点态几乎无差别。",
         problems=[
             ("P1", "品类分布 6 段全同色，与下方「上装5·外套4…」标签无法一一对应"),
             ("P1", "年度长图禁用态对比度贴阈值，且无解锁条件说明"),
             ("P1", "数据包导入/导出在演示模式仍为可点态，禁用信息仅底部小字"),
             ("P2", "提醒「90天」选中灰而非品牌绿；开关关闭 chips 仍显选中"),
         ],
         shot="wardrobe-09-W9-review.png", wire="wf-w9.png",
         points=[
             (1, "今年/累计等分 选中态填充"),
             (2, "同色系色阶 + 段内直接标注一一对应"),
             (3, "禁用态补解锁条件副文案·对比度达标"),
             (4, "提醒开关联动·选中态统一品牌绿"),
             (5, "演示模式行降透明去箭头·行内徽标说明"),
         ]),
    dict(app="wardrobe", no="W10", title="心愿 · 占位与一致性",
         concl="2 单品 + 0 穿搭的角色隔离与语料核对一致；无图占位空洞与「删除同色"
               "并排」是从 W7 延续到弹层的老问题。",
         problems=[
             ("P1", "无图心愿卡左侧大面积白块仅星 + 品类，视觉空洞失衡"),
             ("P1", "详情弹层内垃圾桶与铅笔同色并排，无警示无间距"),
             ("P2", "「鞋」chip 被右缘硬裁无渐隐；分段与回顾页连体控件两套规格"),
             ("P2", "价格绿 + 中文灰 + 等宽域名同行混排嘈杂"),
         ],
         shot="wardrobe-10-W10-wishlist.png", wire="wf-w10.png",
         points=[
             (1, "连体分段·与回顾页同规格"),
             (2, "品类 chips 右缘渐隐·保留下一 chip 露头"),
             (3, "品类色块占位，告别空白洞"),
             (4, "域名移到次级行 不再混排"),
         ]),
]

# 跨应用共性问题表
COMMONS = [
    ("C1", "名称/标签截断：长中文名语料把「名称+计数+×」的取舍放大", "W1/W3/W6", "P1",
     "名称 maxLines=2 + 计数「图 n/m」+ 标签 ellipsis（线框 W1②/W3③/W6①）"),
    ("C2", "卡图适配不统一与邻物脏边；根因=素材 362² 与 410×319 两批比例混用",
     "W1/W3/W5/W14", "P1",
     "出图统一 4:3 + centerCrop + 裁切净化（it-035 素材管线；线框 W3④）"),
    ("C3", "触控目标低于 44dp：图标 18dp / 品类 chips 19dp / 标签 chips 15dp / 评论 × 14dp / 分页 15dp",
     "W3/W4/W5/W7/W8/W14", "P1",
     "热区统一 44dp（线框 W3①/W8③/详情④⑤），实测 bounds 见附录"),
    ("C4", "状态栏/顶栏三段式色阶接缝（浅绿带×白顶栏×浅绿内容）", "W4/W5/W7/W9", "P1",
     "详情/表单/回顾页状态栏沉浸同色（线框详情①）"),
    ("C5", "破坏性操作无警示：删除与编辑同色并排、评论 × 无确认", "W5/W7/W10", "P1",
     "删除改警示红/收进 ⋯ 菜单 + 确认（线框详情②⑤）"),
    ("C6", "禁用/演示模式态语义不足：可点态与禁用态几乎无差别", "W9", "P1",
     "解锁条件副文案 + 降透明 + 行内徽标（线框 W9③④⑤）"),
    ("C7", "记录无日期、计数无语义——信息维度缺失", "W1/W8", "P1",
     "日期角标/日期行 + 「图 n/m」（线框 W8②/W1②）"),
    ("C8", "导出弹层文案区被动作栏裁切 + 拼贴标签硬截", "W6", "P1",
     "24dp 安全区可滚至完整 + 标签 ellipsis（线框 W6①③）"),
    ("C9", "品类分布 6 段同色，与文字标签无法对应", "W9", "P1",
     "同色系色阶 + 段内直接标注（线框 W9②）"),
    ("C10", "无图心愿卡占位空洞", "W10", "P1",
     "品类色块占位（线框 W10③）"),
    ("C11", "chips 形制与边缘处理不统一（胶囊/圆 chip/分段三套并存、硬裁无渐隐）",
     "W3/W8/W10/W14", "P2", "统一胶囊 + 右缘渐隐 + 分段同规格（线框 W3② 等）"),
    ("C12", "文案与格式不统一：随机翻一套 vs 随机一套、日期两格式、绿色说明文字像链接",
     "W1/W8/W9", "P2", "统一文案与日期格式；说明文字改中性灰（线框 W8①）"),
]

# 落地拆分：迭代名 → [(线框号, 内容, 对应主题)]
SPLITS = [
    ("wardrobe / it-033 · 触控与截断专项（P1 高频先修）", [
        ("O1", "名称 maxLines=2 + 计数「图 n/m」+ × 视觉放大", "W1/W3/W6"),
        ("O2", "触控目标统一 44dp：📊🌟⋮ 图标、品类/标签 chips、分页胶囊、评论 ×",
         "W3/W4/W5/W7/W8"),
        ("O3", "记录卡与网格补日期角标/日期行", "W8"),
    ]),
    ("wardrobe / it-034 · 页面骨架与状态语义", [
        ("O1", "状态栏沉浸同色，消除详情/表单/回顾三段接缝", "W4/W5/W7/W9"),
        ("O2", "删除警示红 + 确认（详情顶栏/心愿弹层/评论）", "W5/W7/W10"),
        ("O3", "禁用与演示态：解锁说明、降透明、徽标；比例条色阶 + 段内标注", "W9"),
    ]),
    ("wardrobe / it-035 · 素材管线统一（根因修复）", [
        ("O1", "出图规格统一 4:3、裁剪净化邻物残片；tools/mock-data 加比例/纯净度校验",
         "素材层/W1/W3"),
        ("O2", "效果图上下安全边距 5-8%（帽顶/鞋底贴边两例）", "素材层"),
    ]),
    ("wardrobe / it-036 · 组件一致性收尾", [
        ("O1", "chips 形制统一 + 右缘渐隐；分段控件统一规格", "W3/W8/W10/W14"),
        ("O2", "导出文案区安全区 + 标签 ellipsis + 两输入框同规格", "W6"),
        ("O3", "心愿色块占位、域名弱化、混入心愿开启提示；日期/文案格式统一",
         "W10/W1/W8"),
    ]),
]

# 附录：实测记录 + 证据截图（图高自动 88mm，三张以内）
TESTS = [
    ("构建与安装", "APK 11:02 构建、无源码比它新；装机 + 演示模式加载 it-032 语料成功"),
    ("语料规模核对", "JSON：36 单品/9 穿搭/5 评论/4 心愿单品/1 心愿穿搭；"
                "UI：Leo 20 件·5 套·心愿 2+0（Mia 16·4·2+1，角色隔离正确）"),
    ("筛选断言", "W3 点「上装」→「共 5 件」= JSON 中 Leo 的 TOP 数量 5"),
    ("槽位滑动", "外套格 1/4→2/4 换件；「随机一套」整组刷新"),
    ("混入心愿 A/B", "ON：外套 2/4→2/5、包 1/2→1/3，OFF 即还原；"
                "愿望卡角标/虚线样式代码 SlotGrid.kt:132-192 存在（it-019/030）"),
    ("上轮 C1 崩溃复测", "愿望页 + 关闭混入反复开关 6 轮 0 FATAL（上轮为必现越界崩溃）"),
    ("打卡闭环", "「今天穿了这套」→ 双按钮（今日已穿·再记一次 / 撤销今日）→ 撤销还原"),
    ("上轮修复复核", "9-23 C1–C10 全绿：翻页器外置 ✓、打卡主按钮 ✓、空态去打卡 ✓、"
                "名称无品类前缀 ✓、崩溃不复现 ✓"),
    ("触控目标实测", "420dp 密度换算：📊🌟⋮=18dp、品类 chips=19dp、标签 chips=15dp、"
                "评论 ×=14dp、分页=15dp；W1 × 节点 48×43dp 达标"),
    ("导航冒烟", "W1–W10 + 弹层/过程态逐页导航，全程零崩溃"),
    ("源码核对", "W1 空品类不渲染 = it-015 既定设计（非缺陷）；名称截断为代码注释"
              "明示的已知取舍——本轮语料让其显性化"),
]
EVIDENCE = [
    ("wardrobe-15-W7-checkedin.png", "打卡后双按钮实测——上轮 C7 落地 + 本轮打卡闭环全过"),
    ("wardrobe-09-W9-review.png", "回顾页空态带「去打卡」主按钮——上轮 C10 已落地"),
    ("wardrobe-14-W3-filtered.png", "筛选「共 5 件」= JSON 中 Leo TOP 数量 5（新语料数据断言）"),
]

# ═══════════════════════ 以下为生成逻辑（一般不动） ═══════════════════════

CSS = """
@page { size: 210mm 297mm; margin: 0; }
html, body { margin: 0; padding: 0; width: 210mm; background: #F7F8FA;
  font-family: "Hiragino Sans GB","PingFang SC","Heiti SC",sans-serif;
  color: #1D2129; line-break: strict; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
* { box-sizing: border-box; }
.page { width: 210mm; height: 297mm; overflow: hidden; position: relative;
  padding: 12mm 14mm 10mm; background: #F7F8FA; break-after: page;
  display: flex; flex-direction: column; }
.page:last-child { break-after: auto; }
img { display: block; }
.cover { padding: 18mm 16mm 16mm; background: #FFFFFF; }
.cover .kicker { font-size: 10pt; letter-spacing: 3pt; color: #86909C; font-weight: 600; }
.cover .hairline { width: 30mm; height: 0; border-top: 0.7mm solid #2F6BFF; margin-top: 7mm; }
.cover h1 { font-size: 40pt; font-weight: 800; letter-spacing: 1pt; margin: 24mm 0 0; }
.cover h1.mid { font-size: 34pt; margin-top: 30mm; }
.cover .sub { font-size: 14pt; color: #4E5969; margin-top: 7mm; }
.cover .summary { font-size: 10.5pt; line-height: 1.75; color: #4E5969; margin-top: 20mm; width: 104mm; }
.cover .phones { display: flex; gap: 6mm; margin-left: auto; margin-top: auto; }
.cover .phone { width: 34mm; height: 75.6mm; border: 0.5mm solid #C9CDD4; border-radius: 4.5mm;
  padding: 2.2mm; background: #FFFFFF; }
.cover .phone img { width: 100%; height: 100%; object-fit: cover; border-radius: 2.5mm; }
.cover .meta { display: flex; gap: 10mm; font-size: 9.5pt; color: #86909C;
  border-top: 0.3mm solid #E5E6EB; padding-top: 6mm; margin-top: 12mm; }
.cover .meta b { color: #1D2129; font-weight: 600; }
.phead { display: flex; align-items: baseline; gap: 4mm; border-bottom: 0.45mm solid #E5E6EB;
  padding-bottom: 3.5mm; margin-bottom: 4mm; }
.phead .no { font-size: 13pt; font-weight: 800; color: #2F6BFF; }
.phead .t { font-size: 14.5pt; font-weight: 700; }
.phead .sev { margin-left: auto; font-size: 8.5pt; color: #86909C; }
.pfoot { display: flex; font-size: 8pt; color: #A9AEB8;
  border-top: 0.3mm solid #E5E6EB; padding-top: 2.5mm; margin-top: auto; }
.concl { font-size: 10pt; line-height: 1.65; margin: 0 0 4mm; }
.problems { display: flex; flex-wrap: wrap; gap: 1.6mm 4mm; margin-bottom: 4.5mm; }
.prob { width: calc(50% - 2mm); font-size: 8.8pt; line-height: 1.5; color: #4E5969; }
.tag { display: inline-block; font-size: 7.5pt; font-weight: 700; color: #fff; border-radius: 1.2mm;
  padding: 0.3mm 1.6mm; margin-right: 1.6mm; vertical-align: 0.3mm; }
.tag.p0 { background: #F53F3F; } .tag.p1 { background: #FF7D00; } .tag.p2 { background: #86909C; }
.shots { display: flex; gap: 6mm; align-items: flex-start; flex: 1; }
.shotcol { width: 76mm; }
.shotcol img { width: 76mm; height: 168.9mm; object-fit: cover; object-position: top;
  border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; background: #fff; }
.shotlabel { display: flex; align-items: center; gap: 2mm; margin-top: 2.2mm; font-size: 9pt; font-weight: 700; }
.shotlabel .dot { width: 2.6mm; height: 2.6mm; border-radius: 50%; }
.d-now { background: #F53F3F; } .d-new { background: #2F6BFF; }
.shotlabel span.cap { font-weight: 400; color: #86909C; font-size: 8pt; margin-left: auto; }
.points { flex: 1; display: flex; flex-direction: column; gap: 2.6mm; }
.pt { font-size: 8.8pt; line-height: 1.5; color: #4E5969; }
.pt .n { display: inline-flex; width: 5mm; height: 5mm; border-radius: 50%; background: #2F6BFF;
  color: #fff; font-size: 8pt; font-weight: 700; align-items: center; justify-content: center;
  margin-right: 1.8mm; vertical-align: -1mm; }
table { border-collapse: collapse; width: 100%; }
th { font-size: 9pt; text-align: left; color: #86909C; font-weight: 600;
  border-bottom: 0.45mm solid #C9CDD4; padding: 2mm 2.5mm; }
td { font-size: 9.3pt; line-height: 1.5; padding: 1.9mm 2.5mm; border-bottom: 0.3mm solid #E5E6EB;
  vertical-align: top; }
td.c { text-align: center; }
.h2 { font-size: 13pt; font-weight: 800; margin: 4.5mm 0 2.5mm; }
.h2:first-child { margin-top: 0; }
.lead { font-size: 10pt; line-height: 1.75; color: #4E5969; }
.statrow { display: flex; gap: 5mm; margin: 5mm 0; }
.stat { flex: 1; background: #fff; border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; padding: 4mm; }
.stat .v { font-size: 22pt; font-weight: 800; color: #2F6BFF; }
.stat .l { font-size: 8.5pt; color: #86909C; margin-top: 1mm; }
.mrow { display: flex; gap: 4mm; margin: 2mm 0; }
.mcard { flex: 1; background: #fff; border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; padding: 3.5mm; }
.mcard b { font-size: 9.5pt; display: block; margin-bottom: 1.5mm; }
.mcard p { margin: 0; font-size: 8.6pt; line-height: 1.6; color: #4E5969; }
.appendix-shot { display: flex; gap: 5mm; }
.appendix-shot .acol { flex: 1; text-align: center; }
.appendix-shot img { width: 39.6mm; height: 56mm; object-fit: cover; object-position: top;
  border: 0.3mm solid #E5E6EB; border-radius: 2mm; margin: 0 auto; }
.appendix-shot .cap { font-size: 7.5pt; color: #86909C; margin-top: 1.5mm; line-height: 1.45; }
"""

A = ASSETS


def esc(s):
    return html.escape(s, quote=False)


def sev_counts(th):
    c = {"P0": 0, "P1": 0, "P2": 0}
    for s, _ in th["problems"]:
        c[s] += 1
    return " ".join(f"{k}×{v}" for k, v in c.items() if v)


def theme_page(th, pageno):
    probs = "".join(
        f'<div class="prob"><span class="tag {p[0].lower()}">{p[0]}</span>{esc(p[1])}</div>'
        for p in th["problems"])
    pts = "".join(
        f'<div class="pt"><span class="n">{n}</span>{esc(t)}</div>'
        for n, t in th["points"])
    appname = next((s["label"].split(" · ")[0] for s in SECTIONS if s["app"] == th["app"]), "")
    return f"""
<section class="page">
  <div class="phead"><span class="no">{esc(th['no'])}</span><span class="t">{esc(th['title'])}</span>
    <span class="sev">{sev_counts(th)}</span></div>
  <p class="concl">{esc(th['concl'])}</p>
  <div class="problems">{probs}</div>
  <div class="shots">
    <div class="shotcol"><img src="{A}/{esc(th['shot'])}" alt="">
      <div class="shotlabel"><span class="dot d-now"></span>现状截图<span class="cap">模拟器实机</span></div></div>
    <div class="shotcol"><img src="{A}/{esc(th['wire'])}" alt="">
      <div class="shotlabel"><span class="dot d-new"></span>改版线框<span class="cap">蓝色徽标 = 变更点</span></div></div>
    <div class="points">{pts}</div>
  </div>
  <div class="pfoot"><span>{esc(appname)} · UX 评审报告</span><span style="margin-left:auto">{pageno}</span></div>
</section>"""


def divider(sec, pageno):
    imgs = "".join(f'<div class="phone"><img src="{A}/{esc(s)}" alt=""></div>' for s in sec["shots"])
    n = sum(1 for t in THEMES if t["app"] == sec["app"])
    return f"""
<section class="page cover">
  <div class="kicker">{esc(sec['kicker'])}</div>
  <div class="hairline"></div>
  <h1 class="mid">{esc(sec['label'])}</h1>
  <div class="sub">{n} 个页面 · 现状与改版线框对照</div>
  <div class="phones">{imgs}</div>
  <div class="meta"><span>UX 评审报告 · {esc(META['date'])}</span><span style="margin-left:auto">{pageno}</span></div>
</section>"""


def build():
    pages, pageno = [], 1

    stats = "".join(f'<div class="stat"><div class="v">{esc(v)}</div><div class="l">{esc(l)}</div></div>'
                    for v, l in META["stats"])
    methods = "".join(f'<div class="mcard"><b>{esc(a)}</b><p>{esc(b)}</p></div>' for a, b in META["methods"])
    crows = "".join(
        f"<tr><td class='c'><b>{c[0]}</b></td><td>{esc(c[1])}</td><td class='c'>{esc(c[2])}</td>"
        f"<td class='c'><span class='tag {c[3].lower()}'>{c[3]}</span></td><td>{esc(c[4])}</td></tr>"
        for c in COMMONS)
    cover_phones = "".join(f'<div class="phone"><img src="{A}/{esc(s)}" alt=""></div>'
                           for s in META["cover_shots"])

    pages.append(f"""
<section class="page cover">
  <div class="kicker">UX REVIEW</div>
  <div class="hairline"></div>
  <h1>{esc(META['title'])}</h1>
  <div class="sub">{esc(META['sub'])}</div>
  <div class="summary">{esc(META['summary'])}</div>
  <div class="phones">{cover_phones}</div>
  <div class="meta">
    <span>日期 <b>{esc(META['date'])}</b></span><span>范围 <b>{esc(META['scope'])}</b></span>
    <span style="margin-left:auto">蓝色徽标与改版要点编号对应</span>
  </div>
</section>""")
    pageno += 1

    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">00</span><span class="t">总评 · 方法</span></div>
  <p class="lead">{esc(META['summary'])}</p>
  <div class="statrow">{stats}</div>
  <div class="h2">评审方法</div>
  <div class="mrow">{methods}</div>
  <div class="pfoot"><span>总评</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")
    pageno += 1

    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">00</span><span class="t">跨应用共性问题（优先修）</span></div>
  <table>
    <tr><th style="width:9mm">#</th><th>问题</th><th style="width:36mm">涉及页面</th>
        <th style="width:12mm">级别</th><th style="width:52mm">修法（对应线框）</th></tr>
    {crows}
  </table>
  <div class="pfoot"><span>共性问题</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")
    pageno += 1

    for sec in SECTIONS:
        pages.append(divider(sec, pageno))
        pageno += 1
        for th in (t for t in THEMES if t["app"] == sec["app"]):
            pages.append(theme_page(th, pageno))
            pageno += 1

    srows = ""
    for name, items in SPLITS:
        srows += f'<div class="h2">{esc(name)}</div><table>'
        srows += '<tr><th style="width:14mm">线框</th><th>内容</th><th style="width:22mm">对应</th></tr>'
        for o, content, target in items:
            srows += f"<tr><td class='c'>{esc(o)}</td><td>{esc(content)}</td><td class='c'>{esc(target)}</td></tr>"
        srows += "</table>"
    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">→</span><span class="t">落地拆分建议</span></div>
  <p class="lead">按仓库迭代流程确认后动工；先修共性问题（功能在但用户够不着/猜不到），再修各 App 核心体验。</p>
  {srows}
  <div class="pfoot"><span>落地拆分</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")
    pageno += 1

    trows = "".join(f"<tr><td><b>{esc(a)}</b></td><td>{esc(b)}</td></tr>" for a, b in TESTS)
    ev = "".join(f'<div class="acol"><img src="{A}/{esc(p)}" alt=""><div class="cap">{esc(c)}</div></div>'
                 for p, c in EVIDENCE)
    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">A</span><span class="t">附录 · 交互实测记录与资产</span></div>
  <div class="h2">实测验证（uiautomator / 源码核对）</div>
  <table><tr><th style="width:34mm">项目</th><th>结论</th></tr>{trows}</table>
  <div class="h2">实证截图</div>
  <div class="appendix-shot">{ev}</div>
  <div class="h2">资产清单</div>
  <p class="lead" style="font-size:9pt">现状截图与改版线框见 {A}/ 目录；本报告由 report_template.py 生成，可改数据重出。</p>
  <div class="pfoot"><span>附录</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"><title>{esc(META['title'])} · UX 评审报告 · {esc(META['date'])}</title>
<style>{CSS}</style></head>
<body>{''.join(pages)}</body></html>"""

    out = sys.argv[1] if len(sys.argv) > 1 else "report.html"
    with open(out, "w", encoding="utf-8") as f:
        f.write(doc)
    print("written", out, len(doc), "bytes")


if __name__ == "__main__":
    build()
