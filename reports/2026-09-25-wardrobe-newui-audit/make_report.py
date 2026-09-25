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
    title="衣橱 wardrobe · AI 新三面审美走查",
    sub="W11 设置 / W12 对话 / W5 补抠状态条 · 截图走查 · 改版线框",
    date="2026-09-25",
    scope="3 新页面 · 10 状态截图",
    summary="对 it-040/it-041 刚落地的 W11 设置页、W12 对话页与 W5 补抠状态条共 10 个状态"
            "进行实机截图走查与 13 项交互实测，汇总为按严重度分级的问题清单"
            "（P0×0 / P1×7 / P2×14），并给出 4 组灰盒改版线框：蓝色数字徽标对应右侧改版要点。",
    cover_shots=["wardrobe-06-w12-tools-reply.png", "wardrobe-01-w11-settings-top.png"],
    stats=[("10", "状态截图"), ("0", "P0 级问题"), ("7", "P1 级问题"), ("13", "交互项实测")],
    methods=[
        ("实机截图走查", "emulator-5554 最新构建（9b63b48），逐状态导航采集 10 张 1080×2400 截图，"
                    "演示/正常双模式覆盖空态、流式、错误、候选、已抠等过程态"),
        ("视觉模型逐页评审", "对 10 页按「时装编辑 print」风格轴逐页评审 P0/P1/P2，"
                     "再经仓库标准校准：热区实测、对比度按 inkFaint≥3:1 口径复核定级"),
        ("交互实测验证", "13 项 uiautomator/源码断言（E1–E13）：含杀进程续聊、自检成功/401 失败、"
                    "补抠四态、alpha 检测 36 图 ground truth 交叉核对"),
    ],
)

SECTIONS = [
    dict(label="衣橱 · WARDROBE", kicker="PART 01", app="wardrobe",
         shots=["wardrobe-01-w11-settings-top.png", "wardrobe-06-w12-tools-reply.png",
                "wardrobe-09-w5-candidate-checker.png"]),
]

THEMES = [
    dict(app="wardrobe", no="W5-A", title="W5 衣物详情 · 补抠状态条",
         concl="核心动作「去背景」以幽灵按钮呈现，视觉重量低于品名——最重要操作像说明文字。",
         problems=[
             ("P1", "全宽描边 CTA「去背景 · 一键透明底」灰字弱态，易被误读为装饰说明（截图08）"),
             ("P1", "颜色/材质描述等关键信息使用弱文本色，信息被降级"),
             ("P2", "标签 chips 视觉偏挤、白底与纸绿边界弱；小节标题与品名词声不一"),
             ("P2", "同卡三种状态（原图/候选/已抠）图区内边距不一致"),
         ],
         shot="wardrobe-08-w5-statusbar-state.png", wire="wf-t1-revised.png",
         points=[
             (1, "主 CTA 升实心填充高对比（白字 6.7:1），成为页面唯一主动作"),
             (2, "描述升主文本色（14.6:1），关键信息不再降级"),
             (3, "小节标题升衬线与品名同声部；chips 间距统一"),
         ]),
    dict(app="wardrobe", no="W12-A", title="W12 对话页 · 空态与引导",
         concl="空态顶置导致 1300px+ 空洞，示例引导是不可点的弱灰文本。",
         problems=[
             ("P1", "内容顶置、到输入栏留 1339px 空洞，重心失衡"),
             ("P1", "引导 hint 用弱文本色，是全屏唯一引导却最弱"),
             ("P2", "示例为纯文本不可点；标题与占位逐字重复；折行孤字"),
             ("P2", "应用内纸飞机与键盘蓝色回车双发送语义并存"),
         ],
         shot="wardrobe-03-w12-empty.png", wire="wf-t2-revised.png",
         points=[
             (1, "空态块垂直居中，重心落在屏幕中部"),
             (2, "示例改可点 chip 一键提问，引导升主文本色"),
             (3, "标题改问句、占位改指令，措辞错开"),
         ]),
    dict(app="wardrobe", no="W12-B", title="W12 对话页 · 工具条与错误语言",
         concl="工具调用以 raw 英文名+调试文本平铺，错误是距失败轮次半屏远的裸红字。",
         problems=[
             ("P1", "「已查衣橱：search_items」原始工具名与结构化结果直接铺给用户、多卡重复表头（截图06）"),
             ("P1", "错误行裸红字无容器无图标，距失败消息 552px 因果断裂；有重试无「去设置」（截图07）"),
             ("P1", "回复与工具卡顶部锚定，最新内容距输入栏 785px+ 空洞"),
             ("P2", "回复完成后无复制/追问/重生成动作，会话断头"),
         ],
         shot="wardrobe-06-w12-tools-reply.png", wire="wf-t3-revised.png",
         points=[
             (1, "工具折叠为中文摘要「查了衣橱 · n 次」，可展开看结果"),
             (2, "错误容器化紧贴失败轮次，网络/Key 类直达「设置」"),
             (3, "回复下给复制/追问/重生成动作 chips"),
         ]),
    dict(app="wardrobe", no="W11-A", title="W11 设置页 · 自检状态与语义",
         concl="设置页没有常驻自检状态，用户无法知道当前 Key 是否可用；版本行外泄 ADR 编号。",
         problems=[
             ("P1", "重进设置页无「上次自检结果」，只能再点一次试错（截图01）"),
             ("P1", "版本行「联网仅用于模型直连（ADR-024）」把内部决策编号暴露给用户（截图02）"),
             ("P2", "「清除 Key」与主 CTA 同色同级（已有二次确认兜底，仅色彩语义分级缺失）"),
             ("P2", "表单右缘三档不齐；用量大数字缺「累计」口径标注"),
         ],
         shot="wardrobe-01-w11-settings-top.png", wire="wf-t4-revised.png",
         points=[
             (1, "自检结果持久化为常驻状态行（通过/错误 + 检测时间）"),
             (2, "回退动作改中性弱色，与主按钮分层"),
             (3, "版本行去 ADR 黑话；用量标注「累计」口径"),
         ]),
]

