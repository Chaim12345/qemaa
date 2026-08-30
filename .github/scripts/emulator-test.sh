#!/usr/bin/env bash
# Real emulator test for the QEMU Linux-VM app.
#
# Executed by the "Android Emulator Test" workflow via
# reactivecircus/android-emulator-runner@v2, whose `script:` input is run
# line-by-line in separate /bin/sh processes (variables and multi-line
# control flow do not survive across lines). The workflow therefore calls
# this file as a single command: `bash .github/scripts/emulator-test.sh`.
#
# Stages:
#   1. The bundled QEMU binary must execute standalone on the emulator.
#   2. The APK must install, with libqemu-system-x86_64.so extracted into
#      nativeLibraryDir (validates the exec fix for API 29+ W^X policy).
#   3. The app must launch without crashing.
#   4. Tapping "Start" must spawn the QEMU process.
#   5. QEMU must stay alive for ~90s.
#   6. VM console output is collected (best effort; TCG is slow).
set -euo pipefail

ARTIFACTS="${GITHUB_WORKSPACE}/emulator-artifacts"
mkdir -p "${ARTIFACTS}"

APK="$(find "${GITHUB_WORKSPACE}/apk" -maxdepth 2 -type f -name '*.apk' -print -quit)"
if [ -z "${APK}" ]; then
  echo "::error::No APK found in the downloaded artifact"
  exit 1
fi
echo "Testing APK: ${APK} ($(du -h "${APK}" | cut -f1))"

SDK_LEVEL="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Emulator API level: ${SDK_LEVEL} (expected ${ANDROID_API_LEVEL})"
echo "## Emulator test (API ${SDK_LEVEL})" >> "${GITHUB_STEP_SUMMARY}"

# ── 1. Bundled QEMU binary must execute standalone on the emulator ──────────
echo "=== [1/6] Standalone QEMU binary check ==="
if ! unzip -l "${APK}" | grep -q "lib/${ANDROID_ARCH}/libqemu-system-x86_64.so"; then
  echo "::error::APK does not contain lib/${ANDROID_ARCH}/libqemu-system-x86_64.so (was it built by an outdated workflow?)"
  unzip -l "${APK}" | head -80 > "${ARTIFACTS}/apk-contents.txt" || true
  exit 1
fi
BIN_DIR="$(mktemp -d)"
unzip -o -j "${APK}" "lib/${ANDROID_ARCH}/libqemu-system-x86_64.so" -d "${BIN_DIR}" >/dev/null
file "${BIN_DIR}/libqemu-system-x86_64.so" | tee "${ARTIFACTS}/qemu-binary-file.txt"
adb push "${BIN_DIR}/libqemu-system-x86_64.so" /data/local/tmp/qemu-system-x86_64 >/dev/null
adb shell "chmod 0755 /data/local/tmp/qemu-system-x86_64"
adb shell "ls -lh /data/local/tmp/qemu-system-x86_64"
if adb shell "/data/local/tmp/qemu-system-x86_64 --version" 2>&1 | tee "${ARTIFACTS}/qemu-version.txt"; then
  grep -q "QEMU emulator version" "${ARTIFACTS}/qemu-version.txt" || {
    echo "::error::QEMU binary ran but did not report its version"
    exit 1
  }
else
  echo "::error::Bundled QEMU binary does not run on the emulator (API ${SDK_LEVEL})"
  exit 1
fi
echo "* Standalone QEMU binary: $(head -n1 "${ARTIFACTS}/qemu-version.txt")" >> "${GITHUB_STEP_SUMMARY}"

# ── 2. Install the APK ───────────────────────────────────────────────────────
echo "=== [2/6] Install APK ==="
adb install -r "${APK}" | tee "${ARTIFACTS}/adb-install.txt"
grep -q "Success" "${ARTIFACTS}/adb-install.txt" || {
  echo "::error::adb install failed"
  exit 1
}

# Verify the QEMU binary was extracted into the app's nativeLibraryDir
# (this validates the core fix: exec from nativeLibraryDir, not filesDir).
NATIVE_DIR="$(adb shell dumpsys package "${APP_PACKAGE}" \
  | tr -d '\r' | grep -oE 'legacyNativeLibraryDir=[^ ]+' | head -1 | cut -d= -f2)"
echo "App nativeLibraryDir: ${NATIVE_DIR}"
adb shell "ls -la ${NATIVE_DIR}" | tee "${ARTIFACTS}/native-lib-dir.txt"
if ! adb shell "ls ${NATIVE_DIR}" | tr -d '\r' | grep -q "libqemu-system-x86_64.so"; then
  echo "::error::libqemu-system-x86_64.so was NOT extracted into nativeLibraryDir (check useLegacyPackaging/extractNativeLibs)"
  exit 1
fi
echo "* QEMU binary present in nativeLibraryDir" >> "${GITHUB_STEP_SUMMARY}"

# Keep the setup wizard / keyguard out of the way.
adb shell settings put global device_provisioned 1
adb shell settings put secure user_setup_complete 1
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_WAKEUP || true

# ── 3. Launch the app and verify it does not crash ──────────────────────────
echo "=== [3/6] Launch app ==="
adb logcat -c
adb shell am start -W -n "${APP_PACKAGE}/${APP_ACTIVITY}" | tee "${ARTIFACTS}/am-start.txt"
sleep 8
adb exec-out screencap -p > "${ARTIFACTS}/screen-after-launch.png" || true

APP_PID="$(adb shell pidof "${APP_PACKAGE}" | tr -d '\r')"
if [ -z "${APP_PID}" ]; then
  echo "::error::App process died right after launch"
  adb logcat -d > "${ARTIFACTS}/logcat-launch.txt" || true
  exit 1
