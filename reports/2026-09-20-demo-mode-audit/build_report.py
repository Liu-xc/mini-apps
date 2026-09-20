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
    sub="演示模式（Mock 数据源）与数据层 SDK 化 · 实机走查",
    date="2026-09-20",
    scope="2 应用 · 18 页面",
    summary="两应用本次落地 libs/store 本地存储 SDK 与应用内演示模式（内存 Mock 数据源）。"
            "在演示模式下对 18 个页面实机截图走查、两轮视觉模型评审与 6 组交互实测："
            "演示开关往返、写入不落盘、真实数据零污染全部验证通过；"
            "走查发现的种子元数据与配图矛盾（15 处）已当场修复并复验。"
            "本报告汇总剩余问题为分级清单，并逐页给出灰盒改版线框：蓝色数字徽标对应右侧改版要点。",
    cover_shots=["eats-04-list.png", "wardrobe-04-wardrobe.png"],
    stats=[("18", "走查页面"), ("2", "P0 证据失效(已补拍)"), ("12", "P1 级问题"), ("6", "交互组实测")],
    methods=[("实机截图走查", "emulator-5554 · 演示模式全页面 18 张过程态截图"),
             ("视觉模型逐页评审", "两轮独立评审 + 局部放大取证 + MD5 查重"),
             ("交互实测验证", "演示开关往返 / 增删不落盘 / 真实数据零污染断言链"),
             ("数据层实测", "store SDK 迁移后单测全绿、老数据无缝读起")],
)

SECTIONS = [
    dict(label="吃啥 · EATS", kicker="PART 01", app="eats",
         shots=["eats-01-spin-home.png", "eats-04-list.png"]),
    dict(label="衣橱 · WARDROBE", kicker="PART 02", app="wardrobe",
         shots=["wardrobe-01-outfit.png", "wardrobe-04-wardrobe.png"]),
]

