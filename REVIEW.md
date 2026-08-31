# App Status Review — `qemaa` (Linux VM)

**Review date:** 2026-08-31 · **Head of `main`:** `d66727f` ("fix(ci): pass --repo to gh calls that run before checkout")

---

## 1. Executive summary

**Status: WORKING and SHIPPING.** The app is in its best state so far. The full CI
pipeline is green end-to-end, and the latest release (`android-qemu-v1.1.100`,
published 2026-08-30) contains an APK that was **proven on an emulator**: install →
tap Start → QEMU spawns from `nativeLibraryDir` → Alpine guest boots all the way to
the login prompt over user-mode networking → tap Stop → QEMU exits cleanly → app survives.

Below the surface there are a handful of real (but non-fatal) input-handling bugs,
some dead code/features that are advertised but unreachable, and repo hygiene issues
(stale docs, missing Gradle wrapper, unused dependencies).

| Area | State |
|---|---|
| CI (build → emulator test → release) | 🟢 Green (runs #100 chain, 17h ago) |
| Latest release APK | 🟢 `android-qemu-v1.1.100` — 105 MB, dual-ABI QEMU 9.0.0 |
| Emulator E2E proof | 🟢 Full guest boot to Alpine login prompt |
| Unit tests (Robolectric) | 🟢 6+ engine tests covering past regressions |
| Terminal input correctness | 🟡 Ctrl+C and Tab keys send the wrong bytes |
| Dead / unreachable features | 🟡 Nano editor, `RealNativeProcessEngine` |
| Docs & repo hygiene | 🟡 README drift, stale `android-build.txt`, no `gradlew` |
| Device reach | 🟠 `minSdk 36` → Android 16+ only |

---

## 2. What the app is

A Kotlin/Jetpack-Compose Android app (`com.aistudio.linuxvm.qemu`, v1.1 / versionCode 2)
that runs a **real Alpine Linux VM in QEMU** on-device:

- **QEMU 9.0.0** system emulator cross-compiled with the NDK for **both host ABIs**
  (`x86_64` + `arm64-v8a`), always emulating an x86_64 PC via TCG (no KVM).
- Binary ships as `jniLibs/<abi>/libqemu-system-x86_64.so` with `useLegacyPackaging`,
  so the installer extracts it to `nativeLibraryDir` — the only place an API 29+ app
  may `exec()` (SELinux W^X). This was a hard-won fix and is now covered by tests.
- Guest boots via Alpine **netboot** (`-kernel`/`-initrd`, `ip=dhcp`, `alpine_repo=…`)
  into a RAM-backed rootfs; no disk image. External DNS (8.8.8.8/1.1.1.1) is pinned in
  the guest cmdline because slirp's virtual DNS (10.0.2.3) reads a host
  `/etc/resolv.conf` that doesn't exist on Android.
- UI: single-screen terminal (xterm.js in a WebView, with a "classic" Compose
  fallback), start/stop, font size, 6 color themes, input history, touch accessory
  bar (Tab / Ctrl+C / history), and a hardware virtualization report dialog
  (KVM/pKVM/AVF/root detection).

Code size: ~980 lines of engine/VM Kotlin + ~1,860 lines of UI Compose code.

---

## 3. Verified working (evidence)

- **CI chain green on `main`** (17h ago): *Android CI & QEMU Build* → *Android Emulator
  Test* (3m23s) → *Tag & Release*. One earlier *Tag & Release* failure was fixed by the
  head commit itself (`--repo` for pre-checkout `gh` calls).
- **Release `android-qemu-v1.1.100`** built from `d66727f` with evidence artifacts
  (`qemu-version.txt`, `emulator-boot-progress.txt`, `emulator-screen-at-login.png`, SHA256SUMS).
- **The emulator test is genuinely thorough** (`.github/scripts/emulator-test.sh`):
  asserts both `lib/<abi>/libqemu-system-x86_64.so` are in the APK, runs the binary
  standalone, verifies it inside `nativeLibraryDir` after install, checks no crash on
  launch, taps Start via UI automation, requires the **login prompt** (explicitly
  treating the initramfs emergency shell as FAILURE), verifies the QEMU process is
  alive and then gone after Stop, and that the app process survived.
- **Regression tests encode real production bugs** already fixed: single-ABI APKs
  (error must name the device ABI), `root=/dev/ram0` emergency shell, DNS pinning,
  firmware via `-L`, binary resolution preferring `nativeLibraryDir`.

---

## 4. Bugs found (real, ordered by impact)

### 4.1 The "Ctrl+C" key sends Enter, not SIGINT — 🔴 functional bug
`MainViewModel.handleCtrlC()` → `VmManagerService.sendInput("")` →
`QemuEngine.sendInput()` writes `input + "\n"` — i.e. a bare **newline (0x0A)** to the
serial console. A real Ctrl+C is byte **0x03 (ETX)**. Users cannot interrupt a running
command (`ping`, `apk`, …); the button just presses Enter. Fix: add a raw-write path in
`QemuEngine` (no `\n` appended) and send `"\u0003"`.

### 4.2 Tab completion sends `\t` **followed by Enter** — 🔴 functional bug
`handleTab()` → `sendInput("\t")` → QEMU stdin receives `"\t\n"`. The shell completes
the word and **immediately executes it**. Fix: same raw-write path as above (`"\t"` only).

### 4.3 Any QEMU stderr line flips the UI to "Error" — 🟡 misleading state
`VmManagerService` sets `VmState.ERROR` on *every* `onError` callback, but QEMU prints
benign warnings to stderr while the VM keeps running. The header shows "Error" during a
healthy boot. Fix: only treat non-zero exit / startup exception as ERROR (or demote
stderr lines to warning-colored output).

### 4.4 Terminal buffer is unbounded and O(n²) — 🟡 performance
`_terminalLines.value = _terminalLines.value + line` copies the whole list per line and
never trims. Long sessions (a full boot prints thousands of lines) will grow memory and
recomposition cost until jank/OOM. Fix: cap the buffer (e.g. 5–10k lines) and append via
a synchronized list, or feed xterm.js directly.

### 4.5 `stop()`'s "graceful quit" goes to the guest, not the monitor — 🟢 cosmetic
The code sends `quit` to stdin claiming it's the QEMU monitor, but `-monitor none` is
configured; the string is typed into the guest console instead. Harmless (`destroy()`
does the real work), but the comment/behavior should match reality.

### 4.6 Input is dropped while `BOOTING` — 🟢 minor
`sendInput` gates on `VmState.RUNNING`; state only flips when output matches
`"Welcome to Alpine Linux"`, `"login:"`, `"# "` or `"$ "`. Keystrokes at an early
login prompt are lost if pattern matching lags.

---

## 5. Dead / unreachable code & features

- **Nano editor is unreachable.** `MainViewModel.openNano()` has **zero callers** — the
  modal (`NanoEditorModal.kt`, 229 lines) can never open from the UI, and
  `saveAndExitNano()` doesn't persist anything (code comment admits the file layer
  "is not connected"). Yet the README advertises "Built-in nano editor".
