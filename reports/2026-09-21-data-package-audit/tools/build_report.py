#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-09-21 数据包功能 UI 走查报告 · 数据区（基于 ui-audit report_template，wire 改为可选）"""
import html
import sys

sys.path.insert(0, "/Users/leo/Documents/mini-apps/.agents/skills/ui-audit/scripts")
import report_template as T  # noqa: E402

A = T.A
CSS = T.CSS + """
.shots.wrap { flex-wrap: wrap; align-content: flex-start; }
.shotcol.wf img { height: 150mm; }
.points-row { display: flex; flex-wrap: wrap; gap: 2mm 4mm; width: 100%; margin-top: 1mm; }
.points-row .pt { width: calc(50% - 2mm); }
"""

META = dict(
    title="衣橱 × 吃啥 · 数据包功能",
    sub="it-024 / it-012 结构化导入导出 · 走查问题清单 · 改版线框",
    date="2026-09-21",
    scope="2 应用 · 16 屏状态",
    summary="对两应用新增的数据包功能（回顾页「数据」小节 + 导入/导出全流程对话框）做实机截图走查："
            "16 个屏状态逐屏视觉评审、9 项交互实测验证。结论：链路与信息架构达标，"
            "核心风险集中在「替换」这一不可逆操作的危险态表达（P0×2）；另两条视觉模型的 P0 判定"
            "（符号豆腐块、导出无反馈）经像素级复核与 dump 断言推翻。修复建议拆为 it-025 交互加固迭代，"
            "两张改版线框给全变更点。",
    cover_shots=["03-wd-import-confirm.png", "04-wd-replace-summary.png"],
    stats=[("16", "走查屏态"), ("2", "P0 级问题"), ("3", "P1 级问题"), ("9", "交互项实测"),
           ("2", "视觉误报复核撤销")],
    methods=[
        ("实机截图走查", "emulator-5554 实机（debug 构建含 it-024/it-012），16 个屏状态含过程态与演示态"),
        ("视觉模型逐页评审", "每屏一 prompt 对标 Airbnb/Things/Linear 严格评审，P0/P1/P2 分级"),
        ("交互实测验证", "uiautomator dump 断言 + run-as 落盘核对 + 导出包 validator 回环，视觉结论须实测实锤"),
    ],
)

SECTIONS = [
    dict(label="衣橱 · WARDROBE", kicker="PART 01", app="wardrobe",
         shots=["02-wd-data-section.png", "04-wd-replace-summary.png"]),
    dict(label="吃啥 · EATS", kicker="PART 02", app="eats",
         shots=["09-eats-import-confirm.png", "10-eats-playtakeout-reject.png"]),
]