THEMES = [
    dict(app="eats", no="E1", title="首页卡组（W1）",
         concl="骨架健康、信息齐全，但卡片下部约 220px 死区让重心失衡；选中态与分页热区普遍偏弱。",
         problems=[
             ("P1", "「＋记一笔/详情」按钮下方 220px 空白死区，卡片重心失衡"),
             ("P2", "选中筛选 chip 仅极浅填充无勾选标记，筛选生效难察觉"),
             ("P2", "「综合 4」徽章骑在卡片圆角弧线上，像未对齐"),
             ("P2", "卡内三行内容左缘参差（140/168/163px）；分页箭头 20px 且浅灰"),
             ("P2", "演示横幅满宽直角，与全页圆角浮动语言不一致"),
         ],
         shot="eats-01-spin-home.png", wire="wf-e1-card.png",
         points=[
             (1, "卡片高度包内容，「记一笔/详情」锚定卡片底部，消死区"),
             (2, "选中 chip 加深容器色 + 前置自绘勾；卡内左缘统一栅格线"),
             (3, "分页箭头改 IconButton 级热区并加深着色"),
             (4, "演示横幅改左右留边圆角胶囊（两应用统一）"),
         ]),
    dict(app="eats", no="E2", title="抽取落定态（W1）",
         concl="结果卡本体（深绿底+亮绿描边）层级出色；但落定后底部按钮以 15% 透明度残存，「换一张」与「再抽」语义重复易误触。",
         problems=[
             ("P1", "落定态「随机抽一张/换一张」残影仍可读，「换一张」与「再抽」冲突"),
             ("P2", "scrim 只罩卡组区，标题/chips/导航仍全亮，舞台感被稀释"),
             ("P2", "静态截图下彩屑呈 2–4px 脏点散落在文字区内"),
         ],
         shot="eats-02-spin-drawn.png", wire="wf-e2-drawn.png",
         points=[
             (1, "落定态直接隐藏底部按钮条，结果条成为唯一动作出口"),
             (2, "全屏 scrim（导航除外），明暗聚焦让结果卡独占舞台"),
         ]),
    dict(app="eats", no="E3", title="地图页（W2）",
         concl="图例与 marker 用色自洽；但自有红圈 marker 与高德底图地铁红色图标同形同色，扫视时互相混淆。",
         problems=[
             ("P1", "堂食红圈 marker 与底图地铁红色圆形图标撞车"),
             ("P2", "状态栏文字裸贴瓦片，与地名交叠"),
             ("P2", "「3 条未上地图」用文字箭头无按钮感；定位钮在右上不合惯例"),
             ("P2", "底部导航硬裁地图兴趣点标注"),
         ],
         shot="eats-03-map.png", wire="wf-e3-map.png",
         points=[
             (1, "marker 改水滴 pin + 白色外环，与底图图标拉开形制"),
             (2, "状态栏区域加自上而下浅色渐变 scrim"),
             (3, "定位钮移至右下角（地图类惯例位）"),
             (4, "「未上地图」入口改标准 trailing chevron"),
         ]),
    dict(app="eats", no="E4", title="录入表单与弹层（W4/W6）",
         concl="表单栅格工整是亮点；但位置信息双重展示无标签、记一笔两个输入框零间距相接、详情/表单顶部 170px 空带三处最伤。",
         problems=[
             ("P1", "位置卡与无标签输入框双重展示同一地址，改哪个不明确"),
             ("P1", "记一笔弹层「花费/感想」两输入框零间距相接"),
             ("P1", "详情/表单工具栏上方约 170px 异常空带（inset 重复计算）"),
             ("P2", "表单尾部标签被 CTA 条硬裁；删除入口无危险色；类型选中态弱"),
             ("P2", "「记一笔/落账」主行动三种叫法不统一"),
         ],
         shot="eats-07-edit.png", wire="wf-e3-form.png",
         points=[
             (1, "工具栏上移收紧空带；删除钮转危险红"),
             (2, "选点卡与「详细地址」拆分并各自带标签"),
             (4, "滚动区底部留白 + CTA 上缘渐隐（详情/弹层同修）"),
         ]),
    dict(app="wardrobe", no="W1", title="衣橱网格与演示入口（W3）",
         concl="衬线大标题与品类圆标 Tab 的编辑感成立；种子 15 处「名称/颜色与配图矛盾」已当场修复复验，导出预览标签已与图一致。",
         problems=[
             ("P1", "种子元数据与配图矛盾（白T恤配绿衣等 15 处）——已当场修复 ✅"),
             ("P2", "每卡灰色圆底 ⋮ 一屏五枚喧宾夺主"),
             ("P2", "「筛选」与末位品类圆标间距仅 10px，几乎相触"),
             ("P2", "头部烧瓶圆钮语义不明；颜色值与 #标签同灰无层级"),
         ],
         shot="wardrobe-04-wardrobe.png", wire="wf-w1-wardrobe.png",
         points=[
             (1, "种子字段与实际素材逐张对齐（已修：棉质T恤/星球衣/棕飞行夹克等）"),
             (2, "颜色前置小色点，与 #标签分层"),
             (3, "「筛选」与滚动区间距 ≥16px"),
             (4, "演示入口改「演示」文字 chip（DEBUG 构建）"),
         ]),
    dict(app="wardrobe", no="W2", title="录入/编辑表单（W4）",
         concl="照片大图占 60% 屏高挤压表单，唯一可见字段被「更新」按钮栏裁掉上半，呈现为破相截断。",
         problems=[
             ("P1", "大图占比过高 + 「名称」字段被按钮栏裁断（05/06 同根）"),
             ("P2", "「点击更换照片（必填）」在有图编辑态语义错位"),
             ("P2", "按钮栏与内容硬切无渐隐；首屏无删除路径"),
         ],
         shot="wardrobe-06-item-edit.png", wire="wf-w2-edit.png",
         points=[
             (1, "照片区卡片化并压至 35–40% 屏高"),
             (2, "滚动区 bottom padding + 按钮栏上缘渐隐；有图时文案改「更换照片」"),
         ]),
    dict(app="wardrobe", no="W3", title="穿搭记录与详情（W7）",
         concl="「这套包含」是全 App 最标准的列表；但记录卡翻页胶囊直接压住「未配帽」槽位文字，详情页「录入成品图」按钮骑压虚线槽，两处叠压均经放大取证。",
         problems=[
             ("P1", "记录卡「‹1/3›」白色胶囊叠压「未配帽」槽位文案（放大取证）"),
             ("P1", "详情页「+录入成品图」骑压虚线槽且语义悬空"),
             ("P2", "列表场景空槽占近半卡高；标签行右缘硬切"),
             ("P2", "同页日期格式不一（2026/08/31 vs 09/15）；评论删除热区小"),
         ],
         shot="wardrobe-07-records.png", wire="wf-w3-records.png",
         points=[
             (1, "列表卡空槽压成单行条，文字不再被胶囊遮挡"),
             (2, "翻页胶囊移出卡片右上角"),
             (4, "已填槽位带品类与名称标签条；成品图按钮收进槽位右缘并改「为帽子录入」"),
         ]),
    dict(app="wardrobe", no="W4", title="导出长图弹层（W6）",
         concl="底部动作分层清楚；但预览仅约 21% 屏宽悬在空旷底色上，确认前无法核对产物内容——种子修复后标签已与图一致。",
         problems=[
             ("P1", "预览图过小（约 21% 宽），标签与提示词模板不可读"),
             ("P2", "「自定义要求」与「人物描述」两套输入框样式"),
             ("P2", "「文案」字段被按钮栏裁切；「复制长图」同动作两种图标"),
         ],
         shot="wardrobe-09-export-sheet.png", wire="wf-w4-export.png",
         points=[
             (1, "预览放大至 50% 宽左置，右侧参数双栏"),
             (3, "两输入框统一 outlined + 浮水标签"),
             (4, "字段区底部渐隐，不被按钮栏裁切"),
         ]),
]

