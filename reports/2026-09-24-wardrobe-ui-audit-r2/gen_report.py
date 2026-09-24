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
    sub="实施后复验走查 · C1–C12 验证矩阵 · 残留清单",
    date="2026-09-24",
    scope="it-033~036 实施后 · 19 页面/状态 · APK 14:59",
    summary="针对 it-033~036 实施后的最新构建（APK 14:59，5 提交 0963e3c/5656189/"
            "be076bf/c7269fc/6756a63）重新全页走查：19 个状态重截，dump bounds、像素探测、"
            "视觉逐页三通道与基线报告逐条对照。结论，12 项共性问题 10 项完全修复、"
            "1 项部分修复（窄格长名）、1 项为刻意保留的素材债务；本轮新增 P0×0 / P1×0 / "
            "P2×4（均为基线范围外或提案遗漏项），全程零崩溃零回归。",
    cover_shots=["r2-01-W1-match.png", "r2-09-W9-review.png"],
    stats=[("12", "共性问题验证"), ("10", "完全修复"), ("4", "残留 P2"), ("0", "新增 P1+")],
    methods=[
        ("实施后全量重截", "APK 14:59（无源码比它新）install -r，演示模式 19 个页面/"
                     "过程态逐一重截（r2-* 系列），含 W6 滚动态、打卡双按钮等过程态"),
        ("三通道验证", "uiautomator bounds（420dp 换算）、关键区域像素探测"
                  "（状态栏/角标/色阶）、视觉逐页评审，每条结论至少两通道互证"),
        ("与基线对照", "对照 2026-09-24 基线报告 C1–C12 与 7 张线框要点逐条判定"
                  " ✅完全修复 / ⚠️部分修复 / 📌刻意保留债务"),
    ],
)

SECTIONS = [
    dict(label="衣橱 · 复验", kicker="PART 02 · RECHECK", app="wardrobe",
         shots=["r2-09-W9-review.png", "r2-08b-W6-scrolled.png"]),
]

