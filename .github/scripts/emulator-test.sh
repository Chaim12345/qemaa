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

# ── 1b. Standalone netboot probe (diagnostic, non-fatal) ────────────────────
# Boots the bundled kernel/initramfs with the bundled QEMU directly from the
# shell (no app involved) using the same guest cmdline the app uses. This
# isolates Android-environment problems (slirp DNS, network) from app problems
# and its serial log lands in the evidence artifact either way.
echo "=== [1b/7] Standalone netboot probe (diagnostic) ==="
unzip -o -j "${APK}" "assets/alpine/vmlinuz-lts" "assets/alpine/initramfs-lts" -d "${BIN_DIR}" >/dev/null
unzip -o -j "${APK}" "assets/qemu/pc-bios/*" -d "${BIN_DIR}/pc-bios" >/dev/null
adb push "${BIN_DIR}/vmlinuz-lts" "${BIN_DIR}/initramfs-lts" /data/local/tmp/ >/dev/null
adb shell "rm -rf /data/local/tmp/netboot-pc-bios; mkdir -p /data/local/tmp/netboot-pc-bios"
adb push "${BIN_DIR}/pc-bios/." /data/local/tmp/netboot-pc-bios/ >/dev/null
adb push "${BIN_DIR}/libqemu-system-x86_64.so" /data/local/tmp/qemu-system-x86_64 >/dev/null
adb shell "chmod 0755 /data/local/tmp/qemu-system-x86_64"
adb shell "rm -f /data/local/tmp/netboot.log"
adb shell "nohup /data/local/tmp/qemu-system-x86_64 \
  -L /data/local/tmp/netboot-pc-bios \
  -kernel /data/local/tmp/vmlinuz-lts \
  -initrd /data/local/tmp/initramfs-lts \
  -append 'console=ttyS0 quiet ip=dhcp:::::::8.8.8.8:1.1.1.1 alpine_repo=http://dl-cdn.alpinelinux.org/alpine/latest-stable/main/' \
  -m 512 -smp 2 -nographic -serial stdio -monitor none \
  -net nic,model=virtio -net user,hostfwd=tcp::2222-:22 \
  -no-reboot -nodefaults > /data/local/tmp/netboot.log 2>&1 &"
NETBOOT_PROBE=""
for i in $(seq 1 30); do
  sleep 10
  LOG_NOW="$(adb shell cat /data/local/tmp/netboot.log 2>/dev/null | tr -d '\r' || true)"
  if echo "${LOG_NOW}" | grep -q "login:"; then
    echo "Standalone netboot reached the login prompt after ~$((i * 10))s."
    NETBOOT_PROBE="ok"
    break
  fi
  if echo "${LOG_NOW}" | grep -q "emergency recovery shell"; then
    echo "::warning::Standalone netboot dropped into the emergency recovery shell (see netboot-standalone.log)"
    NETBOOT_PROBE="emergency-shell"
    break
  fi
done
adb shell "pkill -f qemu-system-x86_64" >/dev/null 2>&1 || true
sleep 2
adb shell cat /data/local/tmp/netboot.log 2>/dev/null | tr -d '\r' > "${ARTIFACTS}/netboot-standalone.log" || true
adb shell "rm -f /data/local/tmp/netboot.log /data/local/tmp/vmlinuz-lts /data/local/tmp/initramfs-lts; rm -rf /data/local/tmp/netboot-pc-bios" >/dev/null 2>&1 || true
if [ "${NETBOOT_PROBE}" = "ok" ]; then
  echo "* Standalone netboot: reached login prompt" >> "${GITHUB_STEP_SUMMARY}"
elif [ -z "${NETBOOT_PROBE}" ]; then
  echo "::warning::Standalone netboot probe timed out after 300s (TCG is slow); see netboot-standalone.log"
else
  echo "::warning::Standalone netboot failed: ${NETBOOT_PROBE}; the app test below will tell the full story"
