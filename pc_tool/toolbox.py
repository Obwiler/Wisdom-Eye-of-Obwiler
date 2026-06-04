#!/usr/bin/env python3
"""WEO Glasses Toolbox — CLI for APK dev loop: screenshot, key, deploy, status."""

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent
ADB = str(PROJECT_ROOT / "lib" / "scrcpy" / "adb.exe")
APK_SRC = str(PROJECT_ROOT.parent / "apk" / "build" / "outputs" / "apk" / "debug" / "WEO-debug.apk")
SCREENSHOT_PATH = str(PROJECT_ROOT / "_screenshot.png")
PKG = "com.obwiler.weo"
ACTIVITY = "com.obwiler.weo/.MainActivity"
SERIAL = "1901092603971563"

KEY_MAP = {
    "up": 19, "down": 20, "left": 21, "right": 22,
    "enter": 66, "back": 4, "home": 3, "center": 23,
}


def _adb(*args, timeout: int = 15) -> str:
    cmd = [ADB, "-s", SERIAL, *args]
    r = subprocess.run(cmd, capture_output=True, text=True,
                       timeout=timeout, creationflags=subprocess.CREATE_NO_WINDOW)
    if r.returncode != 0 and r.stderr.strip():
        return f"ERR: {r.stderr.strip()}"
    return r.stdout.strip()


# ── Commands ────────────────────────────────────────────────

def cmd_shot(_args):
    """Take a screenshot and save to _screenshot.png"""
    data = subprocess.run(
        [ADB, "-s", SERIAL, "exec-out", "screencap", "-p"],
        capture_output=True, timeout=10,
        creationflags=subprocess.CREATE_NO_WINDOW,
    ).stdout
    Path(SCREENSHOT_PATH).write_bytes(data)
    size_kb = len(data) / 1024
    print(f"✓ Screenshot saved: {SCREENSHOT_PATH} ({size_kb:.0f} KB)")


def cmd_key(args):
    """Send a key event to the glasses"""
    code = KEY_MAP.get(args.key)
    if code is None:
        print(f"✗ Unknown key: {args.key}. Known: {list(KEY_MAP.keys())}")
        sys.exit(1)
    _adb("shell", "input", "keyevent", str(code))
    print(f"✓ Key sent: {args.key} (code={code})")


def cmd_deploy(args):
    """Install APK, push config, launch app, dismiss permission dialog"""
    apk = args.apk or APK_SRC
    if not Path(apk).exists():
        print(f"✗ APK not found: {apk}")
        sys.exit(1)

    print("→ Stopping app...")
    _adb("shell", "am", "force-stop", PKG)
    time.sleep(0.5)

    print(f"→ Installing: {Path(apk).name}")
    r = subprocess.run([ADB, "-s", SERIAL, "install", "-r", apk],
                       capture_output=True, text=True, timeout=60,
                       creationflags=subprocess.CREATE_NO_WINDOW)
    if "Success" not in r.stdout:
        print(f"✗ Install failed: {r.stdout.strip()}")
        sys.exit(1)
    print("  ✓ Installed")

    if args.push:
        print("→ Pushing config...")
        _adb("shell", "mkdir", "-p", f"/sdcard/Android/data/{PKG}/files")
        _adb("push", args.push, f"/sdcard/Android/data/{PKG}/files/weo_config.json",
             timeout=15)
        print("  ✓ Config pushed")

    print("→ Launching...")
    _adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(2)

    # Dismiss any permission dialog
    _adb("shell", "input", "keyevent", "66")
    time.sleep(0.5)

    # Take post-deploy screenshot
    cmd_shot(args)
    print("✓ Deploy complete")


def cmd_status(_args):
    """Show device and app status"""
    model = _adb("shell", "getprop", "ro.product.model")
    android = _adb("shell", "getprop", "ro.build.version.release")
    print(f"Device: {model} | Android {android} | Serial: {SERIAL}")

    pkgs = _adb("shell", "pm", "list", "packages", PKG)
    installed = PKG in pkgs
    print(f"WEO: {'✓ installed' if installed else '✗ not installed'}")

    if installed:
        pid = _adb("shell", f"pidof {PKG}")
        print(f"     {'✓ running' if pid else '✗ not running'}" + (f" (PID {pid})" if pid else ""))

    # Battery
    batt = _adb("shell", "dumpsys", "battery")
    for line in batt.split("\n"):
        line = line.strip()
        if line.startswith("level:"):
            print(f"Battery: {line.split(':')[-1].strip()}%")


def cmd_uninstall(_args):
    """Uninstall WEO from glasses"""
    r = subprocess.run([ADB, "-s", SERIAL, "uninstall", PKG],
                       capture_output=True, text=True, timeout=15,
                       creationflags=subprocess.CREATE_NO_WINDOW)
    if "Success" in r.stdout:
        print("✓ Uninstalled")
    else:
        print(f"✗ Uninstall failed: {r.stdout.strip()}")


def cmd_nav(args):
    """Navigate through the app: shot → key → shot cycle for DPAD testing"""
    print("=== Before ===")
    cmd_shot(args)

    for key in args.keys:
        print(f"→ Pressing: {key}")
        cmd_key(argparse.Namespace(key=key))
        time.sleep(0.4)

    print("=== After ===")
    cmd_shot(args)


def cmd_type(args):
    """Type text into the currently focused field"""
    _adb("shell", "input", "text", args.text)
    print(f"✓ Typed: {args.text}")


def cmd_tap(args):
    """Tap at screen coordinates (x y)"""
    _adb("shell", "input", "tap", str(args.x), str(args.y))
    print(f"✓ Tapped: ({args.x}, {args.y})")


# ── CLI ─────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="WEO Glasses Toolbox")
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("shot", help="Take screenshot → _screenshot.png")

    p_key = sub.add_parser("key", help="Send DPAD key event")
    p_key.add_argument("key", choices=list(KEY_MAP.keys()), help="Key to send")

    p_deploy = sub.add_parser("deploy", help="Install + launch + screenshot")
    p_deploy.add_argument("--apk", help="APK path (default: latest debug build)")
    p_deploy.add_argument("--push", help="Config JSON to push to device")

    sub.add_parser("status", help="Device + app status")
    sub.add_parser("uninstall", help="Uninstall WEO from glasses")

    p_nav = sub.add_parser("nav", help="Press keys with before/after screenshots")
    p_nav.add_argument("keys", nargs="+", choices=list(KEY_MAP.keys()),
                       help="Keys to press in sequence")

    p_type = sub.add_parser("type", help="Type text")
    p_type.add_argument("text", help="Text to type")

    p_tap = sub.add_parser("tap", help="Tap at coordinates")
    p_tap.add_argument("x", type=int, help="X coordinate")
    p_tap.add_argument("y", type=int, help="Y coordinate")

    args = parser.parse_args()
    if args.command is None:
        parser.print_help()
        sys.exit(1)

    globals()[f"cmd_{args.command}"](args)


if __name__ == "__main__":
    main()
