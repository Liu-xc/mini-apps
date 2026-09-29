#!/usr/bin/env bash
# 显影（darkroom）专用模拟器控制 —— 设备分治约定
# （见根 specs/iterations/it-002-emu-device-split.md）
#
#   本应用只操作 darkroom_* AVD；一切设备命令绑定 -s <序列号>，
#   序列号按 AVD 名解析（端口只是显示名，不硬编码 emulator-5554 这类号码）。
#   与 wardrobe 并行开发时各用各的模拟器，绝不互抢前台。
#
# 用法：
#   tools/emu.sh up         确保本应用模拟器在跑并就绪，打印序列号
#   tools/emu.sh serial     打印本应用设备序列号（无实例时报错）
#   tools/emu.sh install    assembleDebug 并安装到本应用设备
#   tools/emu.sh launch     冷启动前不停进程，仅拉起主界面
#   tools/emu.sh restart    force-stop 后重新拉起（走查卡住时的复位手段）
#   tools/emu.sh stop       停掉本应用进程
#   tools/emu.sh cap [文件]  截图（默认 /tmp/darkroom-<时间>.png）
#   tools/emu.sh uadump [文件] uiautomator 无障碍树 dump（默认 /tmp/darkroom-ui.xml）
set -euo pipefail

AVD_PREFIX="darkroom"        # 本应用的 AVD 命名前缀（darkroom_qa）
PRIMARY_AVD="darkroom_qa"    # 无实例在跑时启动哪一个
PORT=5564                    # 启动优先端口（序列号稳定）；被占则自动换端口
PKG="com.leo.darkroom"
ACTIVITY="com.leo.darkroom/.MainActivity"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

sdk_dir() {
  if [ -n "${ANDROID_HOME:-}" ]; then echo "$ANDROID_HOME"; return; fi
  if [ -f "$ROOT/local.properties" ]; then
    local d
    d=$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" | head -1)
    if [ -n "$d" ]; then echo "$d"; return; fi
  fi
  echo "$HOME/Library/Android/sdk"
}
SDK="$(sdk_dir)"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"

# 在线模拟器里找本应用 AVD 的实例：优先 PRIMARY_AVD，其次任意 AVD_PREFIX* 名
resolve_serial() {
  local serial avd best="" best_pri=""
  for serial in $("$ADB" devices | awk '/^emulator-[0-9]+/ && $2=="device" {print $1}'); do
    avd=$("$ADB" -s "$serial" shell getprop ro.boot.qemu.avd_name 2>/dev/null | tr -d '\r')
    case "$avd" in
      "$PRIMARY_AVD") best_pri="$serial" ;;
      "$AVD_PREFIX"*) [ -z "$best" ] && best="$serial" ;;
    esac
  done
  if [ -n "$best_pri" ]; then echo "$best_pri"; elif [ -n "$best" ]; then echo "$best"; fi
}

require_serial() {
  local s
  s=$(resolve_serial)
  if [ -z "$s" ]; then
    echo "错误：没有在跑的 ${AVD_PREFIX}_* 模拟器，先执行 tools/emu.sh up" >&2
    exit 1
  fi
  echo "$s"
}

wait_boot() {
  local s="$1" i=0
  while [ "$("$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    i=$((i + 1))
    if [ "$i" -gt 90 ]; then echo "错误：等待 $s 启动超时（180s）" >&2; exit 1; fi
    sleep 2
  done
}

cmd_up() {
  local s
  s=$(resolve_serial)
  if [ -z "$s" ]; then
    local port_args=""
    if "$ADB" devices | awk '{print $1}' | grep -qx "emulator-$PORT"; then
      echo "提示：emulator-$PORT 被其他实例占用，本次自动分配端口（序列号以 serial 输出为准）" >&2
    else
      port_args="-port $PORT"
    fi
    echo "启动 AVD $PRIMARY_AVD ..."
    # shellcheck disable=SC2086
    nohup "$EMU" -avd "$PRIMARY_AVD" $port_args >"/tmp/emu-$AVD_PREFIX.log" 2>&1 &
    local i=0
    while [ -z "$s" ]; do
      i=$((i + 1))
      if [ "$i" -gt 60 ]; then echo "错误：模拟器未出现在 adb devices（见 /tmp/emu-$AVD_PREFIX.log）" >&2; exit 1; fi
      sleep 2
      s=$(resolve_serial)
    done
  fi
  wait_boot "$s"
  echo "$s"
}

cmd_install() {
  local s
  s=$(require_serial)
  (cd "$ROOT" && ./gradlew assembleDebug)
  "$ADB" -s "$s" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  echo "已安装到 $s"
}

cmd_launch() {
  local s
  s=$(require_serial)
  "$ADB" -s "$s" shell am start -n "$ACTIVITY"
  echo "已拉起 $PKG @ $s"
}

cmd_restart() {
  local s
  s=$(require_serial)
  "$ADB" -s "$s" shell am force-stop "$PKG"
  "$ADB" -s "$s" shell am start -n "$ACTIVITY"
  echo "已重启 $PKG @ $s"
}

cmd_stop() {
  local s
  s=$(require_serial)
  "$ADB" -s "$s" shell am force-stop "$PKG"
  echo "已停掉 $PKG @ $s"
}

cmd_cap() {
  local s out
  s=$(require_serial)
  out="${1:-/tmp/$AVD_PREFIX-$(date +%H%M%S).png}"
  "$ADB" -s "$s" exec-out screencap -p >"$out"
  echo "$out"
}

cmd_uadump() {
  local s out
  s=$(require_serial)
  out="${1:-/tmp/$AVD_PREFIX-ui.xml}"
  # 先删旧 dump：失败时宁可报错，也不能 pull 回过期文件
  "$ADB" -s "$s" shell rm -f /sdcard/win.xml >/dev/null
  if ! "$ADB" -s "$s" shell uiautomator dump /sdcard/win.xml 2>/dev/null | grep -q "dumped"; then
    sleep 2  # 转场/动画中 UiTestAutomationBridge 常拿不到根节点，缓一拍重试
    "$ADB" -s "$s" shell uiautomator dump /sdcard/win.xml | grep -q "dumped"
  fi
  "$ADB" -s "$s" pull /sdcard/win.xml "$out" >/dev/null
  echo "$out"
}

case "${1:-help}" in
  up) cmd_up ;;
  serial) require_serial ;;
  install) cmd_install ;;
  launch) cmd_launch ;;
  restart) cmd_restart ;;
  stop) cmd_stop ;;
  cap) shift; cmd_cap "$@" ;;
  uadump) shift; cmd_uadump "$@" ;;
  *)
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
