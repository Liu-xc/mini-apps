#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""R2 实施效果对照报告（before/after）· flex 流式，禁 absolute"""
import html, os, sys

A = "assets"

# (no, 标题, 结论, before图, after图, [(要点)], 断言)
PAGES = [
    ("E1", "吃啥 · 首页（筛选与卡组）",
     "筛选入口与类型 chips 视觉分离，卡序胶囊移出卡面不再压露边，卡片信息减负。",
     "eats-01-home.png", "eats-01-home.png",
     ["「筛选」改 AssistChip：描边 + 漏斗前缀，与四个类型/标签 chip 一眼区分",
      "‹ n/m › 卡序胶囊上移为独立行，不再叠在后卡露边上；堆叠露边 14→18dp 更可滑动可视",
      "卡面链接只留第一条 + 「＋n 条链接在详情」，卡内信息密度下降"],
     "视觉核验：AssistChip 分离 / 胶囊独立 / 露边可见 全部在位"),
    ("E2", "吃啥 · 抽取落定",
     "落定态卡组让位更深，结果块成为唯一视觉主体与唯一动作区；彩屑双发延长高潮。",
     "eats-02-drawn.png", "eats-02-drawn.png",
     ["卡组收拢加深：alpha 1→0.15、scale 0.82，让位不再「若隐若现」",
      "彩屑双发（落定 + 400ms 后第二次），氛围停留延长",
      "回归：✓就吃这个 / 再抽 仍在可视区（y=1958/1934 断言 PASS）"],
     "断言：落定按钮可见 PASS（回归）"),
    ("E7", "吃啥 · 添加食堂表单（保存栏）",
     "保存栏从「禁用按钮内嵌提示」重构为中性原因 + 全宽两态，就绪态按钮占满行宽。",
     "eats-07b-form-ready-keyboard.png", "eats-07b-form-ready-keyboard.png",
     ["未就绪：原因文案独立成行（中性灰，不再像报错），描边按钮可点，点击 toast 说明缺什么",
      "就绪：全宽实心按钮（原仅 ~60% 宽），随键盘上移（实测 y=2081）",
      "imePadding 移到最外层，键盘与保存栏贴合无缝隙",
      "「位置」选点入口加描边 + › chevron，与普通输入框区分"],
     "断言：未就绪原因 / 就绪保存 均在吸底栏 PASS"),
    ("R5", "衣橱 · 列表（本轮唯一 P0）",
     "长按删除获得显式入口（··· 菜单），「筛选」固定行尾不再被品类 Tab 挤出屏外。",
     "wardrobe-05-wardrobe.png", "wardrobe-05-wardrobe.png",
     ["卡片右上 ··· 圆形菜单 = 编辑/删除（实测弹层两项齐全），长按保留为快捷路径——P0 可发现性修复",
      "品类 Tab 改图标圆 chip（「全部」保留文字），「筛选」固定行尾：首屏直接可见 @(917,302)，无需横滑",
      "去品类小节标题，按品类序平铺；颜色改胶囊与 #标签 分列"],
     "断言：筛选可见 PASS · ···菜单编辑/删除 PASS"),
    ("R3", "衣橱 · 穿搭记录（拼贴统一）",
     "下方网格从 2×2 罗列换成与卡组同一套人形拼贴，一页一种语言、缺失叙事贯通。",
     "wardrobe-03-records.png", "wardrobe-03-records.png",
     ["网格缩略改迷你 BodyCollage：人形落位 + 「未配帽/未配鞋」虚线空槽与卡组一致",
      "人形轮廓底对比度 0.35→0.5，剪影更可读",
      "衬纸 padding 4→2dp，扁槽（鞋）内照片占比提升"],
     "视觉核验：网格两卡均为人形拼贴含空槽"),
    ("R4", "衣橱 · 穿搭详情",
     "录入角标远离「未配鞋」空槽语义区；「复制素材」与搭配页统一为「复制长图」。",
     "wardrobe-04-record-detail.png", "wardrobe-04-record-detail.png",
     ["「＋录入成品图」角标移拼贴右上，不再贴着「未配鞋」被误读为补配鞋",
      "操作按钮更名「📋 复制长图」，与 W1/导出面板一套词（截图实锤）",
      "单品行 › chevron 与按压反馈保留（上轮已修，回归在位）"],
     "视觉核验：右上角标 / 复制长图命名 确认"),
    ("R7", "衣橱 · 添加衣物（照片第一步）",
     "无照片时给大虚线占位「拍照 / 选照片 · 第一步」，必填第一步显性化；保存栏与 eats 同构。",
     None, "wardrobe-07b-add-empty.png",
     ["无照片时显示 1.6:1 大虚线占位（相机图标 + 第一步提示），点击即选——必填不再只是文字",
      "保存栏重构：中性灰原因「选照片、填名称后可保存」+ 全宽两态 + 点击 toast（与 E7 同构）",
      "编辑态：占位隐藏、显示「点击更换照片」，「更新」全宽吸底（实测）"],
     "断言：大占位 / 未就绪原因 / 添加衣物 标题 全 PASS"),
    ("R8", "衣橱 · 导出面板（三修 + 历史 bug）",
     "动作栏钉住推不走、长图全貌一屏可见、维度记忆跨进程生效——顺带修掉自 it-002 起的预览空白 bug。",
     "wardrobe-08-export.png", "wardrobe-08-export.png",
     ["结构改「滚动区 + 底部固定动作栏」：复制长图/分享/只复制文本推不走（展开四维前后 y=2248 不变，断言 PASS）",
      "预览高 42% 屏 + ContentScale.Fit：8666px 长图全貌一屏尽收——顺带修复预览自 it-002 起一直空白的显示 bug（内层滚动无限高度约束使 Coil 请求尺寸失效；R1/R2 截图可证，像素检查 stddev 2.0→21.4）",
      "折叠条「已选 N」accent 徽标；维度记忆跨进程生效：选「通勤简约」→杀进程→重开 prompt 含「氛围：通勤简约」（exportSelectionsReady 首发射标志修竞态）"],
     "断言：钉住 PASS ×2 · 记忆持久 PASS · 预览像素检查 PASS"),
]