COMMONS = [
    ("C1", "种子元数据与配图矛盾（白T恤/藏青球衣/白色小白鞋等）", "W1 · W2 · W4", "P1",
     "已修：15 处字段对齐实际素材并重验（导出预览标签已一致）"),
    ("C2", "选中态与可点性普遍偏弱（筛选 chip/分页箭头/图例箭头/「改」）", "E1 · E3 · E4", "P2", "选中容器加深+前置勾；热区≥48dp（E1 线框③）"),
    ("C3", "底部按钮栏硬裁滚动内容、无渐隐", "E4 · W2 · W4", "P1", "滚动区 bottom padding + CTA 上缘渐隐（E4 线框④）"),
    ("C4", "底部导航容器偏淡紫，与米绿主题色温打架", "两应用全部页", "P2", "导航底色对齐主色 tonal palette"),
    ("C5", "图标体系混用（emoji 剪贴板 vs 矢量）", "W1 · W4", "P2", "统一矢量图标（导出弹层已是矢量，直接对齐）"),
    ("C6", "演示横幅与入口打磨", "两应用", "P2", "横幅圆角化+单色；eats 列表入口降 tonal；wardrobe 入口语义化（E1④/W1④）"),
    ("C7", "文案与格式不统一（记一笔/落账；日期两种格式）", "E4 · W3", "P2", "术语表收敛；统一「同年省年」规则"),
]

SPLITS = [
    ("eats / it-007 · 卡组与表单打磨", [
        ("O1", "卡组页：卡片高度包内容、选中态体系、分页热区", "E1"),
        ("O2", "落定态：隐藏底部按钮条 + 全屏 scrim", "E2"),
        ("O3", "地图：水滴 marker + 状态栏 scrim + 定位钮右下", "E3"),
        ("O4", "表单：位置字段拆分、顶部空带收紧、CTA 渐隐、弹层零间距", "E4"),
    ]),
    ("wardrobe / it-016 · 叠压与导出修复", [
        ("O1", "衣橱卡：色点分层、⋮ 降权重、演示入口语义化", "W1"),
        ("O2", "编辑表单：照片区压 40%、按钮栏渐隐、删除路径", "W2"),
        ("O3", "记录/详情：空槽单行化、胶囊移出卡外、成品图按钮收进槽位", "W3"),
        ("O4", "导出：预览 50% 左置、输入框统一、字段渐隐", "W4"),
    ]),
    ("两应用 · 共性收敛", [
        ("O1", "导航底色回归主色 tonal；图标统一矢量", "C4 · C5"),
        ("O2", "选中态与可点性规范（加深+勾+48dp）", "C2"),
        ("O3", "术语表与日期格式收敛", "C7"),
    ]),
    ("wardrobe / it-017 · 随机组合互斥规则", [
        ("O1", "随机一套命中连衣裙时跳过上装/下装槽（走查实测暴露）", "W1 · W2"),
    ]),
]

TESTS = [
    ("eats 演示开关往返", "烧瓶按钮 → 确认框 → 重启进入（横幅+11 家 mock）；点横幅退出回真实 ✅"),
    ("eats 写入不落盘", "演示中删麦当劳 → 10 家 → 退出 → 真实 9 家原样 ✅"),
    ("wardrobe 演示开关往返", "「演示」钮 → 重启进入（「我」12 件+assets 照片）；退出回「Leo 17 件」零污染 ✅"),
    ("eats 数据层迁移", "store SDK 三件套替换自研；单测全绿；老数据无缝读起 ✅"),
    ("mock 种子单测", "eats 4 例 + wardrobe 5 例全绿 ✅"),
    ("走查驱动修复", "评审抓出种子 15 处字段与图矛盾 → 修正 → 导出预览复验一致 ✅"),
]

EVIDENCE = [
    ("eats-04-list.png", "eats 演示模式列表：横幅 + 11 家 mock 数据"),
    ("wardrobe-04-wardrobe.png", "wardrobe 演示模式衣橱：角色「我」12 件、内置照片、种子字段已与图对齐"),
    ("wardrobe-09-export-sheet.png", "导出预览：标签「外套·飞行夹克·棕色」与配图一致（修复后）"),
]
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
