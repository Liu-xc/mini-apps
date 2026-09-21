#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""it-024/it-012 数据包功能 UI 审查 · 改版线框（2 张）"""
import sys
sys.path.insert(0, "/Users/leo/Documents/mini-apps/.agents/skills/ui-audit/scripts")
from wireframe_lib import *  # noqa
from PIL import Image, ImageDraw

OUT = "/Users/leo/Documents/mini-apps/reports/2026-09-21-data-package-audit/assets"

RED = (198, 40, 40)


def sym_red_x(d, cx, cy, sz=11):
    d.line([cx - sz, cy - sz, cx + sz, cy + sz], fill=RED, width=4)
    d.line([cx + sz, cy - sz, cx - sz, cy + sz], fill=RED, width=4)

AMBER = (168, 124, 0)
GREEN = (46, 125, 50)


def dialog_frame(d, title):
    """对话框底板 + 标题，返回内容区起点 y"""
    rr(d, [40, 210, 680, 1390], r=24, fill=(255, 255, 255), outline=LINE, width=3)
    # 屏幕底暗化示意
    txt(d, 360, 90, "（页面被对话框遮罩）", sz=20, fill=INK2, anchor="ma")
    txt(d, 72, 240, title, sz=30, bold=True)
    d.line([72, 292, 648, 292], fill=LINE, width=2)
    return 320


def wf_import_replace():
    img, d = new_canvas()
    y = dialog_frame(d, "数据包预览")

    txt(d, 72, y, "📦 wardrobe-ai-打标.zip", sz=22)
    txt(d, 72, y + 34, "来自 agent: gpt-5 · AI 加工包", sz=20, fill=INK2)

    y += 92
    # ① 当前模式胶囊
    chip(d, 72, y, "当前：替换模式", w=230, h=48, selected=True, sz=21)
    badge(d, 660, y + 24, 1)
    note(d, 72, y + 58, "① 模式胶囊常驻警示色，选合并时消失\n    （解决红色摘要的动线割裂）", sz=18)

    y += 140
    txt(d, 72, y, "替换模式将发生：", sz=23, bold=True)
    y += 42
    # ✕ 清除行（含图片数=变更②）
    sym_red_x(d, 90, y + 14)
    txt(d, 116, y, "清除  本地 20 单品 · 6 穿搭 · 36 张图", sz=22, fill=RED)
    badge(d, 660, y + 14, 2)
    y += 40
    txt(d, 116, y, "（含评论 1 · 打卡 30 · 想买 8）", sz=19, fill=INK2)
    y += 44
    sym_check(d, 90, y + 14, sz=11, color=GREEN)
    txt(d, 116, y, "导入  包内 21 单品 · 7 穿搭 · 34 张图", sz=22, fill=GREEN)
    note(d, 72, y + 44, "② 删除侧补图片数：与导入侧口径对称，损失感最直观", sz=18)

    # 单选组：替换行为警示态
    y += 108
    rr(d, [64, y, 656, y + 116], r=14, fill=FILL, outline=RED, width=3)
    d.ellipse([92, y + 22, 124, y + 54], outline=INK2, width=3)
    txt(d, 148, y + 20, "合并（推荐）", sz=23)
    txt(d, 148, y + 60, "同 id 以包内为准，本地多出保留", sz=19, fill=INK2)
    y2 = y + 132
    rr(d, [64, y2, 656, y2 + 116], r=14, fill=(255, 240, 240), outline=RED, width=4)
    d.ellipse([92, y2 + 22, 124, y2 + 54], outline=RED, width=3, fill=RED)
    txt(d, 148, y2 + 20, "替换全部", sz=23, bold=True, fill=RED)
    txt(d, 148, y2 + 60, "清空本地后导入包内内容（不可撤销）", sz=19, fill=RED)
    badge(d, 660, y2 + 24, 3)
    note(d, 72, y2 + 124, "③ 选中「替换全部」整行转警示红（底/描边/字），\n    与「合并」不再同权；副标题补后果", sz=17)

    # 按钮：确认按钮随模式变色变文案
    y3 = y2 + 208
    btn(d, 200, y3, 200, 72, "取消", primary=False, sz=24)
    rr(d, [400, y3, 600, y3 + 72], r=36, fill=RED)
    txt(d, 500, y3 + 20, "替换…", sz=24, bold=True, fill=(255, 255, 255), anchor="ma")
    badge(d, 640, y3 + 36, 4)
    note(d, 72, y3 + 84, "④ 确认按钮随模式变文案/警示色——最后一道闸", sz=17)

    img.save(f"{OUT}/wf-import-replace.png")
    print("wf-import-replace.png")


def wf_replace_confirm():
    img, d = new_canvas()
    y = dialog_frame(d, "替换全部数据？")

    txt(d, 72, y, "即将执行的是不可逆操作。", sz=24, bold=True, fill=RED)
    y += 56
    # ① 删除/替换两段式明细（对称 + 图片数）
    sym_red_x(d, 90, y + 14)
    txt(d, 116, y, "删除  20 单品 · 6 穿搭 · 36 张图", sz=23, fill=RED)
    txt(d, 116, y + 40, "（评论 1 · 打卡 30 · 想买 8 一并删除）", sz=19, fill=INK2)
    y += 96
    sym_check(d, 90, y + 14, sz=11, color=GREEN)
    txt(d, 116, y, "导入  21 单品 · 7 穿搭 · 34 张图", sz=23, fill=GREEN)
    badge(d, 660, y + 14, 1)
    note(d, 72, y + 52, "① 明细改为「删除/导入」两段对称，各带图片数——\n    2 秒内读懂将要失去什么、换来什么", sz=18)

    y += 130
    rr(d, [64, y, 656, y + 88], r=14, fill=(255, 244, 230), outline=AMBER, width=3)
    txt(d, 88, y + 16, "此操作无法撤销。", sz=22, bold=True, fill=AMBER)
    txt(d, 88, y + 52, "建议先导出一份当前数据再替换。", sz=20, fill=AMBER)

    # ② 先导出备份按钮
    y2 = y + 124
    btn(d, 120, y2, 480, 76, "先导出一份当前数据（推荐）", primary=True, sz=23)
    badge(d, 660, y2 + 38, 2)
    note(d, 72, y2 + 96, "② 「建议先导出」从纯文字升为按钮：点按直接进 SAF 保存流程，\n    完成后回到本对话框继续替换", sz=18)

    # 按钮排
    y3 = y2 + 196
    btn(d, 200, y3, 200, 72, "取消", primary=False, sz=24)
    rr(d, [400, y3, 600, y3 + 72], r=36, fill=RED)
    txt(d, 500, y3 + 20, "仍要替换", sz=24, bold=True, fill=(255, 255, 255), anchor="ma")
    badge(d, 640, y3 + 36, 3)
    note(d, 72, y3 + 84, "③ 确认按钮改「仍要替换」——措辞承认风险已知晓", sz=17)

    img.save(f"{OUT}/wf-replace-confirm.png")
    print("wf-replace-confirm.png")


wf_import_replace()
wf_replace_confirm()