META = dict(
    date="2026-09-20",
    stats=[("8", "对照页（前→后）"), ("1 P0", "长按删除可发现性 已修"), ("17 P1", "全部落地"), ("9", "断言 PASS")],
)

def esc(s): return html.escape(s, quote=False)

def compare_page(no, title, concl, before, after, points, assertion, pageno):
    pts = "".join(f'<div class="pt"><span class="n">•</span>{esc(t)}</div>' for t in points)
    before_col = (f'<div class="shotcol"><img src="{A}/before-{before}"/>'
                  f'<div class="shotlabel"><span class="dot d-before"></span>优化前 · R2 评审时</div></div>'
                  if before else
                  '<div class="shotcol na"><div class="nabox">R2 未采集该状态<br/>（本轮新增能力）</div>'
                  '<div class="shotlabel"><span class="dot d-before"></span>优化前 · 无对照</div></div>')
    return f"""<section class="page">
  <div class="phead"><span class="no">{no}</span><span class="t">{esc(title)}</span>
    <span class="sev">{esc(assertion)}</span></div>
  <p class="concl">{esc(concl)}</p>
  <div class="shots">
    {before_col}
    <div class="shotcol"><img src="{A}/{after}"/>
      <div class="shotlabel"><span class="dot d-after"></span>优化后 · 本轮实施</div></div>
    <div class="points">{pts}</div>
  </div>
  <div class="pfoot"><span>R2 实施效果对照 · {esc(META['date'])}</span><span style="margin-left:auto">{pageno}</span></div>
</section>"""