THEMES = [
    dict(app="wardrobe", no="R1", title="衣橱 · 回顾页「数据」小节",
         concl="入口融入设置卡区、与「衣柜提醒」同构，可达性合格；风险在导入副标题对覆盖型操作的暗示不足。",
         problems=[
             ("P1", "导入副标题「合并打标结果 / 整包恢复」未暗示恢复是覆盖型操作——入口处零风险线索，用户要到预览屏才见到（有预览+二次确认兜底）"),
             ("P2", "行图标 FileUpload/FileDownload 在编辑风米白底上存在感偏轻，两行入口的辨识度靠文字承载"),
             ("P2", "「数据」标题样式与「衣柜提醒」经源码核对一致（titleMedium+8dp），视觉模型的层级疑点不成立，列为观察项"),
         ],
         shot="02-wd-data-section.png", wire=None,
         points=[
             (1, "副标题补风险暗示：「整包恢复（覆盖本地）」或「合并 / 恢复 · 恢复将覆盖」"),
             (2, "图标可加重 tint 或加 44dp 圆底，与米白底拉开层次"),
         ]),
    dict(app="wardrobe", no="R2", title="衣橱 · 导入预览（合并态）",
         concl="信息架构达标：来源/差异/模式三段式，AI 加工包识别正确；差异行的扫读分层可再强化。",
         problems=[
             ("P1", "差异行前缀 ＋/↻/＝ 在 bodySmall 下与正文同色同重，快扫时分层弱（新增 vs 更新 vs 保留一眼难分）"),
             ("P2", "七个集合逐行平铺无分组空行，当前行数可接受，集合更多时会显密"),
         ],
         shot="03-wd-import-confirm.png", wire=None,
         points=[
             (1, "符号 span 着色加粗：＋ 绿 / ↻ 琥珀 / ＝ 灰，数字保持正文色"),
             (2, "零差异的集合行可折叠为一行汇总「其余 N 项无变化」"),
         ]),
    dict(app="wardrobe", no="R3", title="衣橱 · 导入预览（替换态）",
         concl="替换是全流程唯一不可逆操作，当前与「合并」视觉完全同权——危险态表达不足是本次评审最高优先级问题。",
         problems=[
             ("P0", "「替换全部」单选选中态与「合并（推荐）」同权：同色圆圈、同灰副标题，误选后直接进入高危路径"),
             ("P0", "红色「✕清除」摘要行与单选组的滚动动线割裂：警示读起来像「无论如何都会发生」，而非「因为你选了替换」"),
             ("P1", "确认按钮恒为「导入」文案、恒为主色，不随模式变化，缺少最后一道视觉闸"),
         ],
         shot="04-wd-replace-summary.png", wire="wf-import-replace.png",
         points=[
             (1, "「当前：替换模式」警示胶囊常驻摘要区顶部（动线锚点，选合并时消失）"),
             (2, "删除侧补图片数（36 张图），与导入侧口径对称"),
             (3, "选中的「替换全部」整行转警示红：底色+描边+文字三重表达，与「合并」拉开"),
             (4, "确认按钮文案随模式（导入/替换…）并着警示色"),
         ]),
    dict(app="wardrobe", no="R4", title="衣橱 · 替换二次确认",
         concl="标题、具体数字与不可撤销提醒都在，但删除明细口径不对称、备份建议只给文字不给动作。",
         problems=[
             ("P0", "删除侧只有实体数没有图片数——「36 张图」是最直观的损失感，两侧明细粒度不一致"),
             ("P1", "「建议先导出一份当前数据再替换」纯文字，不可执行；愿意备份的用户仍要取消整个流程去手动导出"),
             ("P2", "确认按钮「替换」措辞与常规确认无异，未承认风险已被知晓"),
         ],
         shot="05-wd-replace-confirm.png", wire="wf-replace-confirm.png",
         points=[
             (1, "明细改「删除 / 导入」两段对称，各带图片数，2 秒读懂失去什么换来什么"),
             (2, "「先导出一份当前数据」升为主按钮：点按直接进 SAF 保存，完成后回到本对话框"),
             (3, "确认按钮改「仍要替换」（措辞承认风险已知晓）"),
         ]),
    dict(app="wardrobe", no="R5", title="衣橱 · 错误与导出反馈",
         concl="拒绝理由一句话可懂、零改动承诺到位；两条视觉模型的 P0 判定经实测复核撤销（详见附录）。",
         problems=[
             ("P2", "✗/✓ 符号与正文同色同重：错误行/安抚行的色彩权重未拉开（放大复核符号渲染正常，全页评审的「豆腐块」为低分辨率误报）"),
             ("P2", "导出完成 snackbar 实测显示 6s+（4 次 dump 断言），初判「反馈缺失」撤销——见附录证据图"),
         ],
         shot="07-wd-notzip-reject.png", wire=None,
         points=[
             (1, "✗ 行着 error 色、✓ 安抚行着主色，一红一绿低成本拉开语义"),
             (2, "多条错误原因时（缺图清单等）可加行距分组"),
         ]),
    dict(app="eats", no="E1", title="吃啥 · 数据小节与导入预览",
         concl="与衣橱同构实现，跨应用一致性良好（间距/卡片/单选布局一致）；差异行符号问题同 R2。",
         problems=[
             ("P1", "同 R2：＋/↻/＝ 符号与正文同色同重，扫读分层弱（跨应用共性问题 C1）"),
             ("P2", "eats 特有校验文案「玩 + 外送是无效组合」对普通用户偏术语——但对 AI 打标场景是有效反馈，保持"),
         ],
         shot="09-eats-import-confirm.png", wire=None,
         points=[
             (1, "随 C1 一并修符号着色，两应用同一套规则"),
         ]),
    dict(app="eats", no="E2", title="吃啥 · 校验拒绝与演示态",
         concl="PLAY+TAKEOUT 校验拒绝带店名级原因、指向明确；演示模式置灰与说明行两应用一致。",
         problems=[
             ("P2", "演示模式置灰说明行与提醒卡的「演示模式不推送」样式一致——一致性达标，观察项"),
             ("P2", "导出 snackbar 首轮截图未捕获（eats 图片少导出瞬间完成），实测正常——同 R5"),
         ],
         shot="10-eats-playtakeout-reject.png", wire=None,
         points=[
             (1, "校验文案可附修复指引「请改为出门或在家」——帮助 agent 自改包"),
         ]),
]