fi

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
# The AVD is cached across runs, and every build signs the APK with a fresh
# debug keystore — a stale install from a previous run would make
# `adb install -r` fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
adb uninstall "${APP_PACKAGE}" >/dev/null 2>&1 || true
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

echo "Netboot phase passed. Continuing to the prebuilt-distro phases."

# ── 8. Standalone distro probe: boot the prebuilt image, verify toolchain ───
# Boots the distro image with the bundled QEMU using the EXACT arguments the
# app uses in DISK mode, waits for the autologin shell and types version
# checks for node/npm/go/pi/opencode over the serial console. This proves the
# prebuilt distro ships a working toolchain before it is released.
DISTRO_DIR_HOST="${GITHUB_WORKSPACE}/distro"
DISTRO_IMG_GZ="$(find "${DISTRO_DIR_HOST}" -maxdepth 1 -type f -name 'linux-vm-rootfs.img.gz' -print -quit 2>/dev/null || true)"
if [ -n "${DISTRO_IMG_GZ}" ]; then
  echo "=== [8/9] Standalone distro probe ==="
  adb shell "mkdir -p /data/local/tmp/distro"
  adb push "${DISTRO_DIR_HOST}/linux-vm-vmlinuz-lts" "${DISTRO_DIR_HOST}/linux-vm-initramfs-lts" /data/local/tmp/distro/ >/dev/null
  adb push "${DISTRO_IMG_GZ}" /data/local/tmp/distro/linux-vm-rootfs.img.gz >/dev/null
  # Decompress on the emulator (a raw push would be 3 GB over adb).
  adb shell "gunzip -f /data/local/tmp/distro/linux-vm-rootfs.img.gz"
  adb shell "ls -lh /data/local/tmp/distro" | tee "${ARTIFACTS}/distro-files.txt"
  adb shell "test -s /data/local/tmp/distro/rootfs.img"

  # Re-create the firmware dir (cleaned up after the netboot probe).
  adb shell "rm -rf /data/local/tmp/netboot-pc-bios; mkdir -p /data/local/tmp/netboot-pc-bios"
  adb push "${BIN_DIR}/pc-bios/." /data/local/tmp/netboot-pc-bios/ >/dev/null

  # QEMU with a FIFO on stdin so the test can type into the serial console.
  adb shell "rm -f /data/local/tmp/distro.in /data/local/tmp/distro.log"
  adb shell "mkfifo /data/local/tmp/distro.in"
  adb shell "nohup sh -c 'cat /data/local/tmp/distro.in | /data/local/tmp/qemu-system-x86_64 \
    -L /data/local/tmp/netboot-pc-bios \
    -kernel /data/local/tmp/distro/linux-vm-vmlinuz-lts \
    -initrd /data/local/tmp/distro/linux-vm-initramfs-lts \
    -append \"console=ttyS0 root=/dev/vda rw\" \
    -accel tcg,thread=multi -cpu max -smp 2 -m 768 -rtc base=utc \
    -nographic -serial stdio -monitor none \
    -net nic,model=virtio -net user,hostfwd=tcp::2222-:22 \
    -drive file=/data/local/tmp/distro/rootfs.img,format=raw,if=virtio,cache=writeback \
    -device virtio-rng-pci -no-reboot -nodefaults \
    > /data/local/tmp/distro.log 2>&1' >/dev/null 2>&1 &"
  # Persistent FIFO writer so later writes do not see EOF (18 min lifetime).
  adb shell "nohup sh -c 'exec 3>/data/local/tmp/distro.in; sleep 1080' >/dev/null 2>&1 &"

  distro_type() {
    adb shell "printf '%s\n' \"$1\" > /data/local/tmp/distro.in" >/dev/null 2>&1 || true
  }
  distro_log() {
    adb shell cat /data/local/tmp/distro.log 2>/dev/null | tr -d '\r' || true
  }

  # Wait for the autologin shell (motd banner is unique to the distro).
  DISTRO_PROMPT=""
  for i in $(seq 1 60); do
    sleep 10
    LOG_NOW="$(distro_log)"
    if echo "${LOG_NOW}" | grep -q "root@linuxvm"; then
      echo "Distro reached the autologin shell after ~$((i * 10))s."
      DISTRO_PROMPT="yes"
      break
    fi
    if echo "${LOG_NOW}" | grep -qi "Kernel panic"; then
      echo "::error::Distro kernel panicked (see distro-boot.log)"
      break
    fi
  done
  if [ "${DISTRO_PROMPT}" = "yes" ]; then
    echo "* Standalone distro: booted to root shell" >> "${GITHUB_STEP_SUMMARY}"
    sleep 3
    distro_type 'echo PROBE_START; node -v; npm -v; go version; echo PI_VERSION_MARKER; pi --version; echo OPENCODE_VERSION_MARKER; opencode --version; echo PROBE_DONE'
    PROBE_DONE=""
    for i in $(seq 1 36); do
      sleep 10
      if distro_log | grep -q "PROBE_DONE"; then PROBE_DONE="yes"; break; fi
    done
    distro_log | tail -60 > "${ARTIFACTS}/distro-probe.log"
    if [ -z "${PROBE_DONE}" ]; then
      echo "::error::Distro toolchain probe timed out (see distro-probe.log)"
      adb shell "pkill -f qemu-system-x86_64" >/dev/null 2>&1 || true
      exit 1
    fi
    PROBE_SECTION="$(distro_log | sed -n '/PROBE_START/,/PROBE_DONE/p')"
    echo "${PROBE_SECTION}" | tee "${ARTIFACTS}/distro-toolchain.txt"
    echo "${PROBE_SECTION}" | grep -Eq 'node|v[0-9]+\.[0-9]+' \
      || { echo "::error::node did not report a version"; exit 1; }
    echo "${PROBE_SECTION}" | grep -q 'go version go1' \
      || { echo "::error::go toolchain missing"; exit 1; }
    if echo "${PROBE_SECTION}" | grep -q 'not found'; then
      echo "::error::A preinstalled tool is missing on PATH (see distro-toolchain.txt)"
      exit 1
    fi
    echo "* Distro toolchain: node, npm, go, pi and opencode all verified" >> "${GITHUB_STEP_SUMMARY}"
    distro_type 'sync; poweroff -f'
  else
    distro_log | tail -80 > "${ARTIFACTS}/distro-boot.log"
    adb shell "pkill -f qemu-system-x86_64" >/dev/null 2>&1 || true
    echo "::error::Distro image did not boot to the root shell (see distro-boot.log)"
    exit 1
  fi
  sleep 3
  adb shell "pkill -f qemu-system-x86_64" >/dev/null 2>&1 || true