THEMES = [
    dict(app="wardrobe", no="W1", title="搭配页 · 名称条与热区复验",
         concl="名称条三段式落地：锈红色灯芯绒夹克、海军条纹针织Polo 等长名两行完整显示、"
               "一屏网格未破；✕ 与 n/n 角标热区 48dp（dump 126×126px）、a11y 语义就位。"
               "残留：包格 9 字名两行仍带省略；「混入心愿」仍为灰色低对比（提案遗漏）。",
         problems=[
             ("✅", "长名两行完整：锈红色灯芯绒夹夹克、海军条纹针织Polo 全显，单行高度与基线一致"),
             ("✅", "✕ 视觉 16dp、触控节点 126×126px=48dp；n/n 角标 a11y「第 n 件，共 m 件」"),
             ("⚠️", "包格「鼠尾草绿尼龙双肩包」两行后仍省略（窄格 9 字，fitStep 阈值残留）"),
             ("⚠️", "「混入心愿」灰色描边低对比未改（原线框要点①，四个迭代 scope 遗漏）"),
             ("📌", "象牙白短裤卡底仍见米色衣领残片，素材债务（线框④，待素材重出）"),
         ],
         shot="r2-01-W1-match.png", wire="wf-w1.png",
         points=[
             (1, "⚠️ 混入心愿改正文色，原要点未落地，列入 it-037 候选"),
             (2, "✅ 名称 maxLines=2/动态字号已落地，长名两行完整"),
             (3, "✅ ✕ 视觉放大 + 48dp 热区已落地（dump 实测）"),
             (4, "📌 对齐轨道未做（基线 P2 范围外）；素材残片待重出"),
         ]),
    dict(app="wardrobe", no="W3", title="衣橱页 · 网格与筛选复验",
         concl="卡标题两行恒行高落地（海军条纹针织 Polo 全显）；行尾白色渐隐让「鞋」chip "
               "不再硬裁；📊🌟⋮ 热区装机实测 48dp。卡图邻物残片为素材债务按预期保留。",
         problems=[
             ("✅", "「海军条纹针织 Polo」两行完整显示，网格行高稳定"),
             ("✅", "品类 chips 行尾渐隐生效，「鞋」chip 半透明过渡不再生硬裁切"),
             ("✅", "📊/🌟/⋮ 触控 48dp（it-033 装机 dump 实测 126×126px）"),
             ("📌", "高领衫底缘深色残片、条纹衫酒红残片仍在，素材二进制零改动是 it-035 刻意决策"),
         ],
         shot="r2-03-W3-closet.png", wire="wf-w3.png",
         points=[
             (1, "✅ 图标 48dp 热区已落地（视觉 32dp 圆钮保持，热区由外层补足）"),
             (2, "✅ 统一胶囊 + 右缘渐隐已落地（ScrollFade 组件）"),
             (3, "✅ 标题 maxLines=2 已落地，两行全显"),
             (4, "📌 centerCrop/素材统一，待 validate_assets --strict 驱动重出"),
         ]),
    dict(app="wardrobe", no="W5·W7", title="详情页 · 骨架与警示复验",
         concl="三段式色阶接缝根除：appbar 由 y309 上移到 y174–249、状态栏随白底铺满"
               "（W4/W5/W7 像素白，视觉无接缝）；W7 垃圾桶 error 红；评论日期与顶栏"
               "统一 2026/09/24；标签 chips 44dp。二次确认核实为 it-029 既有（基线误报）。",
         problems=[
             ("✅", "状态栏沉浸：appbar y309→174，四白底路由状态栏像素纯白，接缝消失"),
             ("✅", "W7 垃圾桶 error 红（视觉实锤 (179,38,30)）；删除确认 it-029 已存在"),
             ("✅", "评论日期 2026/09/24（dump+视觉），与顶栏同格式"),
             ("✅", "标签 chips ≥44dp、评论 × 热区 48dp（it-033 装机实测）"),
         ],
         shot="r2-07b-W7-scrolled.png", wire="wf-detail.png",
         points=[
             (1, "✅ 状态栏沉浸同色已落地（路由级 inset 修复）"),
             (2, "✅ 删除警示红已落地；确认弹窗核实为 it-029 既有"),
             (3, "✅ 大图 full-bleed，基线 P2 未列入迭代，视觉维持原衬纸容器（记录）"),
             (4, "✅ chips 44dp 已落地"),
             (5, "✅ 评论删除 48dp 热区 + it-029 确认已在"),
         ]),
    dict(app="wardrobe", no="W6", title="导出面板 · 文案与标签复验",
         concl="文案区块可滚动至完整可见（bounds 证实整框露出，与动作栏留安全间距）；"
               "拼贴标签加 ellipsis+内边距（「帽子·黑色棒球帽·…」）；两输入框统一占位符式。",
         problems=[
             ("✅", "文案区滚到底完整可见（dump bounds），底部 24dp 渐隐 + 40dp 安全底距"),
             ("✅", "长图标签 ellipsis + 8dp 内边距，硬截消失"),
             ("✅", "人物描述/自定义要求两框统一占位符式（floating label 废止）"),
         ],
         shot="r2-08b-W6-scrolled.png", wire="wf-w6.png",
         points=[
             (1, "✅ 等宽标签 + ellipsis + 内边距已落地"),
             (2, "✅ 两输入框同规格已落地"),
             (3, "✅ 文案区安全距离+渐隐已落地（滚动态 bounds 证）"),
         ]),
    dict(app="wardrobe", no="W8", title="穿搭记录 · 时间与热区复验",
         concl="「随机一套」与 W1 统一；hero 日期角标存在（像素深色 1729，网格瓦片区 818）；"
               "分页拆 ‹/› 双半热区，dump 节点「上一张」「下一张」均 126×126px=48dp。",
         problems=[
             ("✅", "顶栏「随机一套」（dump 实测，与 W1 同文案）"),
             ("✅", "hero 日期角标：角标区深色像素 1729（网格瓦片区 818）"),
             ("✅", "分页左右半「上一张/下一张」126×126px=48dp（dump 节点名实锤）"),
             ("⚠️", "网格缩略效果图/拼贴混杂未改，基线 P2 不在四迭代 scope"),
         ],
         shot="r2-06-W8-records.png", wire="wf-w8.png",
         points=[
             (1, "✅ 文案与 W1 统一为「随机一套」已落地"),
             (2, "✅ 日期角标已落地（像素探测证实）"),
             (3, "✅ 分页整半边 44dp 已落地（实测 48dp）"),
             (4, "⚠️ 缩略统一未做，P2 后续项"),
         ]),
    dict(app="wardrobe", no="W9", title="衣橱回顾 · 状态语义复验",
         concl="四处状态语义全部落地：比例条 6 档绿色阶+宽段段内白字直标（下装6/上装5）、"
               "年度长图解锁说明按 hasWearData 生成、数据行演示态（降透明+徽标+无箭头）、"
               "提醒 chips 关联动灰且演示说明改中性灰；页面无接缝。",
         problems=[
             ("✅", "比例条 6 档绿（#14543A→#BFE1CE），段内白字「下装 6」「上装 5」直标"),
             ("✅", "解锁说明「2026 年打卡 ≥ 1 次后解锁 · 去『穿搭记录』…」+ 对比度达标"),
             ("✅", "数据行 alpha 降透明 + 「演示模式」行内徽标 + 行尾箭头移除"),
             ("✅", "提醒总开关关 → chips 全体灰化；「演示模式不推送」改中性灰"),
         ],
         shot="r2-09-W9-review.png", wire="wf-w9.png",
         points=[
             (1, "✅ 分段与 W10 共享同款 SegmentedToggle（勾选样式为 M3 既定取舍）"),
             (2, "✅ 同色系色阶 + 段内直接标注已落地"),
             (3, "✅ 解锁条件副文案 + 对比度已落地"),
             (4, "✅ 提醒开关联动 + 品牌绿选中已落地"),
             (5, "✅ 演示模式行降透明 + 行内徽标已落地"),
         ]),
    dict(app="wardrobe", no="W10", title="心愿 · 占位与分段复验",
         concl="品类色块占位（外套蓝灰、包焦糖）告别空白洞；分段改与 W9 同款连体控件；"
               "域名 example.com 下移独立弱化行；chips 行尾渐隐就位。",
         problems=[
             ("✅", "无图卡品类色块占位（蓝灰外套/焦糖包，星+品类保留，亮度选字色）"),
             ("✅", "连体分段 SegmentedToggleRow 与 W9 同款，双 FilterChip 伪分段废止"),
             ("✅", "域名移至独立行 inkFaint 弱化，价格+颜色首行不再三种样式混排"),
             ("✅", "品类 chips 行尾渐隐（「鞋」chip 半透明过渡）"),
         ],
         shot="r2-10-W10-wishlist.png", wire="wf-w10.png",
         points=[
             (1, "✅ 连体分段·与回顾页同规格已落地"),
             (2, "✅ 右缘渐隐已落地"),
             (3, "✅ 品类色块占位已落地"),
             (4, "✅ 域名弱化次级行已落地"),
         ]),
]