COMMONS = [
    ("C1", "差异/明细行符号（＋↻＝/✗✓）与正文同色同重，扫读分层弱", "R2 · R5 · E1", "P1",
     "符号 span 着色加粗（＋绿 ↻琥珀 ＝灰；✗红 ✓绿）"),
    ("C2", "替换模式危险态表达不足：单选同权 + 红色摘要动线割裂 + 按钮不随模式", "R3 · E1（同构对话框）", "P0",
     "模式胶囊 + 单选警示红 + 按钮随模式（线框 wf-import-replace ①③④）"),
    ("C3", "替换明细口径不对称：删除侧缺图片数", "R3 · R4", "P0",
     "删除/导入两段对称各带图数（wf-import-replace ② / wf-replace-confirm ①）"),
    ("C4", "「建议先导出备份」纯文字不可执行", "R4", "P1", "升为主按钮直达 SAF 保存（wf-replace-confirm ②）"),
    ("C5", "导入入口副标题对覆盖型操作零暗示", "R1 · E1", "P2", "文案补「（覆盖需确认）」类风险词"),
]

SPLITS = [
    ("wardrobe + eats / it-025 · 数据包交互加固（建议，待 Leo 确认）", [
        ("O1", "替换危险态三件套：模式胶囊 + 单选警示红 + 确认按钮随模式变色变文案", "R3 · E1"),
        ("O2", "替换明细对称并补图片数（预检服务已可算 referencedImages）", "R3 · R4"),
        ("O3", "二次确认升版：先导出备份按钮 + 「仍要替换」措辞", "R4"),
        ("O4", "符号分层着色（＋↻＝✗✓ span 色，两应用同一套）", "R2 · R5 · E1"),
        ("O5", "入口副标题风险暗示文案", "R1 · E1"),
    ]),
]

TESTS = [
    ("合并导入（agent 样例包）", "PASS：18→20 单品、5→6 穿搭落盘，图片归一 uuid.webp（run-as 核对）"),
    ("导出 → validator 回环", "PASS：2.29MB/21 图导出包拉回本机，validate.py PASS（23 WARN 均历史短 id）"),
    ("跨 app 拒绝（吃啥包→衣橱）", "PASS：「这是『吃啥』的数据包」+ 零改动承诺"),
    ("非 zip 拒绝", "PASS：坏文件给「不是有效的数据包」"),
    ("替换 + 二次确认", "PASS：摘要切换/确认/即时生效（标题与品类带刷新、旧图片文件回收）"),
    ("备份恢复（导出包替换导回）", "PASS：persons/items/outfits/wearLogs 与快照一致"),
    ("导出 snackbar 反馈", "PASS：SAVE 后 4 次 dump 断言 6s+ 持续显示（撤销视觉「无反馈」P0）"),
    ("✗/✓ 符号渲染", "PASS：放大裁剪像素复核清晰（撤销视觉「豆腐块」P0）"),
    ("PLAY+TAKEOUT 拒绝 / eats 合并导入", "PASS：店名级原因；9→11 家 14→16 笔"),
]

EVIDENCE = [
    ("14-wd-export-done.png", "导出完成 snackbar 实证：显示 6s+，带 [分享] 动作（首轮截图未捕获造成视觉误报）"),
    ("06-wd-cross-app-reject.png", "跨 app 拒绝：一句话原因 + 下一步指引 + 零改动承诺三要素齐备"),
    ("10-eats-playtakeout-reject.png", "eats 特有校验：PLAY+TAKEOUT 带店名拒绝（AI 打标包的有效反馈）"),
]


# ---- 渲染（自 template 移植，wire 可选） ----

def sev_counts(th):
    c = {"P0": 0, "P1": 0, "P2": 0}
    for s, _ in th["problems"]:
        c[s] += 1
    return " ".join(f"{k}×{v}" for k, v in c.items() if v)


def esc(s):
    return html.escape(s, quote=False)


