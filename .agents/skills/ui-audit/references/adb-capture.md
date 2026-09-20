# adb 采集 · 模拟器导航与截图

## 环境预检

```bash
adb devices                     # 模拟器应显示 emulator-5554 device
adb -s emulator-5554 shell pm list packages | grep <包名>   # 是否已装
```

构建安装（评审前必须最新代码；多 App 可后台并行跑）：

```bash
cd <app目录> && JAVA_HOME=<jdk17> <gradle路径> installDebug -q
```

启动页（从 `pm dump` 找 launcher activity）：

```bash
adb -s emulator-5554 shell pm dump <包名> | grep -A1 "android.intent.action.MAIN" | head -4
adb -s emulator-5554 shell am start -n <包名>/.MainActivity
```

## 节点 dump 与导航

用技能自带脚本（省去每次手写正则）：

```bash
python3 ~/.agents/skills/ui-audit/scripts/dump_ui.py            # 列出文本+bounds+中心坐标
python3 ~/.agents/skills/ui-audit/scripts/dump_ui.py --clickable  # 只看可点元素
```

常用操作：

```bash
adb shell input tap <x> <y>                    # 点按（坐标= bounds 中心）
adb shell input swipe 700 860 300 860 250      # 滑动（横滑换卡）
adb shell input keyevent 4                      # BACK
adb shell input keyevent 111                    # ESC 收软键盘
adb shell cmd uimode night yes|no               # 深浅色切换
adb -s emulator-5554 exec-out screencap -p > 页面名.png
```

## 坑

- **键盘遮挡**：点过输入框后底部导航被顶出屏，点击全部落空——先 `keyevent 111`，必要时 BACK。
- **am start 提示 "current task has been brought to the front"**：应用本就在前台，不是失败。
- **Compose 控件的 `selected`/`checked` 属性恒为 false**：判断选中态要看行为（点击前后 dump 对比、候选集变化），不要信属性。
- dump 文件重名会被覆盖，用 `/sdcard/ui.xml`、`/sdcard/ui2.xml` 递增。
- 模拟器录屏只有 ~3fps，只够证明"编排存在"，别下流畅性结论；录屏须 `run_in_background` 前台会话跑。
- 截图建议放工作目录 `<repo>/reports/<日期-主题>/assets/`，后续报告直接引用。
