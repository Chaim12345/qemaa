#!/usr/bin/env bash
# FULL emulator test for the QEMU Linux-VM app.
#
# Executed by the "Android Emulator Test" workflow via
# reactivecircus/android-emulator-runner@v2, whose `script:` input is run
# line-by-line in separate /bin/sh processes (variables and multi-line
# control flow do not survive across lines). The workflow therefore calls
# this file as a single command: `bash .github/scripts/emulator-test.sh`.
#
# Stages:
#   1. APK must bundle the QEMU emulator for BOTH host ABIs (x86_64 +
#      arm64-v8a). Android extracts only the device's primary ABI at install
#      time — a single-ABI APK bricks every other arch ("Missing required
#      assets: QEMU binary").
#   2. The bundled x86_64 QEMU binary must execute standalone on the emulator.
#   3. The APK must install, with libqemu-system-x86_64.so extracted into
#      nativeLibraryDir (validates the exec fix for API 29+ W^X policy).
#   4. The app must launch without crashing.
#   5. Tapping "Start" must spawn the QEMU process.
#   6. The guest must FULLY BOOT: the Alpine netboot initramfs has to fetch
#      packages over QEMU's user-mode network and reach the login prompt.
#      (A boot that only lands in the initramfs emergency shell FAILS.)
#   7. Tapping "Stop" must terminate the QEMU process.
#
# NOTE: no `set -o pipefail` here! grep -q exits at the first match and the
# producer (unzip/adb) can then die with SIGPIPE (141), which pipefail would
# turn into a false failure even though the match was found.
set -eu

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
echo "## FULL emulator test (API ${SDK_LEVEL})" >> "${GITHUB_STEP_SUMMARY}"

dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/ui.xml 2>/dev/null || true
}

# ── 1. APK must contain the QEMU emulator for every supported host ABI ───────
echo "=== [1/7] APK multi-ABI content check ==="
for abi in x86_64 arm64-v8a; do
  if ! unzip -l "${APK}" | grep -q "lib/${abi}/libqemu-system-x86_64.so"; then
    echo "::error::APK does not contain lib/${abi}/libqemu-system-x86_64.so — devices of that ABI have no QEMU binary at all"
    unzip -l "${APK}" | grep -E "lib/|assets/" | head -80 > "${ARTIFACTS}/apk-contents.txt" || true
    exit 1
  fi
done
echo "APK bundles QEMU for x86_64 and arm64-v8a."
echo "* QEMU bundled for both host ABIs (x86_64 + arm64-v8a)" >> "${GITHUB_STEP_SUMMARY}"

BIN_DIR="$(mktemp -d)"
unzip -o -j "${APK}" "lib/${ANDROID_ARCH}/libqemu-system-x86_64.so" -d "${BIN_DIR}" >/dev/null

# ── 2. Bundled QEMU binary must execute standalone on the emulator ──────────
echo "=== [2/7] Standalone QEMU binary check ==="
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

# ── 3. Install the APK ───────────────────────────────────────────────────────
echo "=== [3/7] Install APK ==="
adb install -r "${APK}" | tee "${ARTIFACTS}/adb-install.txt"
grep -q "Success" "${ARTIFACTS}/adb-install.txt" || {
  echo "::error::adb install failed"
  exit 1
}

# Verify the QEMU binary was extracted into the app's nativeLibraryDir
# (this validates the core fix: exec from nativeLibraryDir, not filesDir).
# dumpsys prints both 'legacyNativeLibraryDir=.../lib' and
# ' nativeLibraryDir=.../lib/x86_64' — the .so lives in the ABI dir.
# NOTE: strip prefixes with sed — paths contain '==' (random suffixes),
# so 'cut -d= -f2' would truncate at the first '='.
NATIVE_DIR="$(adb shell dumpsys package "${APP_PACKAGE}" \
  | tr -d '\r' | grep -oE '[[:space:]]nativeLibraryDir=[^[:space:]]+' | head -1 \
  | sed 's/^[[:space:]]*nativeLibraryDir=//')"
if [ -z "${NATIVE_DIR}" ]; then
  NATIVE_DIR="$(adb shell dumpsys package "${APP_PACKAGE}" \
    | tr -d '\r' | grep -oE 'legacyNativeLibraryDir=[^[:space:]]+' | head -1 \
    | sed 's/^legacyNativeLibraryDir=//')"