COMMONS = [
    ("C1", "名称/标签截断", "W1/W3", "⚠️", "两行+动态字号落地；包格 9 字名仍省略 → it-037 O1（r2-01/03 视觉，0963e3c）"),
    ("C2", "卡图适配与邻物残片（素材根因）", "W1/W3", "📌", "刻意零改素材；validate_assets --strict 待 it-032 合入后重出收口（be076bf）"),
    ("C3", "触控目标 <44dp", "W3/W4/W5/W7/W8", "✅", "分页/✕ 126×126px=48dp、chips 44–48dp、图标 48dp（dump+装机，0963e3c）"),
    ("C4", "状态栏三段式色阶接缝", "W4/W5/W7/W9", "✅", "四路由 appbar y309→174、状态栏像素白（5656189）"),
    ("C5", "破坏性操作无警示", "W5/W7/W10", "✅", "删除 error 红视觉 (179,38,30)；确认弹窗核实 it-029 既有（5656189）"),
    ("C6", "禁用/演示态语义不足", "W9", "✅", "解锁文案/演示徽标/提醒联动/灰字全部视觉实锤（5656189）"),
    ("C7", "记录无日期、计数无语义", "W1/W8", "✅", "评论 2026/09/24；hero 角标像素 1729、网格 818；n/n a11y（0963e3c）"),
    ("C8", "导出文案区被裁+标签硬截", "W6", "✅", "文案 bounds 完整+标签 ellipsis+输入统一（c7269fc）"),
    ("C9", "品类分布同色不可对应", "W9", "✅", "6 档绿色阶+段内白字直标（5656189）"),
    ("C10", "无图心愿卡占位空洞", "W10", "✅", "品类色块占位（c7269fc）"),
    ("C11", "chips/分段形制不统一", "W3/W8/W10", "✅", "ScrollFade 渐隐 + SegmentedToggle 共享（c7269fc）"),
    ("C12", "文案与格式不统一", "W1/W8/W9", "✅", "随机一套/YYYY/MM/DD/灰字说明（c7269fc+5656189）；W1 混入灰另列 it-037"),
]

