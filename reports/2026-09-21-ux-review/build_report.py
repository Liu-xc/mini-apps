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
    title="吃啥 × 衣橱",
    sub="全页面截图走查 · 问题清单 · 改版线框",
    date="2026-09-21",
    scope="2 应用 · 27 张实拍 · 6 项交互实测",
    summary="对吃啥（eats）与衣橱（wardrobe）两应用共 27 个页面状态做模拟器实机截图走查，"
            "逐页视觉评审并以 uiautomator 断言与源码核对做交互实测。汇总为 P0/P1/P2 分级问题清单，"
            "对两个 P1 级问题给出灰盒改版线框：蓝色数字徽标与右侧改版要点一一对应。",
    cover_shots=["e-01-W1-默认.png", "w-01-W1-搭配.png"],
    stats=[("27", "走查页面"), ("0", "P0 级问题"), ("2", "P1 级问题"), ("6", "交互项实测")],
    methods=[("实机截图走查", "模拟器 1080×2400 实装最新 main 构建，逐页导航截图 + uiautomator 文本树留档"),
             ("视觉逐页评审", "每页按版式/层级/文案/留白四维检查，问题落到具体位置并分级"),
             ("交互实测验证", "关键结论以行为断言或源码核对实锤（Compose 控件不暴露 selected，采用行为对比）")],
)

SECTIONS = [
    dict(label="吃啥 · EATS", kicker="PART 01", app="eats",
         shots=["e-01-W1-默认.png", "e-04-W2-地图.png", "e-06-W3-列表.png", "e-11-W7-回顾.png"]),
    dict(label="衣橱 · WARDROBE", kicker="PART 02", app="wardrobe",
         shots=["w-01-W1-搭配.png", "w-05-W3-衣橱.png", "w-10-W9-回顾.png", "w-12-W10-心愿.png"]),
]

THEMES = [
    dict(app="eats", no="E1", title="吃啥 · 干啥页抽中态（附全页走查）",
         concl="W1 抽中落定是本应用最高频的「高潮时刻」，但抽中后卡组整片飞空、结果块孤悬屏底，"
               "高潮被 ~40% 屏的空白稀释；其余页面（地图/列表/详情/回顾/长图）状态良好，仅存观感级问题。",
         problems=[
             ("P1", "抽中落定后卡组区域整片空白（e-02 实拍：卡序胶囊悬在顶部，中部 ~900px 空档，结果块贴屏底）——抽中反馈被稀释"),
             ("P2", "地图页两侧与状态栏区彩色噪声条带（e-04/e-04b），「回位」后不消失——疑模拟器截图管线伪影，需真机复核，暂不计入缺陷"),
             ("P2", "列表页 FAB「添加去处」遮挡末行卡片的次数统计与 #聚餐 标签（e-06 右下实拍）——LazyColumn 底部未为 FAB 预留 contentPadding"),
             ("P2", "W1 卡片图区下部留白偏大（e-01）：内容只占卡面一半，卡底空白约 400px"),
             ("P2", "W1 分类 chips 状态跨启动持久（规格声明行为），但「玩+出门+在家」多选组合态无任何「已筛选」提示，二次进入不易察觉"),
         ],
         shot="e-02-W1-抽中落定.png", wire="wf-eats-落定改版.png",
         points=[
             (1, "结果块上移并撑满卡组原区：卡序胶囊保留在上，块内标题/候选数/双按钮随块放大，空白归零"),
             (2, "抽中态底部主按钮改「换一张（返回卡组）」单一出口，避免与块内「再抽」双入口打架"),
         ]),
    dict(app="wardrobe", no="W1", title="衣橱 · 衣橱页卡片交互（附全页走查）",
         concl="衣橱网格的视觉与信息密度是两应用最好的页面之一，但卡片单击被绑定为「进编辑」，"
               "W5 详情（大图/评论/穿搭反查）从此页无路可达——高频的「看看这件」被低频的「改这件」抢占。",
         problems=[
             ("P1", "衣橱卡片单击直达「编辑衣物」，长按=删除；W5 详情只能从搭配槽位/穿搭详情/回顾进入（源码实锤：ItemCard combinedClickable(onClick=onEdit)）；看图与反查的最短路径缺失，且误触成本高"),
             ("P2", "W6 导出面板长图预览仅 ~40% 屏宽居中，prompt 文字在预览中不可读（已知设计取舍，it-017 注记「预览放大留待后续」）"),
             ("P2", "W9 回顾页真实数据 0 打卡时，「最百搭 TOP3」显示「穿 0 次」「利用率 0%」的空指标——应改空态引导（先去打卡）"),
             ("P2", "W7 详情「这套包含」中 上装·白T恤 显示为绿色 T 恤：种子数据命名与内容不符（数据问题，非 UI）"),
         ],
         shot="w-05-W3-衣橱.png", wire="wf-wardrobe-衣橱卡详情入口.png",
         points=[
             (1, "卡片单击改为进 W5 详情：大图/评论/穿搭反查一触直达，与 eats 列表行→详情的语义对齐"),
             (2, "右上更多菜单语义收窄为「编辑」（原与单击重复），长按删除保持不变"),
             (3, "名称行尾加向右箭头，暗示卡片可点进详情"),
         ]),
]