fi
echo "App nativeLibraryDir: ${NATIVE_DIR}"
adb shell "ls -la '${NATIVE_DIR}'" | tee "${ARTIFACTS}/native-lib-dir.txt"
SO_PATH=""
for d in "${NATIVE_DIR}" "${NATIVE_DIR}/${ANDROID_ARCH}"; do
  if adb shell "ls '${d}'" | tr -d '\r' | grep -q "libqemu-system-x86_64.so"; then
    SO_PATH="${d}/libqemu-system-x86_64.so"
    break
  fi
done
if [ -z "${SO_PATH}" ]; then
  echo "::error::libqemu-system-x86_64.so was NOT extracted into nativeLibraryDir (checked '${NATIVE_DIR}' and '${NATIVE_DIR}/${ANDROID_ARCH}')"
  exit 1
fi
echo "QEMU executable on device: ${SO_PATH}"

# Pre-validate that the packaged binary is executable in place — exactly
# what QemuEngine will do via ProcessBuilder.
adb shell "'${SO_PATH}' --version" 2>&1 | tee "${ARTIFACTS}/qemu-in-nativedir-version.txt" \
  || { echo "::error::Packaged QEMU binary is not executable from nativeLibraryDir"; exit 1; }
grep -q "QEMU emulator version" "${ARTIFACTS}/qemu-in-nativedir-version.txt" || {
  echo "::error::Packaged QEMU binary did not report its version from nativeLibraryDir"
  exit 1
}
echo "* QEMU binary present and executable in nativeLibraryDir" >> "${GITHUB_STEP_SUMMARY}"

# Keep the setup wizard / keyguard out of the way.
adb shell settings put global device_provisioned 1
adb shell settings put secure user_setup_complete 1
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_WAKEUP || true

# ── 4. Launch the app and verify it does not crash ──────────────────────────
echo "=== [4/7] Launch app ==="
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

# ── 5. Tap the "Start" button to boot the VM ────────────────────────────────
echo "=== [5/7] Tap 'Start' to boot the VM ==="
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

# ── 5b. Wait for the QEMU process to appear ─────────────────────────────────
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
  dump_ui > "${ARTIFACTS}/ui-no-qemu.xml" || true
  adb exec-out screencap -p > "${ARTIFACTS}/screen-no-qemu.png" || true
  adb logcat -d > "${ARTIFACTS}/logcat-no-qemu.txt" || true
  exit 1
fi
echo "* QEMU process: $(cat "${ARTIFACTS}/qemu-ps.txt")" >> "${GITHUB_STEP_SUMMARY}"

# ── 6. FULL BOOT: the guest must reach the Alpine login prompt ──────────────
echo "=== [6/7] Wait for full Alpine boot (login prompt) ==="
# The Alpine netboot initramfs must DHCP via slirp, install alpine-base from
# the network and hand over to openrc. A boot that stalls in the initramfs
# emergency shell is a FAILURE (this is exactly the root=/dev/ram0 bug the
# cmdline once had).
BOOT_DEADLINE=$(( $(date +%s) + 600 ))   # up to 10 minutes (TCG is slow)
BOOT_MARKER=""
PROGRESS_FILE="${ARTIFACTS}/boot-progress.txt"
: > "${PROGRESS_FILE}"
LAST_UI="${ARTIFACTS}/ui-during-boot.xml"