COMMONS = [
    ("C1", "关键指引误用弱文本 token（hint/主 CTA/工具正文用 inkFaint；数值达 3:1 但语义应为 ink）", "W5\u00a0·\u00a0W12\u00a0·\u00a0W11", "P1", "指引与主动作文案改 ink；次要说明保留 inkFaint（W5-A ② · W12-A ②）"),
    ("C2", "W12 会话列顶部锚定，最新内容与输入栏之间 785–1481px 空洞", "W12 三态", "P1", "底部锚定/可靠跟随最新，空态垂直居中（W12-A ①）"),
    ("C3", "技术词汇外泄：raw 工具名、ADR-024、调试式结果文本", "W11\u00a0·\u00a0W12", "P1", "工具条中文化折叠；版本行去 ADR 编号（W12-B ① · W11-A ③）"),
    ("C4", "错误/状态语言两套：W12 裸红字 vs W5 容器状态带；错误距失败轮次远、无去设置", "W12-07 vs W5", "P1", "错误容器化紧贴轮次 + 直达设置（W12-B ②）"),
    ("C5", "主 CTA 幽灵态（描边+弱灰字低于品名权重）", "W5-08", "P1", "实心填充高对比（W5-A ①）"),
    ("C6", "自检结果不持久，重进无法判断 Key 可用性", "W11-01", "P1", "常驻状态行含时间戳（W11-A ①）"),
    ("C7", "候选态状态带对比 1.07 几乎不可见，且无放大核对边缘入口", "W5-09", "P1", "状态带加 hairline/抬对比；大图可放大核对"),
]