pages = []
# 封面
pages.append(f"""<section class="page cover">
  <div class="kicker">MINI-APPS · IMPLEMENTATION RESULT</div>
  <div class="hairline"></div>
  <h1>R2 评审<br/>实施效果对照</h1>
  <div class="sub">吃啥 it-005 × 衣橱 it-012 · 前后逐页对照</div>
  <div class="summary">按《UX评审报告R2》完整实施：新 P0（长按删除可发现性）与 17 项 P1 全部落地，
  另修复导出预览自 it-002 起一直空白的显示 bug。逐页前后对照与断言实证见后。</div>
  <div class="phones">
    <div class="phone"><img src="{A}/wardrobe-05-wardrobe.png"/></div>
    <div class="phone"><img src="{A}/wardrobe-08-export.png"/></div>
    <div class="phone"><img src="{A}/eats-01-home.png"/></div>
  </div>
  <div class="meta"><span>日期 <b>{META['date']}</b></span><span>迭代 <b>eats it-005 · wardrobe it-012</b></span>
    <span>核验 <b>断言 9 项 · 视觉逐页</b></span></div>
</section>""")
# 总表
rows = "".join(f"""<tr><td class="cid">{no}</td><td>{esc(t.split('（')[0])}</td><td class="st">✓ 已修</td></tr>"""
               for no, t, *_ in PAGES)
pages.append(f"""<section class="page">
  <div class="phead"><span class="no">00</span><span class="t">实施总表</span><span class="sev">R2 全部建议 → 已落地</span></div>
  <div class="statrow">{''.join(f'<div class="stat"><div class="v">{v}</div><div class="l">{esc(l)}</div></div>' for v, l in META['stats'])}</div>
  <p class="lead">R2 报告的全部 P0/P1 与可落地 P2 已实施；轻问题中明确跳过的 5 项（图例交互、marker 形状、瓦片延迟、⌖ 跨机型、hero 渐变、鞋槽比例）按迭代提案记录理由，不在本表。</p>
  <table class="tb">
    <tr><th style="width:12mm">页</th><th>对照页（详见后）</th><th style="width:18mm">状态</th></tr>
    {rows}
    <tr><td class="cid">+</td><td>附加：导出预览空白历史 bug（it-002 起）——实施 O6' 时发现并修复</td><td class="st">✓ 修复</td></tr>
  </table>
  <div class="pfoot"><span>R2 实施效果对照</span><span style="margin-left:auto">2</span></div>
</section>""")
# 对照页
for i, (no, title, concl, before, after, points, assertion) in enumerate(PAGES):
    pages.append(compare_page(no, title, concl, before, after, points, assertion, i + 3))