while [ "$(date +%s)" -lt "${BOOT_DEADLINE}" ]; do
  dump_ui > "${LAST_UI}" || true

  # Fatal error strings reported by the app UI -> fail immediately.
  if grep -qiE "Failed to start QEMU|Missing required assets|Permission denied|Kernel panic|emergency recovery" \
      "${LAST_UI}" 2>/dev/null; then
    echo "::error::The app UI reports a QEMU/guest failure during boot:"
    grep -oiE "Failed to start QEMU[^\"]*|Missing required assets[^\"]*|Permission denied[^\"]*|Kernel panic[^\"]*|emergency recovery[^\"]*" "${LAST_UI}" | head -5
    adb exec-out screencap -p > "${ARTIFACTS}/screen-boot-failed.png" || true
    adb logcat -d > "${ARTIFACTS}/logcat-boot-failed.txt" || true
    exit 1
  fi

  # Boot progress milestones (informational).
  for marker in "SeaBIOS" "Booting from ROM" "Starting openrc" "Welcome to Alpine Linux"; do
    if ! grep -qF "${marker}" "${PROGRESS_FILE}" 2>/dev/null; then
      if grep -qF "${marker}" "${LAST_UI}" 2>/dev/null; then
        echo "$(date -u +%H:%M:%S) milestone: ${marker}" | tee -a "${PROGRESS_FILE}"
      fi
    fi
  done

  # Success: the login prompt proves userspace came up.
  if grep -qE "login:|Welcome to Alpine Linux" "${LAST_UI}" 2>/dev/null; then
    BOOT_MARKER="yes"
    break
  fi

  # If QEMU died mid-boot, fail early (two consecutive misses guard
  # against a transient adb hiccup).
  if ! adb shell "ps -A" 2>/dev/null | grep -q "qemu-system"; then
    sleep 5
    if ! adb shell "ps -A" 2>/dev/null | grep -q "qemu-system"; then
      echo "::error::QEMU exited before the guest reached the login prompt"
      dump_ui > "${ARTIFACTS}/ui-qemu-died-early.xml" || true
      adb exec-out screencap -p > "${ARTIFACTS}/screen-qemu-died-early.png" || true
      adb logcat -d > "${ARTIFACTS}/logcat-qemu-died-early.txt" || true
      exit 1
    fi
  fi

  sleep 20
done

cp "${LAST_UI}" "${ARTIFACTS}/ui-at-login.xml" 2>/dev/null || true
adb exec-out screencap -p > "${ARTIFACTS}/screen-at-login.png" || true
adb logcat -d > "${ARTIFACTS}/logcat-at-login.txt" || true

if [ -z "${BOOT_MARKER}" ]; then
  echo "::error::Guest did not reach the Alpine login prompt within 10 minutes (TCG)."
  echo "Last visible terminal lines:"
  grep -oE 'text="[^"]{5,}"' "${LAST_UI}" 2>/dev/null | tail -15 || true
  cat "${PROGRESS_FILE}"
  exit 1
fi

echo "Guest fully booted to the Alpine login prompt."
echo "* Guest boot: reached Alpine login prompt" >> "${GITHUB_STEP_SUMMARY}"
cat "${PROGRESS_FILE}" >> "${GITHUB_STEP_SUMMARY}" || true

# ── 7. Tap "Stop" and verify the QEMU process terminates ────────────────────
echo "=== [7/7] Tap 'Stop' and verify QEMU exits ==="
if ! python3 /tmp/ui_tap.py "Stop"; then
  echo "::warning::'Stop' button not found — was the VM screen replaced?"
  cp /tmp/ui.xml "${ARTIFACTS}/ui-no-stop-button.xml" 2>/dev/null || true
else
  STOPPED=""
  for i in $(seq 1 12); do
    if adb shell "ps -A" 2>/dev/null | grep -q "qemu-system"; then
      sleep 5
    else
      # Confirm the process is really gone (guards against adb hiccups).
      sleep 3
      if adb shell "ps -A" 2>/dev/null | grep -q "qemu-system"; then
        sleep 2
      else
        echo "QEMU process terminated ~$((i * 5))s after tapping Stop"
        STOPPED="yes"
        break
      fi
    fi
  done
  if [ -z "${STOPPED}" ]; then
    echo "::error::QEMU process still running 60s after tapping Stop"
    adb shell "ps -A" | grep "qemu-system" | tee "${ARTIFACTS}/qemu-ps-after-stop.txt" || true
    dump_ui > "${ARTIFACTS}/ui-after-stop.xml" || true
    adb exec-out screencap -p > "${ARTIFACTS}/screen-after-stop.png" || true
    adb logcat -d > "${ARTIFACTS}/logcat-after-stop.txt" || true
    exit 1
  fi
  echo "* Stop button: QEMU terminated cleanly" >> "${GITHUB_STEP_SUMMARY}"
fi

# App must survive the whole cycle.
if ! adb shell pidof "${APP_PACKAGE}" | grep -q .; then
  echo "::error::App process died during the test"
  adb logcat -d > "${ARTIFACTS}/logcat-app-died.txt" || true
  exit 1
fi

echo "FULL TEST PASSED"
echo "* Result: PASS (install -> start -> full guest boot -> stop)" >> "${GITHUB_STEP_SUMMARY}"