SPLITS = [
    ("wardrobe / it-043 · 新三面审美收口（P1 批）", [
        ("O1", "W5 主 CTA 实心化 + 描述升主文本 + 状态带容器统一（跨原图/候选/已抠三态）", "W5-A\u00a0·\u00a0C1\u00a0·\u00a0C5"),
        ("O2", "W12 空态垂直居中 + 示例可点 chip + 会话底部锚定（修自动滚动竞态）", "W12-A\u00a0·\u00a0C1\u00a0·\u00a0C2"),
        ("O3", "工具条中文折叠摘要 + 错误容器化紧贴失败轮次 + 网络/Key 类直达「设置」", "W12-B\u00a0·\u00a0C3\u00a0·\u00a0C4"),
        ("O4", "W11 自检结果持久化状态行 + 版本行去 ADR 黑话 + 回退动作分色", "W11-A\u00a0·\u00a0C6\u00a0·\u00a0C3"),
    ]),
    ("wardrobe / it-044 · P2 打磨批", [
        ("O5", "右缘对齐线、W5 图区内边距单值、chips 间距统一", "C9"),
        ("O6", "回复气泡动作行（复制/追问/重生成）+ IME 回车与应用内发送统一", "C11"),
        ("O7", "字体声部与用量口径写入 05-design-system；候选态说明棋盘=透明底", "C10\u00a0·\u00a0W5-09"),
    ]),
]

TESTS = [
    ("E1 导航", "✓ W3 ⚙ → W11，白顶栏沉浸，结构齐"),
    ("E2 用量", "✓ 对话后 3508 → 6016 tokens 实时更新"),
    ("E3 Key mask", "✓ 1b0d***EW0f；全仓无明文 Key"),
    ("E4/E5 自检", "✓ 连通正常·glm-4-flash；[401] 透传"),
    ("E6 空态", "✓ 清会话后 EmptyState + 示例 hint"),
    ("E7 工具链", "✓ 4 轮 search_items，回复引用真实单品"),
    ("E8 续聊", "✓ force-stop 后历史完整恢复（US-41c）"),
    ("E9 错误横幅", "✓ 分类红字 + 重试/知道了"),
    ("E10 补抠四态", "✓ 棋盘格 46,268px 指纹 + 还原可逆"),
    ("E11 alpha 检测", "✓ 36 图 ground truth 全一致"),
    ("E13 热区", "✓ 关键按钮实测全 48dp"),
]

EVIDENCE = [
    ("wardrobe-07-w12-error-retry.png", "错误态实测：分类文案 + 重试/知道了成对出现（E4/E5；此 401 为自动化误输坏 Key 触发）"),
    ("wardrobe-09-w5-candidate-checker.png", "候选预览实测：棋盘格 46,268px + 保留钮 2,028px 像素指纹；保留后转已抠态、还原可逆（E10）"),]


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
.appendix-shot img { width: 39.6mm; height: 88mm; object-fit: cover; object-position: top;
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
    appname = next((s["label"].split(" · ")[0] for s in SECTIONS if s["app"] == th["app"]), "")
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
  <div class="pfoot"><span>{esc(appname)} · UX 评审报告</span><span style="margin-left:auto">{pageno}</span></div>
</section>"""


def divider(sec, pageno):
    imgs = "".join(f'<div class="phone"><img src="{A}/{esc(s)}" alt=""></div>' for s in sec["shots"])
    n = sum(1 for t in THEMES if t["app"] == sec["app"])
    return f"""
<section class="page cover">
  <div class="kicker">{esc(sec['kicker'])}</div>
  <div class="hairline"></div>
  <h1 class="mid">{esc(sec['label'])}</h1>
  <div class="sub">{n} 个页面 · 现状与改版线框对照</div>
  <div class="phones">{imgs}</div>
  <div class="meta"><span>UX 评审报告 · {esc(META['date'])}</span><span style="margin-left:auto">{pageno}</span></div>
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
  <div class="phead"><span class="no">00</span><span class="t">总评 · 方法与共性问题</span></div>
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
  <div class="phead"><span class="no">A</span><span class="t">附录 · 交互实测记录与资产</span></div>
  <div class="h2">实测验证（uiautomator / 源码核对）</div>
  <table><tr><th style="width:34mm">项目</th><th>结论</th></tr>{trows}</table>
  <div class="h2">实证截图</div>
  <div class="appendix-shot">{ev}</div>
  <div class="pfoot"><span>附录</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"><title>{esc(META['title'])} · UX 评审报告 · {esc(META['date'])}</title>
<style>{CSS}</style></head>
<body>{''.join(pages)}</body></html>"""

    out = sys.argv[1] if len(sys.argv) > 1 else "report.html"
    with open(out, "w", encoding="utf-8") as f:
        f.write(doc)
    print("written", out, len(doc), "bytes")


if __name__ == "__main__":
    build()