- **`RealNativeProcessEngine`** is unused by the app (only referenced by a Robolectric
  test). The README's "Without QEMU (Native Shell Mode)" section describes a mode the
  app no longer has (`VmManagerService` is "No fallback modes - pure QEMU VM only").
- **Unused dependencies** (zero references in code): `firebase-ai`, `firebase-appcheck-recaptcha`,
  `retrofit`, `converter-moshi`, `moshi-kotlin` (+KSP codegen), `okhttp`, `logging-interceptor`,
  and the `google-services` plugin. AI-Studio template leftovers; they bloat the APK and the build.

---

## 6. Repo hygiene / docs

- **`android-build.txt` is a stale snapshot** of an old workflow (NDK 26b, API 24,
  x86_64-only, JDK 17, no emulator test). It contradicts the real
  `.github/workflows/android-build.yml` (NDK 30-beta3, API 36, dual-ABI, JDK 21).
  Delete it or regenerate; it will mislead anyone reading it.
- **README drift**: the "QEMU Configuration" block says `-m 256` (code uses `-m 512`)
  and omits `-L`, `-monitor none`, networking and DNS pinning; "File Editor" feature is
  unreachable (see §5); the "Native Shell Mode" section is obsolete.
- **No Gradle wrapper checked in** — `gradlew`/`gradle-wrapper.jar` are missing (only
  `gradle-wrapper.properties` exists); CI regenerates it. Local builds fail out of the
  box. Commit the wrapper (`gradle wrapper --gradle-version 9.3.1`).
- **WebView hardening (low risk):** the xterm WebView enables `javaScriptEnabled` +
  `addJavascriptInterface` + `allowFileAccess = true` and loads
  `file:///android_asset/...`. Only bundled content is ever loaded (no CDN refs — good),
  so risk is minimal, but `allowFileAccess` is unnecessary and could be dropped.
- Naming inconsistency: `applicationId com.aistudio.linuxvm.qemu` vs `namespace com.example`.

---

## 7. Distribution reach — the strategic issue

`minSdk = 36` restricts installs to **Android 16+ only**. Nothing in the Kotlin code
requires API 36; the constraint stems from the build targeting API 36 everywhere
(compileSdk, NDK sysroot in the Dockerfile). Since the QEMU binaries would need
rebuilding against a lower API, lowering minSdk (e.g. to 29, keeping the
`nativeLibraryDir` exec path) is a deliberate but high-value decision: it would
multiply the addressable device base by orders of magnitude. Worth deciding explicitly.

---

## 8. Recommended next steps (priority order)

1. **Fix raw key input** in `QemuEngine` — add `sendRaw(bytes)` (no `\n`); use it for
   Ctrl+C (`0x03`) and Tab (`0x09`). Small change, big UX win. Add unit tests.
2. **Stop flipping to ERROR on stderr** — only real failures should show "Error".
3. **Bound the terminal buffer** to avoid O(n²) growth / jank on long sessions.
4. **Decide the nano editor's fate** — wire it to the VM filesystem or delete it and
   the README claim (and `RealNativeProcessEngine` + native-shell README section).
5. **Clean the repo**: delete/regenerate `android-build.txt`, fix README (-m 512,
   actual QEMU args), commit the Gradle wrapper, strip unused Firebase/Retrofit/Moshi deps.
6. **Decide on `minSdk`** — Android 16-only vs widening to API 29+ (requires QEMU rebuild).
7. Optional: trim `stop()`'s fake monitor-quit; make `sendInput` accept input during BOOTING.

---

## 9. Overall assessment

The hard problems are **solved and proven**: cross-compiling QEMU for Android on two
ABIs, satisfying the W^X exec policy, netbooting Alpine through slirp with working DNS,
and a CI pipeline that actually boots the guest to a login prompt before releasing.
That is genuinely rare rigor for this kind of app. What remains is polish: two
terminal-input bugs, dead features advertised as working, stale docs, and a very
narrow `minSdk` that limits who can install it.