fi
echo "App process pid: ${APP_PID}"
adb shell dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mFocusedWindow" | head -2 \
  | tee -a "${ARTIFACTS}/am-start.txt" || true

if adb logcat -d -b crash 2>/dev/null | grep -q "${APP_PACKAGE}"; then
  echo "::error::App crashed at launch (see logcat-launch.txt)"
  adb logcat -d > "${ARTIFACTS}/logcat-launch.txt" || true
  exit 1
fi
echo "* App launched OK (pid ${APP_PID})" >> "${GITHUB_STEP_SUMMARY}"

# ── 4. Tap the "Start" button to boot the VM ────────────────────────────────
echo "=== [4/6] Tap 'Start' to boot the VM ==="
cat > /tmp/ui_tap.py <<'PYEOF'
import re
import subprocess
import sys
import time

label = sys.argv[1]
deadline = time.time() + 120

while time.time() < deadline:
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
                   capture_output=True, text=True)
    time.sleep(1)
    xml = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"],
                         capture_output=True, text=True).stdout or ""
    if "<node" in xml:
        with open("/tmp/ui.xml", "w", encoding="utf-8") as f:
            f.write(xml)
        pattern = re.compile(
            r'<node[^>]*\btext="' + re.escape(label)
            + r'"[^>]*\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        match = pattern.search(xml)
        if match:
            x = (int(match.group(1)) + int(match.group(3))) // 2
            y = (int(match.group(2)) + int(match.group(4))) // 2
            print("Tapping '{}' at ({},{})".format(label, x, y))
            subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])
            sys.exit(0)
    time.sleep(2)

print("::error::UI element with text '{}' not found within 120s".format(label))
sys.exit(1)
PYEOF

if ! python3 /tmp/ui_tap.py "Start"; then
  cp /tmp/ui.xml "${ARTIFACTS}/ui-no-start-button.xml" 2>/dev/null || true
  adb exec-out screencap -p > "${ARTIFACTS}/screen-no-start-button.png" || true
  adb logcat -d > "${ARTIFACTS}/logcat-no-start-button.txt" || true
  exit 1
fi
cp /tmp/ui.xml "${ARTIFACTS}/ui-at-start-tap.xml" 2>/dev/null || true

# ── 5. Wait for the QEMU process to appear ──────────────────────────────────
echo "=== [5/6] Waiting for the QEMU process to appear ==="
QEMU_STARTED=""
for i in $(seq 1 72); do
  if adb shell "ps -A" | grep -q "qemu-system"; then
    echo "QEMU process appeared after ~$((i * 5))s"
    QEMU_STARTED="yes"
    break
  fi
  sleep 5
done
adb shell "ps -A" | grep "qemu-system" | tee "${ARTIFACTS}/qemu-ps.txt" || true

if [ -z "${QEMU_STARTED}" ]; then
  echo "::error::The QEMU process never started after tapping Start"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml > "${ARTIFACTS}/ui-no-qemu.xml" || true
  adb exec-out screencap -p > "${ARTIFACTS}/screen-no-qemu.png" || true
  adb logcat -d > "${ARTIFACTS}/logcat-no-qemu.txt" || true
  exit 1
fi
echo "* QEMU process: $(cat "${ARTIFACTS}/qemu-ps.txt")" >> "${GITHUB_STEP_SUMMARY}"

# ── 6. QEMU must stay alive; look for VM console output ─────────────────────
echo "=== [6/6] QEMU stability and VM console output ==="
ALIVE=0
for i in $(seq 1 6); do
  sleep 15
  if adb shell "ps -A" | grep -q "qemu-system"; then
    ALIVE=$((ALIVE + 1))
  fi
done
echo "QEMU alive-checks: ${ALIVE}/6 (~90s window)"
echo "* QEMU alive-checks: ${ALIVE}/6" >> "${GITHUB_STEP_SUMMARY}"
if [ "${ALIVE}" -lt 5 ]; then
  echo "::error::QEMU process exited shortly after starting (expected it to keep running)"
  adb logcat -d > "${ARTIFACTS}/logcat-qemu-died.txt" || true
  adb exec-out screencap -p > "${ARTIFACTS}/screen-qemu-died.png" || true
  exit 1
fi

# Give the (slow, TCG-emulated) Alpine kernel some time to print.
sleep 30
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
adb exec-out cat /sdcard/ui.xml > "${ARTIFACTS}/ui-after-boot.xml" || true
adb exec-out screencap -p > "${ARTIFACTS}/screen-final.png" || true
adb logcat -d > "${ARTIFACTS}/logcat-final.txt" || true

if grep -qiE "Failed to start QEMU|Missing required assets|Permission denied" \
    "${ARTIFACTS}/ui-after-boot.xml" 2>/dev/null; then
  echo "::error::The app UI reports a QEMU start failure (see ui-after-boot.xml / screen-final.png)"
  exit 1
fi

if grep -qiE "Welcome to Alpine|Alpine Linux|login:|IP-Config|Linux version|QEMU:" \
    "${ARTIFACTS}/ui-after-boot.xml" 2>/dev/null; then
  echo "VM console output detected in the app terminal."
  echo "* VM console output: detected" >> "${GITHUB_STEP_SUMMARY}"
else
  echo "::warning::No VM console output visible yet (TCG emulation is slow); QEMU process confirmed alive."
  echo "* VM console output: not visible yet (TCG is slow)" >> "${GITHUB_STEP_SUMMARY}"
fi

echo "SMOKE TEST PASSED"
echo "* Result: PASS" >> "${GITHUB_STEP_SUMMARY}"
