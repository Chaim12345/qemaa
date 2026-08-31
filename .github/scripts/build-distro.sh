#!/bin/sh
# =============================================================================
# Build the prebuilt Linux VM distro image.
#
# Runs INSIDE an alpine:3.22 container (x86_64) on the CI host — the guest is
# x86_64 too, so chroot needs no qemu-user emulation. Produces in /work/out:
#
#   linux-vm-rootfs.img.gz           ext4 disk image (gzip), boots as /dev/vda
#   linux-vm-rootfs.img.gz.sha256    checksum (consumed by the app installer)
#   linux-vm-vmlinuz-lts             kernel version-matched to the image
#   linux-vm-initramfs-lts           initramfs with virtio+ext4 guaranteed
#   linux-vm-distro-manifest.txt     versions of everything preinstalled
#
# Preinstalled: Node.js + npm, Go, git, tmux, vim/nano, htop,
# @mariozechner/pi-coding-agent (pi) and opencode.
#
# Why the official opencode installer instead of `npm i -g opencode-ai`:
# the npm wrapper does not detect musl and looks for the glibc binary
# (opencode-linux-x64) on Alpine; the install script DOES detect musl
# (opencode-linux-x64-musl).
# =============================================================================
set -eu

OUT=/work/out
ROOTFS=/work/rootfs
APK_MIRROR_BRANCH_V=v3.22
REPO_MAIN="https://dl-cdn.alpinelinux.org/alpine/${APK_MIRROR_BRANCH_V}/main"
REPO_COMMUNITY="https://dl-cdn.alpinelinux.org/alpine/${APK_MIRROR_BRANCH_V}/community"
IMG_SIZE="3g"

log() { printf '\n\033[1;36m[distro] %s\033[0m\n' "$1"; }

# ── Host (container) tools ──────────────────────────────────────────────────
log "installing container build tools"
apk add --no-cache e2fsprogs-extra curl tar xz gzip

rm -rf "$ROOTFS" "$OUT"
mkdir -p "$ROOTFS" "$OUT"

# ── 1. Base rootfs ──────────────────────────────────────────────────────────
log "installing base packages into the rootfs"
mkdir -p "$ROOTFS/etc/apk"
cp /etc/resolv.conf "$ROOTFS/etc/resolv.conf"

apk --root "$ROOTFS" --initdb --no-progress \
  --repository "$REPO_MAIN" \
  --repository "$REPO_COMMUNITY" \
  add \
  alpine-base \
  linux-lts \
  linux-firmware-none \
  nodejs npm \
  go \
  git curl wget ca-certificates \
  bash vim nano less \
  tmux htop \
  openssh-client openssh-server \
  util-linux e2fsprogs \
  tar xz unzip

# ── 2. AI coding agents ─────────────────────────────────────────────────────
log "installing the pi coding agent (@mariozechner/pi-coding-agent)"
log "installing opencode via the official (musl-aware) installer"
chroot "$ROOTFS" /bin/sh -es <<'EOS'
set -eu
export HOME=/root
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# pi: pure TypeScript npm package — runs on the bundled Node.
npm install -g --no-fund --no-audit @mariozechner/pi-coding-agent

# opencode: official install script detects musl and fetches opencode-linux-x64-musl.
curl -fsSL https://opencode.ai/install | sh
ln -sf /root/.opencode/bin/opencode /usr/local/bin/opencode

# Prove both work before we bake the image.
echo "pi:        $(pi --version 2>&1 | head -1)"
echo "opencode:  $(opencode --version 2>&1 | head -1)"
echo "node:      $(node -v)"
echo "go:        $(go version)"

npm cache clean --force || true
EOS

# ── 3. System configuration for the VM ──────────────────────────────────────
log "configuring the guest system"

echo "linuxvm" > "$ROOTFS/etc/hostname"
cat > "$ROOTFS/etc/hosts" <<EOF
127.0.0.1 localhost localhost.localdomain
127.0.1.1 linuxvm
::1 localhost
EOF

# Serial console autologin: the app talks to ttyS0. agetty --autologin drops
# straight into a root shell — "terminal as good as termux" means NO login wall.
cat > "$ROOTFS/etc/inittab" <<'EOF'
# /etc/inittab — Linux VM (QEMU serial console edition)
::sysinit:/sbin/openrc sysinit
::sysinit:/sbin/openrc boot
::wait:/sbin/openrc default

# Log anything to ttyS0 so it is visible in the app terminal
ttyS0::respawn:/sbin/agetty --autologin root -L 115200 ttyS0 xterm-256color
tty1::respawn:/sbin/getty -L 38400 tty1

# What to do at the "3 Finger Salute"
::ctrlaltdel:/sbin/reboot

# Stuff to do before rebooting
::shutdown:/sbin/openrc shutdown
EOF

# login -f root requires the serial port to be secure
grep -q '^ttyS0$' "$ROOTFS/etc/securetty" 2>/dev/null || echo "ttyS0" >> "$ROOTFS/etc/securetty"

# Networking: DHCP over QEMU slirp
mkdir -p "$ROOTFS/etc/network"
cat > "$ROOTFS/etc/network/interfaces" <<'EOF'
auto lo
iface lo inet loopback

auto eth0
iface eth0 inet dhcp
EOF