COMMONS = [
    ("C1", "卡片点击语义不一致", "wardrobe W3（eats W3 为正例）", "P1",
     "编辑收进右上角更多菜单，卡片单击进详情；见 W1 线框"),
    ("C2", "内容不满时的空档管理", "eats W1 抽中态 · W1 卡片 · W6 预览", "P2",
     "固定高度容器遇到少内容时按内容收缩或让结果块接管空间；见 E1 线框"),
    ("C3", "待真机复核项", "eats W2 地图噪条", "P2",
     "模拟器截图管线伪影可能性高；真机复核后再定是否修渲染"),
]

SPLITS = [
    ("eats / it-012 · 干啥页抽中态重排", [
        ("O1", "结果块上移撑满卡组区，消除空屏", "E1"),
        ("O2", "抽中态底部改「换一张」单一出口", "E1"),
        ("O3", "列表 LazyColumn 底部 contentPadding 预留 FAB 高度", "E1"),
    ]),
    ("wardrobe / it-024 · 衣橱卡交互语义", [
        ("O1", "卡片单击 → W5 详情", "W1"),
        ("O2", "右上更多菜单改「编辑」直入，长按删除不变", "W1"),
        ("O3", "名称行 › 提示 + W9 空指标改打卡引导", "W1"),
    ]),
]

TESTS = [
    ("抽中落定 → 结果块/卡序/再抽", "功能正常；布局空屏判 P1（E1 实拍证据）"),
    ("衣橱卡片单击", "直达编辑表单；无详情路径（源码 combinedClickable 实锤）"),
    ("转正表单无照片提交", "按钮置灰 + 原因文案「先拍一张实物照」——it-023 夜间修复复验通过"),
    ("地图回位按钮", "定位正常；噪声条带不消失（疑截图管线伪影，待真机）"),
    ("长图生成 → 预览 → 存相册", "eats/wardrobe 双端全链路成功（e-12/夜间 QA）"),
    ("落账 → 拔草 → 撤销 snackbar", "撤销闭环成功（it-008 US-10 复验）"),
]

EVIDENCE = [
    ("e-02-W1-抽中落定.png", "E1 P1 证据：抽中后卡组区整片空白，结果块孤悬屏底"),
    ("e-06-W3-列表.png", "E1 P2 证据：FAB 遮挡末行统计与标签（右下角）"),
    ("w-13-W10-转正表单置灰.png", "交互实测：无实物照时提交按钮置灰并明示原因"),
]


# ═══════════ 以下为生成逻辑（一般不动） ═══════════

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