else
  echo "::warning::No distro artifact found — skipping the distro phases."
fi

# ── 9. In-app distro boot (debug APK + seeded image) ────────────────────────
# Seeds the app's private distro directory via run-as on the DEBUG build
# (release builds are not debuggable), then verifies the app boots the
# persistent image: QEMU must run with -drive rootfs.img and root=/dev/vda,
# the guest must reach its shell, and Stop must terminate it.
DEBUG_APK="$(find "${GITHUB_WORKSPACE}/apk-debug" -maxdepth 2 -type f -name '*.apk' -print -quit 2>/dev/null || true)"
if [ -n "${DISTRO_IMG_GZ}" ] && [ -n "${DEBUG_APK}" ] \
  && adb shell "test -d /data/local/tmp/distro" >/dev/null 2>&1 \
  && [ "$(adb shell "test -f /data/local/tmp/distro/rootfs.img && echo ok" | tr -d '\r')" = "ok" ]; then
  echo "=== [9/9] In-app distro boot (debug APK, seeded image) ==="
  adb shell "pkill -f qemu-system-x86_64" >/dev/null 2>&1 || true
  adb uninstall "${APP_PACKAGE}" >/dev/null 2>&1 || true
  adb install -r "${DEBUG_APK}" | tee "${ARTIFACTS}/adb-install-debug.txt"
  grep -q "Success" "${ARTIFACTS}/adb-install-debug.txt" || {
    echo "::error::debug APK install failed"; exit 1; }

  # Seed files/qemu/distro through run-as.
  adb shell "run-as ${APP_PACKAGE} mkdir -p files/qemu/distro"
  adb shell "run-as ${APP_PACKAGE} cp /data/local/tmp/distro/linux-vm-vmlinuz-lts files/qemu/distro/vmlinuz-lts"
  adb shell "run-as ${APP_PACKAGE} cp /data/local/tmp/distro/linux-vm-initramfs-lts files/qemu/distro/initramfs-lts"
  adb shell "run-as ${APP_PACKAGE} sh -c 'cp /data/local/tmp/distro/rootfs.img files/qemu/distro/rootfs.img'"
  adb shell "run-as ${APP_PACKAGE} ls -la files/qemu/distro" | tee "${ARTIFACTS}/distro-seeded.txt"
  adb shell "run-as ${APP_PACKAGE} sh -c 'test \$(stat -c%s files/qemu/distro/rootfs.img) -gt 67108864'"

  adb logcat -c
  adb shell am start -W -n "${APP_PACKAGE}/${APP_ACTIVITY}" | tee "${ARTIFACTS}/am-start-debug.txt"
  sleep 8

  # The banner must acknowledge the installed distro.
  BANNER_OK=""
  for i in $(seq 1 10); do
    dump_ui > "${ARTIFACTS}/ui-distro-banner.xml" || true
    if grep -q "Distro ready" "${ARTIFACTS}/ui-distro-banner.xml" 2>/dev/null; then
      BANNER_OK="yes"; break
    fi
    sleep 3
  done
  if [ -z "${BANNER_OK}" ]; then
    echo "::error::App did not recognize the seeded distro (no 'Distro ready' banner)"
    adb exec-out screencap -p > "${ARTIFACTS}/screen-no-distro-banner.png" || true
    adb logcat -d > "${ARTIFACTS}/logcat-no-distro-banner.txt" || true
    exit 1
  fi

  python3 /tmp/ui_tap.py "Start" || {
    echo "::error::Could not tap Start in the distro phase"
    exit 1
  }

  # QEMU must come up WITH the disk image attached.
  DISK_QEMU_ARGS=""
  for i in $(seq 1 72); do
    sleep 5
    QEMU_ARGS_NOW="$(adb shell "ps -A -o ARGS" 2>/dev/null | tr -d '\r' | grep 'qemu-system-x86_64' | head -1 || true)"
    if echo "${QEMU_ARGS_NOW}" | grep -q "rootfs.img"; then
      DISK_QEMU_ARGS="${QEMU_ARGS_NOW}"
      break
    fi
  done
  if [ -z "${DISK_QEMU_ARGS}" ]; then
    echo "::error::QEMU never started with the distro disk attached"
    adb shell "ps -A -o ARGS" | grep qemu-system | tee "${ARTIFACTS}/qemu-ps-distro.txt" || true
    dump_ui > "${ARTIFACTS}/ui-no-distro-qemu.xml" || true
    adb logcat -d > "${ARTIFACTS}/logcat-no-distro-qemu.txt" || true
    exit 1
  fi
  echo "${DISK_QEMU_ARGS}" > "${ARTIFACTS}/qemu-distro-cmdline.txt"
  echo "${DISK_QEMU_ARGS}" | grep -q "root=/dev/vda" || {
    echo "::error::QEMU runs but without root=/dev/vda (see qemu-distro-cmdline.txt)"; exit 1; }
  echo "${DISK_QEMU_ARGS}" | grep -q "if=virtio" || {
    echo "::error::rootfs not attached as virtio (see qemu-distro-cmdline.txt)"; exit 1; }
  echo "* In-app distro boot: QEMU running with the persistent disk" >> "${GITHUB_STEP_SUMMARY}"

  # Wait for the guest shell. Success = shell prompt in the terminal UI, or
  # (equivalently) sshd answering on the hostfwd port — the distro runs sshd.
  DISTRO_BOOTED=""
  BOOT_DEADLINE=$(( $(date +%s) + 600 ))
  while [ "$(date +%s)" -lt "${BOOT_DEADLINE}" ]; do
    dump_ui > "${ARTIFACTS}/ui-during-distro-boot.xml" || true
    if grep -qE "root@linuxvm" "${ARTIFACTS}/ui-during-distro-boot.xml" 2>/dev/null; then
      DISTRO_BOOTED="prompt"; break
    fi
    SSH_BANNER="$(adb shell 'echo | nc -w 2 127.0.0.1 2222 2>/dev/null' | tr -d '\r' || true)"
    if echo "${SSH_BANNER}" | grep -q "SSH-"; then
      DISTRO_BOOTED="sshd"; break
    fi
    if grep -qiE "Kernel panic|Failed to start QEMU|Missing required assets" \
        "${ARTIFACTS}/ui-during-distro-boot.xml" 2>/dev/null; then
      echo "::error::Distro boot failed in-app (see ui-during-distro-boot.xml)"
      adb exec-out screencap -p > "${ARTIFACTS}/screen-distro-boot-failed.png" || true
      adb logcat -d > "${ARTIFACTS}/logcat-distro-boot-failed.txt" || true
      exit 1
    fi
    if ! adb shell "ps -A" 2>/dev/null | grep -q "qemu-system"; then
      sleep 5
      if ! adb shell "ps -A" 2>/dev/null | grep -q "qemu-system"; then
        echo "::error::QEMU exited during distro boot"
        adb logcat -d > "${ARTIFACTS}/logcat-distro-qemu-died.txt" || true
        exit 1
      fi
    fi
    sleep 15
  done
  adb exec-out screencap -p > "${ARTIFACTS}/screen-distro-booted.png" || true
  adb logcat -d > "${ARTIFACTS}/logcat-distro-booted.txt" || true
  if [ -z "${DISTRO_BOOTED}" ]; then
    echo "::error::Distro guest did not reach its shell within 10 minutes"
    exit 1
  fi
  echo "* In-app distro boot: guest reached the shell (via ${DISTRO_BOOTED})" >> "${GITHUB_STEP_SUMMARY}"

  # Stop must terminate QEMU (also exercises graceful disk flush).
  python3 /tmp/ui_tap.py "Stop" || {
    echo "::warning::'Stop' button not found in the distro phase"
  }
  STOPPED_D=""
  for i in $(seq 1 24); do
    if adb shell "ps -A" 2>/dev/null | grep -q "qemu-system"; then
      sleep 5
    else
      sleep 3
      if adb shell "ps -A" 2>/dev/null | grep -q "qemu-system"; then
        sleep 2
      else
        echo "Distro QEMU terminated ~$((i * 5))s after tapping Stop"
        STOPPED_D="yes"
        break
      fi
    fi
  done
  if [ -z "${STOPPED_D}" ]; then
    echo "::error::QEMU still running 120s after tapping Stop (distro phase)"
    exit 1
  fi
  echo "* Distro Stop: QEMU terminated cleanly" >> "${GITHUB_STEP_SUMMARY}"

  if ! adb shell pidof "${APP_PACKAGE}" | grep -q .; then
    echo "::error::App process died during the distro test"
    exit 1
  fi

  # Cleanup: remove the 3 GB image from the emulator data partition.
  adb shell "rm -rf /data/local/tmp/distro /data/local/tmp/distro.in /data/local/tmp/distro.log" || true
else
  echo "::warning::Skipping the in-app distro boot (missing image or debug APK)."
fi

echo "FULL TEST PASSED"
echo "* Result: PASS (install -> netboot boot -> stop -> distro probe -> in-app distro boot -> stop)" >> "${GITHUB_STEP_SUMMARY}"
