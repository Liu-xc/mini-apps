#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""make_report.py — darkroom UI 审查报告（基于 ui-audit report_template.py，数据区已填）。

用法: python3 make_report.py [输出.html，默认 ../report.html]
之后: check-html → html2pdf-next.js → pdf_qa → 视觉验收
布局铁律: 全程 flex 流式，禁用 position:absolute。
"""
import html
import os
import sys

ASSETS = "assets"  # 截图/线框所在目录（相对输出 HTML）

# ═══════════════════════ ★数据区·按评审填写★ ═══════════════════════

META = dict(
    title="显影 DARKROOM",
    sub="全页面 UI 走查 · 功能自测 · 改版线框",
    date="2026-09-27",
    scope="1 应用 · 4 页面 · 11 张实机截图",
    summary="对显影 darkroom 的 4 个页面（选图 / 显影台 / 成片 / 设置）完成实机截图走查与 11 项交互实测："
            "发现 1 个 P0 崩溃（已当场修复并回归），另汇总 5 个 P1、5 个 P2 视觉与体验问题；"
            "每主题给出灰盒改版线框，蓝色徽标与右侧要点编号一一对应。",
    cover_shots=["darkroom-01-pick.png", "darkroom-04-result.png"],
    stats=[("4", "走查页面"), ("1", "P0 级问题"), ("5", "P1 级问题"), ("11", "交互项实测")],
    methods=[
        ("实机截图走查", "模拟器 1080×2400，四页 + 显影三阶段 / 拖动 / 分享等过程态逐页截图，按根 DESIGN.md §2–§5 逐条对表。"),
        ("交互实测验证", "uiautomator 断言 + 传感器注入 + MediaStore 落盘校验；关键结论均给出可复现的操作与读数。"),
        ("源码核对", "崩溃堆栈、速度档、卡面布局均回溯到具体文件与行号，区分「视觉观感」与「实现事实」。"),
    ],
)

# 分节：每节 (分节页标题, 英文 kicker, 截图文件名列表, 主题 app 标识)
SECTIONS = [
    dict(label="显影 · DARKROOM", kicker="PART 01", app="darkroom",
         shots=["darkroom-01-pick.png", "darkroom-02-develop-p3.png",
                "darkroom-04-result.png", "darkroom-05-settings.png"]),
]

# 主题页（每页一节）。shot=现状截图 wire=改版线框（assets/ 下文件名）
THEMES = [
    dict(app="darkroom", no="D1", title="选图页 · 顶行安全区与首屏节奏",
         concl="顶行嵌入状态栏触摸死区（四页同源缺陷的首例）：⚙ 中心点点击无效；"
               "首屏下半约 800px 空白，设置入口重复且其一失灵。",
         problems=[
             ("P1", "顶行 DARKROOM/⚙ 嵌在状态栏窗口内（实测窗口高 128px）：按钮中心 y=111 点击无响应，"
                    "y≥130 才生效——W2/W3/W4 顶行同病，← 返回同样失灵。"),
             ("P2", "设置入口两处：顶行 ⚙（失灵）与示例图下方「设置」chip，语义重复。"),
             ("P2", "内容止于 y≈1574，底部约 800px 空白；footer「全程离线」未按线框贴底，视觉重心偏上。"),
         ],
         shot="darkroom-01-pick.png", wire="wf-header.png",
         points=[
             (1, "状态栏 insets 下推页头：安全区虚线以下才排控件，四页页头统一。"),
             (2, "⚙ 触控目标 44dp 完整落在安全区下，中心点可点（现状 y=111 落在状态栏窗口内被吞）。"),
             (3, "移除重复「设置」chip、footer 下移贴底，消解中部 800px 空白。"),
         ]),
    dict(app="darkroom", no="D2", title="显影台 · 卡面签名区溢出",
         concl="显影台是全 App 主视觉，但白框被容器高度裁掉 130px：日期章与水印冲出卡面、"
               "撞上进度读数——每次显影全程可见。",
         problems=[
             ("P1", "签名区冲出白框：weight 槽高只给 1028px（CardLayout 需 1158px），"
                    "白底裁到 y=1624，日期章 32px、水印整条落在黑底，并与读数行相撞（100% 时重叠 16px）。"),
             ("P2", "槽口拟物件 #1E1F17 与页底 #14160F 仅差 10 级灰，线框中的「槽口」几乎不可见。"),
             ("P2", "卡片上下各留约 450px 空白，进度区与卡脚的视觉关系松散。"),
         ],
         shot="darkroom-02-develop-p3.png", wire="wf-develop.png",
         points=[
             (1, "卡宽改由可用槽高反解（宽 = 槽高 ÷ 1.2），出纸 / 落定全程不越界。"),
             (2, "签名区（标题 / 日期 / 水印）整体收回白框内，按 52% 互斥分栏。"),
             (3, "进度读数行与卡脚保持净距；槽口描边加深至可读。"),
         ]),
    dict(app="darkroom", no="D3", title="成片页 · 卡面重叠与首屏 CTA",
         concl="卡面标题与日期章字形直接重叠；存图/存视频贴屏幕底被手势条压住，"
               "导出进度条排在首屏之外——导出全程看不到进度。",
         problems=[
             ("P1", "标题与日期章重叠（「Summer 2|026 09 27」）：title 域右界 60% > stamp 域左界 58%，"
                    "且日期右对齐绘制不夹断，长日期一路侵入标题区。"),
             ("P1", "存图片/存视频位于 y2343–2392 被手势条压住半截；LinearProgressIndicator 排在按钮下方＝首屏外，"
                    "40 次轮询未捕获到「冲洗中」（源码有、用户看不见）。"),
             ("P2", "snackbar 弹出时完全遮挡存/分享按钮行；与贴底 CTA 叠加，误触率高。"),
             ("P2", "分享图片/分享视频/再洗一张首屏不可见，需滚动后才发现，线索不足。"),
         ],
         shot="darkroom-04-result.png", wire="wf-result.png",
         points=[
             (1, "标题域止于 52%、日期章左边界夹断，两域互斥不再重叠。"),
             (2, "进度条上移贴 CTA 顶部，导出反馈进首屏。"),
             (3, "存图/存视频上移完整入屏；分享与再洗一张收为一行次级操作。"),
         ]),
    dict(app="darkroom", no="D4", title="设置 · 速度档只改标签不改时长",
         concl="落盘 develop_speed=SLOW 后实测显影仍约 8.8s：clock 初始化固定 STANDARD，"
               "prefs 只流进 UI 标签——慢洗/快显两档无效（US-2 失效）。",
         problems=[
             ("P1", "DarkroomViewModel.kt:72 固定 DevelopClock(STANDARD)；:79 的 collect 只更新 state.speed。"
                    "header 显示「慢洗」但实测 8.8s≈8s 标准档；导出时间线却用所选档 → 视频与预览不一致。"),
             ("P2", "页头「设置」标题/返回同样嵌在状态栏区（C1 同源），返回按钮中心点击无效，需按系统返回键。"),
         ],
         shot="darkroom-05-settings.png", wire="wf-speed.png",
         points=[
             (1, "DataStore 选档即写入（已实测落盘 develop_speed=SLOW）。"),
             (2, "每次显影前按所选档重建 DevelopClock，header 显示时长与实测一致（12/8/4s 对表）。"),
         ]),
]

# 跨应用共性问题表
COMMONS = [
    ("C1", "顶行嵌入状态栏：触摸死区 + 视觉顶格（全页缺 statusBars insets）",
     "D1 · D2 · D3 · D4", "P1", "insets 下推页头，44dp 命中区完整落在安全区下（线框 D1①②）"),
    ("C2", "卡面几何与容器不匹配：W2 溢出白框撞读数、W3 标题×日期重叠",
     "D2 · D3", "P1", "CardLayout 按可用高度反解 + 签名域互斥（线框 D2①② / D3①）"),
    ("C3", "速度档运行时不生效（仅标签与导出时间线变化）",
     "D4", "P1", "DevelopSpeed.collect 重建 clock（线框 D4②）"),
    ("C4", "首屏底部 CTA 裁切、导出进度屏外",
     "D3", "P1", "进度条上贴 CTA、按钮行上移入屏（线框 D3②③）"),
    ("C5", "首屏底部大面积留白（W1/W4 内容止于 60% 高度）",
     "D1 · D4", "P2", "footer / 内容下移贴底或增密（线框 D1③）"),
    ("C6", "拟物槽口对比度不足、不可见",
     "D2", "P2", "槽口描边加深（线框 D2③）"),
]

# 落地拆分：迭代名 → [(线框号, 内容, 对应主题)]
SPLITS = [
    ("darkroom / it-002 · 稳定性与卡面几何", [
        ("O1", "P0：manifest 补 VIBRATE 权限（已随本次审查落盘）+ 定影触感回归", "D2"),
        ("O2", "系统栏 insets 下推全部页头，消除顶行触摸死区", "D1"),
        ("O3", "CardLayout 按可用高度反解、签名域互斥（W2 溢出与 W3 重叠一次修）", "D2 · D3"),
    ]),
    ("darkroom / it-003 · 功能收口与成片页信息架构", [
        ("O4", "DevelopSpeed.collect 重建 clock，速度档实测对表 12/8/4s", "D4"),
        ("O5", "成片页 CTA/进度上移入屏，分享与再洗一张收行", "D3"),
        ("O6", "W1/W4 首屏节奏收口（footer 贴底、去重复设置入口）", "D1"),
    ]),
]

# 附录：实测记录 + 证据截图（图高自动 88mm，三张以内）
TESTS = [
    ("定影触感崩溃（P0）", "复现 3 次堆栈一致：缺 VIBRATE → Haptics.confirm 抛 SecurityException；补权限后全流程回归通过"),
    ("三阶段显影", "潜影 7% / 浮现 54% / 定影 97% 逐帧可辨，线性时钟约 12.5%/s"),
    ("药水条拖动", "82% 拖至约 51% 生效且自动续播（seek + 松手续播）"),
    ("甩一甩 boost", "传感器注入模长 5↔26：t=3.1s 达 74%（自然上限 38.6%），boost ≈ +10%/次"),
    ("速度档", "DataStore 落盘 SLOW ✓；实测时长 8.8s ≠ 12s ✗（P1，源码 ViewModel:72）"),
    ("存图片", "snackbar + 文件：Pictures/显影/显影_2026-09-27.jpg 119KB"),
    ("存视频", "snackbar + 文件：Movies/显影/显影_2026-09-27.mp4 11.1MB；进度条在首屏外（P1）"),
    ("分享面板", "系统 chooser 直出，图片缩略图正常"),
    ("三入口", "Photo Picker 零权限选图→显影 ✓；相机 Shutter ✓；示例图 ✓"),
    ("设置持久化", "darkroom_prefs.preferences_pb 写入 develop_speed=SLOW / 甩一甩开关"),
    ("顶行触摸", "tap y=111 无效 / y=130 生效；StatusBar 窗口 (0,0) fillx128"),
]
EVIDENCE = [
    ("darkroom-02-develop-shake.png",
     "甩一甩实测：t≈3.1s 进度 74% ≫ 自然上限 38.6%（boost 生效）；同帧可见日期章溢出白框、水印与 74% 读数相撞"),
    ("evidence-card-overlap.png",
     "成片页标题与日期章字形重叠（Summer 2|026 09 27）——CardLayout 域交叉的直接证据"),
    ("darkroom-09-save-snackbar.png",
     "存图片成功 snackbar「已存入相册 · 显影」+ 分享 action，MediaStore 同步可见"),
]

# ═══════════════════════ 以下为生成逻辑（与 report_template.py 一致） ═══════════════════════

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
.shotcol { width: 72mm; }
.shotcol img { width: 72mm; height: 160mm; object-fit: cover; object-position: top;
  border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; background: #fff; }
.shotlabel { display: flex; align-items: center; gap: 2mm; margin-top: 2.2mm; font-size: 9pt; font-weight: 700; }
.shotlabel .dot { width: 2.6mm; height: 2.6mm; border-radius: 50%; }
.d-now { background: #F53F3F; } .d-new { background: #2F6BFF; }
.shotlabel span.cap { font-weight: 400; color: #86909C; font-size: 8pt; margin-left: auto; }
.points { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2.6mm; }
.pt { font-size: 8.8pt; line-height: 1.5; color: #4E5969; overflow-wrap: anywhere; word-break: break-word; }
.pt .n { display: inline-flex; width: 5mm; height: 5mm; border-radius: 50%; background: #2F6BFF;
  color: #fff; font-size: 8pt; font-weight: 700; align-items: center; justify-content: center;
  margin-right: 1.8mm; vertical-align: -1mm; }
table { border-collapse: collapse; width: 100%; }
th { font-size: 9pt; text-align: left; color: #86909C; font-weight: 600;
  border-bottom: 0.45mm solid #C9CDD4; padding: 2mm 2.5mm; }
td { font-size: 9.3pt; line-height: 1.5; padding: 1.55mm 2.5mm; border-bottom: 0.3mm solid #E5E6EB;
  vertical-align: top; }
td.c { text-align: center; }
.h2 { font-size: 13pt; font-weight: 800; margin: 3.4mm 0 2mm; }
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
.appendix-shot img { width: 39.6mm; height: 72mm; object-fit: cover; object-position: top;
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
  <div class="phead"><span class="no">00</span><span class="t">总评 · 方法与共性问题</span></div>
  <p class="lead">{esc(META['summary'])}</p>
  <div class="statrow">{stats}</div>
  <div class="h2">评审方法</div>
  <div class="mrow">{methods}</div>
  <div class="h2">跨应用共性问题（优先修）</div>
  <table>
    <tr><th style="width:9mm">#</th><th>问题</th><th style="width:36mm">涉及页面</th>
        <th style="width:12mm">级别</th><th style="width:52mm">修法（对应线框）</th></tr>
    {crows}
  </table>
  <div class="pfoot"><span>总评</span><span style="margin-left:auto">{pageno}</span></div>
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
  <p class="lead" style="font-size:9pt">现状截图与改版线框见 {A}/ 目录；本报告由 make_report.py 生成，可改数据重出。</p>
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