doc = f"""<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<title>R2 实施效果对照 · {META['date']}</title>
<style>
@page {{ size: 210mm 297mm; margin: 0; }}
html, body {{ margin: 0; padding: 0; width: 210mm; background: #F7F8FA;
  font-family: "Hiragino Sans GB","PingFang SC","Heiti SC",sans-serif;
  color: #1D2129; line-break: strict; -webkit-print-color-adjust: exact; print-color-adjust: exact; }}
* {{ box-sizing: border-box; }}
.page {{ width: 210mm; height: 297mm; overflow: hidden; padding: 12mm 14mm 10mm;
  background: #F7F8FA; break-after: page; display: flex; flex-direction: column; }}
.page:last-child {{ break-after: auto; }}
img {{ display: block; }}
.cover {{ padding: 18mm 16mm 16mm; background: #FFFFFF; }}
.cover .kicker {{ font-size: 10pt; letter-spacing: 3pt; color: #86909C; font-weight: 600; }}
.cover .hairline {{ width: 30mm; border-top: 0.7mm solid #00B42A; margin-top: 7mm; }}
.cover h1 {{ font-size: 38pt; font-weight: 800; letter-spacing: 1pt; margin: 20mm 0 0; }}
.cover .sub {{ font-size: 14pt; color: #4E5969; margin-top: 6mm; }}
.cover .summary {{ font-size: 10.5pt; line-height: 1.75; color: #4E5969; margin-top: 12mm; width: 110mm; }}
.cover .phones {{ display: flex; gap: 6mm; margin-left: auto; margin-top: auto; }}
.cover .phone {{ width: 34mm; height: 75.6mm; border: 0.5mm solid #C9CDD4; border-radius: 4.5mm;
  padding: 2.2mm; background: #FFFFFF; }}
.cover .phone img {{ width: 100%; height: 100%; object-fit: cover; object-position: top; border-radius: 2.5mm; }}
.cover .meta {{ display: flex; gap: 12mm; font-size: 8.5pt; color: #86909C; white-space: nowrap;
  border-top: 0.3mm solid #E5E6EB; padding-top: 6mm; margin-top: 12mm; }}
.cover .meta b {{ color: #1D2129; font-weight: 600; }}
.phead {{ display: flex; align-items: baseline; gap: 4mm; border-bottom: 0.45mm solid #E5E6EB;
  padding-bottom: 3.5mm; margin-bottom: 4mm; }}
.phead .no {{ font-size: 13pt; font-weight: 800; color: #00B42A; }}
.phead .t {{ font-size: 14pt; font-weight: 700; }}
.phead .sev {{ margin-left: auto; font-size: 8pt; color: #00B42A; }}
.pfoot {{ display: flex; font-size: 8pt; color: #A9AEB8; border-top: 0.3mm solid #E5E6EB;
  padding-top: 2.5mm; margin-top: auto; }}
.concl {{ font-size: 10pt; line-height: 1.65; margin: 0 0 4mm; }}
.shots {{ display: flex; gap: 5mm; align-items: flex-start; flex: 1; }}
.shotcol {{ width: 60mm; }}
.shotcol img {{ width: 60mm; height: 133.3mm; object-fit: cover; object-position: top;
  border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; background: #fff; }}
.shotcol.na {{ width: 60mm; }}
.nabox {{ width: 60mm; height: 133.3mm; border: 0.3mm dashed #C9CDD4; border-radius: 2.5mm;
  display: flex; align-items: center; justify-content: center; text-align: center;
  font-size: 8.5pt; color: #86909C; background: #FCFCFD; }}
.shotlabel {{ display: flex; align-items: center; gap: 2mm; margin-top: 2.2mm; font-size: 8.5pt; font-weight: 700; }}
.shotlabel .dot {{ width: 2.6mm; height: 2.6mm; border-radius: 50%; }}
.d-before {{ background: #F53F3F; }} .d-after {{ background: #00B42A; }}
.points {{ flex: 1; display: flex; flex-direction: column; gap: 3mm; }}
.pt {{ font-size: 8.8pt; line-height: 1.55; color: #4E5969; }}
.pt .n {{ color: #00B42A; font-weight: 800; margin-right: 1.5mm; }}
.statrow {{ display: flex; gap: 5mm; margin: 4mm 0; }}
.stat {{ flex: 1; background: #fff; border: 0.3mm solid #E5E6EB; border-radius: 2.5mm; padding: 4mm; }}
.stat .v {{ font-size: 19pt; font-weight: 800; color: #00B42A; }}
.stat .l {{ font-size: 8.5pt; color: #86909C; margin-top: 1mm; }}
table.tb {{ width: 100%; border-collapse: collapse; font-size: 9pt; }}
table.tb th {{ text-align: left; font-size: 8pt; color: #86909C; font-weight: 600;
  border-bottom: 0.45mm solid #C9CDD4; padding: 1.6mm 2mm; }}
table.tb td {{ border-bottom: 0.25mm solid #E5E6EB; padding: 2.2mm 2mm; vertical-align: top; line-height: 1.5; }}
table.tb td.cid {{ font-weight: 700; width: 12mm; }}
table.tb td.st {{ color: #00B42A; font-weight: 700; white-space: nowrap; }}
.lead {{ font-size: 9.5pt; line-height: 1.7; color: #4E5969; }}
</style></head><body>
{''.join(pages)}
</body></html>"""

with open("report.html", "w", encoding="utf-8") as f:
    f.write(doc)
print("written report.html", len(doc))
