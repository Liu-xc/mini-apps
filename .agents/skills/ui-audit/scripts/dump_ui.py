#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""dump_ui.py — 拉取当前安卓界面的 uiautomator 节点树并解析。

用法:
  python3 dump_ui.py [serial]              # 列出有文本的节点: 文本 | bounds | 中心坐标
  python3 dump_ui.py [serial] --clickable  # 只列 clickable 且无文本的容器/控件
  python3 dump_ui.py [serial] --all        # 文本 + 无文本可点元素

依赖: 仅系统 python3（正则解析，不需要第三方库）+ adb 在 PATH。
注意: Compose 控件的 selected/checked 属性恒为 false，选中态请用行为验证。
"""
import re
import subprocess
import sys
import shlex

SERIAL = None
ARGS = set(sys.argv[1:])
for a in sys.argv[1:]:
    if not a.startswith("-"):
        SERIAL = a
        ARGS.discard(a)


def adb(*args):
    cmd = ["adb"]
    if SERIAL:
        cmd += ["-s", SERIAL]
    cmd += list(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=20)


def main():
    n = 1
    while adb("shell", "test -f /sdcard/ui.xml && echo ok").stdout.strip() != "ok" or n == 1:
        path = f"/sdcard/ui{n}.xml"
        adb("shell", f"uiautomator dump {path}")
        xml = adb("shell", f"cat {path}").stdout
        if "<node" in xml:
            break
        n += 1
    else:
        xml = adb("shell", f"cat /sdcard/ui{n-1}.xml").stdout

    rows = []
    for m in re.finditer(r"<node[^>]*>", xml):
        nd = m.group(0)
        t = re.search(r'text="([^"]*)"', nd)
        de = re.search(r'content-desc="([^"]*)"', nd)
        b = re.search(r'bounds="(\[[0-9,\[\]]+\])"', nd)
        cl = re.search(r'clickable="(\w+)"', nd)
        if not b:
            continue
        label = (t.group(1) if t and t.group(1).strip() else "") or \
                (de.group(1) if de and de.group(1).strip() else "")
        clickable = cl and cl.group(1) == "true"
        if not label and not ("--clickable" in ARGS or "--all" in ARGS and clickable):
            continue
        if "--clickable" in ARGS and not clickable:
            continue
        nums = [int(x) for x in re.findall(r"-?\d+", b.group(1))]
        if len(nums) == 4 and nums[2] > nums[0] and nums[3] > nums[1]:
            cx, cy = (nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2
            rows.append((label or "(no-text)", b.group(1), cx, cy, clickable))

    for label, bounds, cx, cy, clickable in rows:
        flag = " [click]" if clickable else ""
        print(f"{label} | {bounds} | tap=({cx},{cy}){flag}")
    if not rows:
        print("(no matching nodes)")


if __name__ == "__main__":
    main()