def theme_page(th, pageno):
    probs = "".join(
        f'<div class="prob"><span class="tag {p[0].lower()}">{p[0]}</span>{esc(p[1])}</div>'
        for p in th["problems"])
    pts = "".join(
        f'<div class="pt"><span class="n">{n}</span>{esc(t)}</div>'
        for n, t in th["points"])
    appname = next((s["label"].split(" · ")[0] for s in SECTIONS if s["app"] == th["app"]), "")
    if th.get("wire"):
        shot_cols = f"""
    <div class="shotcol wf"><img src="{A}/{esc(th['shot'])}" alt="">
      <div class="shotlabel"><span class="dot d-now"></span>现状截图<span class="cap">模拟器实机</span></div></div>
    <div class="shotcol wf"><img src="{A}/{esc(th['wire'])}" alt="">
      <div class="shotlabel"><span class="dot d-new"></span>改版线框<span class="cap">蓝色徽标 = 变更点</span></div></div>
    <div class="points-row">{pts}</div>"""
        pts = ""
        pts_style = ""
    else:
        shot_cols = f"""
    <div class="shotcol"><img src="{A}/{esc(th['shot'])}" alt="">
      <div class="shotlabel"><span class="dot d-now"></span>现状截图<span class="cap">模拟器实机</span></div></div>"""
        pts_style = " style='flex:1.7'"
    return f"""
<section class="page">
  <div class="phead"><span class="no">{esc(th['no'])}</span><span class="t">{esc(th['title'])}</span>
    <span class="sev">{sev_counts(th)}</span></div>
  <p class="concl">{esc(th['concl'])}</p>
  <div class="problems">{probs}</div>
  <div class="shots{' wrap' if th.get('wire') else ''}">
    {shot_cols}
    <div class="points"{pts_style}>{pts}</div>
  </div>
  <div class="pfoot"><span>{esc(appname)} · 数据包功能 UI 走查</span><span style="margin-left:auto">{pageno}</span></div>
</section>"""


def divider(sec, pageno):
    imgs = "".join(f'<div class="phone"><img src="{A}/{esc(s)}" alt=""></div>' for s in sec["shots"])
    n = sum(1 for t in THEMES if t["app"] == sec["app"])
    return f"""
<section class="page cover">
  <div class="kicker">{esc(sec['kicker'])}</div>
  <div class="hairline"></div>
  <h1 class="mid">{esc(sec['label'])}</h1>
  <div class="sub">{n} 个页面 · 现状与评审结论</div>
  <div class="phones">{imgs}</div>
  <div class="meta"><span>UI 走查报告 · {esc(META['date'])}</span><span style="margin-left:auto">{pageno}</span></div>
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
  <div class="kicker">UI REVIEW</div>
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
        rows = "".join(
            f"<tr><td class='c'><b>{no}</b></td><td>{esc(txt)}</td><td class='c'>{esc(scope)}</td></tr>"
            for no, txt, scope in items)
        srows += f"<tr><td colspan='3' style='background:#fff;font-weight:700'>{esc(name)}</td></tr>{rows}"

    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">→</span><span class="t">落地拆分建议</span></div>
  <p class="lead">按 spec-driven 流程拆为一次交互加固迭代；O1/O2/O3 对应两张改版线框的全部变更点。</p>
  <table>
    <tr><th style="width:12mm">#</th><th>方案内容</th><th style="width:30mm">对应页面</th></tr>
    {srows}
  </table>
  <div class="pfoot"><span>落地拆分</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")
    pageno += 1

    trows = "".join(f"<tr><td><b>{esc(a)}</b></td><td>{esc(b)}</td></tr>" for a, b in TESTS)
    evid = "".join(
        f'<div class="acol"><img src="{A}/{esc(p)}"><div class="cap">{esc(c)}</div></div>'
        for p, c in EVIDENCE)
    pages.append(f"""
<section class="page">
  <div class="phead"><span class="no">A</span><span class="t">附录 · 交互实测记录与证据</span></div>
  <table>
    <tr><th style="width:52mm">实测项</th><th>结论</th></tr>
    {trows}
  </table>
  <div class="h2">证据截图</div>
  <div class="appendix-shot">{evid}</div>
  <div class="pfoot"><span>附录</span><span style="margin-left:auto">{pageno}</span></div>
</section>""")
    pageno += 1

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"><title>{esc(META['title'])} · UI 走查报告 · {esc(META['date'])}</title>
<style>{CSS}</style></head>
<body>{''.join(pages)}</body></html>"""

    out = sys.argv[1] if len(sys.argv) > 1 else "report.html"
    with open(out, "w", encoding="utf-8") as f:
        f.write(doc)
    print("written", out, len(doc), "bytes")


if __name__ == "__main__":
    build()
