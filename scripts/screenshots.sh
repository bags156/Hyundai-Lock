#!/usr/bin/env bash
# Run by .github/workflows/screenshots.yml inside a booted emulator. Installs the debug APK,
# seeds sample data (debug builds only) and captures the main screens in light and dark mode.
set -euo pipefail

PKG=io.github.bags156.hyundailock
OUT=shots
mkdir -p "$OUT"

adb shell settings put system screen_off_timeout 1800000 || true
adb shell svc power stayon true || true
adb shell wm dismiss-keyguard || true
adb install -r -g app/build/outputs/apk/debug/app-debug.apk

shoot() {
  sleep "${2:-3}"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "captured $1"
}

scroll_down() {
  adb shell input swipe 540 1900 540 800 700
}

launch() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" "$@" > /dev/null
}

# Taps the centre of the first on-screen element whose text contains $1.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb shell cat /sdcard/ui.xml > ui.xml
  local point
  point=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[1]
for node in ET.parse("ui.xml").iter("node"):
    if needle in (node.get("text") or ""):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -n "$point" ]; then
    adb shell input tap $point
  else
    echo "text not found: $1"
  fi
}

for mode in light dark; do
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  sleep 2

  adb shell pm clear "$PKG" > /dev/null
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  launch
  shoot "$mode-0-signed-out"

  adb shell cmd notification allow_listener "$PKG/$PKG.UnlockListenerService" || true
  adb shell dumpsys deviceidle whitelist "+$PKG" > /dev/null || true
  launch --es demo normal
  shoot "$mode-1-top" 4
  for i in 2 3 4 5; do
    scroll_down
    shoot "$mode-$i-scrolled" 2
  done

  if [ "$mode" = dark ]; then
    launch --es demo normal
    sleep 3
    scroll_down
    sleep 1
    tap_text "7:30"
    shoot "$mode-6-schedule-editor" 3
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    tap_text "+ Hot afternoon"
    shoot "$mode-6b-new-schedule" 3
    adb shell input keyevent KEYCODE_BACK
  fi

  launch --es demo pending
  shoot "$mode-7-pending" 4
  adb shell cmd statusbar expand-notifications || true
  shoot "$mode-8-notification" 3
  adb shell cmd statusbar collapse || true
done