# slirp's DHCP hands out 10.0.2.3 as DNS — a forwarder that reads the HOST's
# /etc/resolv.conf, which does not exist on Android. Pin real resolvers at boot.
mkdir -p "$ROOTFS/etc/local.d"
cat > "$ROOTFS/etc/local.d/resolv.start" <<'EOF'
#!/bin/sh
printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > /etc/resolv.conf
EOF
chmod +x "$ROOTFS/etc/local.d/resolv.start"

# In-guest apk repositories (main + community) so users can install anything.
cat > "$ROOTFS/etc/apk/repositories" <<EOF
$REPO_MAIN
$REPO_COMMUNITY
EOF

# Shell environment: colored prompt, Go paths, nano as editor.
cat > "$ROOTFS/etc/profile.d/00-linuxvm.sh" <<'EOF'
# Linux VM environment
export GOPATH="$HOME/go"
export PATH="$PATH:/root/go/bin:/root/.opencode/bin"
export EDITOR=nano
export PAGER=less
export TERM="${TERM:-xterm-256color}"

# BusyBox ash understands \e \u \w \$ (but NOT readline's \[ \]).
export PS1='\e[1;32m\u@linuxvm\e[0m:\e[1;34m\w\e[0m# '
EOF

# Welcome banner shown at every shell start.
cat > "$ROOTFS/etc/motd" <<'EOF'

  ┌─────────────────────────────────────────────────────────┐
  │  Linux VM — Alpine • Node.js • Go • π agent • opencode  │
  └─────────────────────────────────────────────────────────┘

  node -v            Node.js version        (npm install -g <pkg>)
  go version         Go toolchain           (GOPATH=/root/go)
  pi                 π coding agent         (export ANTHROPIC_API_KEY=…)
  opencode           opencode agent         (run: opencode)
  apk add <pkg>      install any Alpine package (main + community)

  Files persist: this is a real disk, not a RAM disk.
EOF

# Boot services
chroot "$ROOTFS" /bin/sh -es <<'EOS'
set -eu
rc-update add devfs sysinit
rc-update add dmesg sysinit
rc-update add mdev sysinit
rc-update add hwclock boot || true
rc-update add modules boot
rc-update add sysctl boot || true
rc-update add hostname boot
rc-update add bootmisc boot
rc-update add syslog boot
rc-update add networking boot
rc-update add local default
rc-update add sshd default || true
rc-update add mount-ro shutdown
rc-update add killprocs shutdown
rc-update add savecache shutdown
EOS

# Empty root password: autologin on the serial console owns the gate.
sed -i 's|^root:[^:]*:|root::|' "$ROOTFS/etc/shadow"

# ── 4. Version-matched kernel + initramfs ───────────────────────────────────
log "building the guest initramfs (virtio + ext4 guaranteed)"
KVER="$(ls "$ROOTFS/lib/modules" | head -n1)"
echo "kernel: $KVER"
cat > "$ROOTFS/tmp/mkinitfs-vm.conf" <<'EOF'
features="ata base ext4 mmc raid scsi usb virtio"
EOF
chroot "$ROOTFS" mkinitfs -c /tmp/mkinitfs-vm.conf -o /boot/initramfs-vm "$KVER"
rm -f "$ROOTFS/tmp/mkinitfs-vm.conf"

cp "$ROOTFS/boot/vmlinuz-lts" "$OUT/linux-vm-vmlinuz-lts"
cp "$ROOTFS/boot/initramfs-vm" "$OUT/linux-vm-initramfs-lts"

# ── 5. Manifest ─────────────────────────────────────────────────────────────
log "recording the toolchain manifest"
{
  echo "Linux VM prebuilt distro"
  echo "built:      $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "alpine:     3.22"
  echo "kernel:     $KVER"
  echo "image size: $IMG_SIZE (ext4, persistent /dev/vda)"
  chroot "$ROOTFS" /bin/sh -c '
    echo "node:       $(node -v 2>/dev/null || echo missing)"
    echo "npm:        $(npm -v 2>/dev/null || echo missing)"
    echo "go:         $(go version 2>/dev/null || echo missing)"
    echo "pi:         $(pi --version 2>/dev/null | head -1 || echo missing)"
    echo "opencode:   $(opencode --version 2>/dev/null | head -1 || echo missing)"
  '
} > "$OUT/linux-vm-distro-manifest.txt"
cat "$OUT/linux-vm-distro-manifest.txt"

# ── 6. Cleanup + pack ───────────────────────────────────────────────────────
log "cleaning caches"
rm -rf "$ROOTFS/var/cache/apk/"* "$ROOTFS/root/.npm" "$ROOTFS/root/.cache" \
       "$ROOTFS/tmp/"* "$ROOTFS/var/tmp/"* "$ROOTFS/root/.opencode/cache" 2>/dev/null || true

log "creating the ext4 image ($IMG_SIZE)"
# -d populates the filesystem from the rootfs tree; the file is created sparse.
mkfs.ext4 -F -q -L linuxvm -b 4096 -d "$ROOTFS" "$OUT/rootfs.img" "$IMG_SIZE"

log "compressing (gzip)"
gzip -6 "$OUT/rootfs.img"
mv "$OUT/rootfs.img.gz" "$OUT/linux-vm-rootfs.img.gz"

log "checksum"
(cd "$OUT" && sha256sum linux-vm-rootfs.img.gz > linux-vm-rootfs.img.gz.sha256)

log "done:"
ls -la "$OUT"