SPLITS = [
    ("wardrobe / it-037（候选）· 复验残留三小项", [
        ("O1", "包格长名 fitStep/窄格两行策略微调，消除 9 字省略", "C1 ⚠️"),
        ("O2", "「混入心愿」pill 改正文色描边（原线框要点①，提案遗漏）", "W1 ⚠️"),
        ("O3", "角色层 ✓ 改 badge、Mia 行补件数辅助（基线 P2 范围外）", "W2 P2"),
    ]),
    ("wardrobe / it-035 执行 · 素材重出（依赖 it-032 合入）", [
        ("O1", "统一 4:3 出图 + 裁剪净化 6 残片；validate_assets.py --strict 全绿收口", "C2 📌"),
        ("O2", "效果图主体上下安全边距 ≥5%（effect-commute 脚贴边）", "素材层"),
    ]),
]

TESTS = [
    ("构建与安装", "APK 14:59 构建、find -newer 源码=0；install -r 成功"),
    ("重截规模", "19 个页面/过程态（r2-*），含 W6 滚动态、打卡双按钮、混入提示态"),
    ("C1–C12 矩阵", "✅10 / ⚠️1（C1 包格长名）/ 📌1（C2 素材债务）；新增 P0×0 P1×0 P2×4"),
    ("分页热区", "dump 节点「上一张」「下一张」=126×126px=48×48dp"),
    ("✕ 热区", "「移除该格」126×126px=48×48dp；视觉 16dp"),
    ("日期统一", "W7 评论 2026/09/24（dump+视觉）；W8 hero 角标像素 1729 / 网格 818"),
    ("状态栏", "W4/W5/W7/W9 appbar y174–249（基线 y309+），白底路由像素纯白"),
    ("混入提示", "dump「愿望件已附加，滑到候选最后可见」+ 5s 收起（it-036 装机）"),
    ("打卡闭环", "单按钮→双按钮→撤销复测通过（r2-15 dump）"),
    ("数据断言", "筛选「共 5 件」复测 = JSON Leo TOP 5；角色隔离不变"),
    ("回归", "19 态导航 0 崩溃；实施期 4 轮 testDebugUnitTest 62 项全绿"),
    ("误报勘定", "评论删除确认 it-029 已存在；基线 18dp 系字形子节点 bounds"),
]
EVIDENCE = [
    ("r2-09-W9-review.png", "比例条 6 档色阶+段内白字直标、解锁说明，C6/C9 落地实锤"),
    ("r2-10-W10-wishlist.png", "品类色块占位+连体分段+域名独立行，C10/C11 落地实锤"),
    ("r2-07b-W7-scrolled.png", "删除 error 红+评论 2026/09/24+白底无接缝，C4/C5/C12 落地实锤"),
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


def tagspan(v):
    bg = {"✅": "#2E7D32", "⚠️": "#FF7D00", "📌": "#86909C"}.get(v)
    if bg:
        return f'<span class="tag" style="background:{bg}">{v}</span>'
    return f'<span class="tag {v.lower()}">{v}</span>'

A = ASSETS


def esc(s):
    return html.escape(s, quote=False)


def sev_counts(th):
    from collections import Counter
    c = Counter(s for s, _ in th["problems"])
    return " ".join(f"{k}×{v}" for k, v in c.items() if v)


def theme_page(th, pageno):
    probs = "".join(
        f'<div class="prob">{tagspan(p[0])}{esc(p[1])}</div>'
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
        f"<td class='c'>{tagspan(c[3])}</td><td>{esc(c[4])}</td></tr>"
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
