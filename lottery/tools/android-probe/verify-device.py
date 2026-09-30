#!/usr/bin/env python3
"""Exercise native host -> Godot/GLB/Jolt -> result -> released stage process.

Only controls the lottery_* AVD selected by lottery/tools/emu.sh.
This is engine compatibility evidence, never machine/skin visual acceptance.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
ADB = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))) / "platform-tools/adb"
SERIAL = subprocess.check_output([str(ROOT / "tools/emu.sh"), "serial"], text=True).strip()
PACKAGE = "com.leo.lottery.engineprobe"
REPORT = ROOT / "reports/2026-09-30-it004"


def adb(*args, allow_failure=False):
    result = subprocess.run([str(ADB), "-s", SERIAL, *args], capture_output=True, text=True, timeout=30)
    if result.returncode and not allow_failure:
        raise RuntimeError(result.stderr + result.stdout)
    return result.stdout.strip()


def logs():
    return adb("logcat", "-d", "-v", "brief", "LotteryProbe:I", "godot:I", "AndroidRuntime:E", "*:S")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gles", action="store_true", help="Explicit GLES compatibility probe; not production fallback")
    args = parser.parse_args()
    backend = "gles" if args.gles else "vulkan"
    adb("shell", "am", "force-stop", PACKAGE)
    initial = logs()
    completed = []
    host_pid = None
    try:
        for cycle in range(1, 11):
            adb("shell", "am", "start", "-n", PACKAGE + "/.LauncherActivity", "--ez", "probe_launch", "true", "--ez", "probe_auto_close", "true", "--ez", "probe_gles", "true" if args.gles else "false")
            deadline = time.monotonic() + 35
            while time.monotonic() < deadline:
                new_logs = logs()[len(initial):]
                marker = f"HOST_RETURN launches={cycle} returns={cycle} "
                if marker in new_logs:
                    break
                time.sleep(0.5)
            else:
                raise RuntimeError(f"Cycle {cycle}: no native result within 35 seconds")
            current_pid = adb("shell", "pidof", PACKAGE)
            if not current_pid or (host_pid is not None and current_pid != host_pid):
                raise RuntimeError("Native host died or changed pid")
            host_pid = current_pid
            stages = re.findall(r"STAGE_EVENT pid=(\d+) MODEL_READY", new_logs)
            contacts = re.findall(r"STAGE_EVENT pid=(\d+) CONTACT_OK", new_logs)
            if len(stages) != cycle or len(contacts) != cycle or "CONTACT_FAILED" in new_logs:
                raise RuntimeError(f"Cycle {cycle}: missing model/contact evidence")
            if "QueuePresentKHR failed" in new_logs or "FATAL EXCEPTION" in new_logs:
                raise RuntimeError("Renderer/Android exception occurred")
            completed.append({"cycle": cycle, "host_pid": host_pid, "stage_pid": stages[-1]})
        deadline = time.monotonic() + 5
        while adb("shell", "pidof", PACKAGE + ":stage", allow_failure=True) and time.monotonic() < deadline:
            time.sleep(0.2)
        if adb("shell", "pidof", PACKAGE + ":stage", allow_failure=True):
            raise RuntimeError("Stage process was not released")
        if len({x["stage_pid"] for x in completed}) != 10:
            raise RuntimeError("A terminated engine process was reused")
        result = {"passed": True, "serial": SERIAL, "backend": backend, "cycles": completed,
                  "scope": "GLB import, Jolt contacts, internal events, native result, separate process release; not visual/FPS acceptance."}
    except Exception as error:
        result = {"passed": False, "serial": SERIAL, "backend": backend, "cycles": completed, "error": str(error)}
    REPORT.mkdir(parents=True, exist_ok=True)
    (REPORT / f"android-probe-{backend}-cycles.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    (REPORT / f"android-probe-{backend}-cycles.txt").write_text(logs()[len(initial):])
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
