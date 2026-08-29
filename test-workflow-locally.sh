#!/bin/bash
# Test script to verify GitHub Actions workflow steps locally

set -e

echo "=== Testing QEMU Download Steps ==="

export QEMU_VERSION="10.1.2.2"
export QEMU_BASE_URL="https://github.com/ziglang/qemu-static/releases/download"

mkdir -p qemu-downloads
cd qemu-downloads

echo "Downloading QEMU static binaries..."

# Try multiple versions until one succeeds
wget -q "${QEMU_BASE_URL}/${QEMU_VERSION}/qemu-linux-x86_64-${QEMU_VERSION}.tar.xz" || \
wget -q "${QEMU_BASE_URL}/10.1.2.1/qemu-linux-x86_64-10.1.2.1.tar.xz" || \
wget -q "${QEMU_BASE_URL}/10.1.1.1/qemu-linux-x86_64-10.1.1.1.tar.xz" || \
wget -q "${QEMU_BASE_URL}/10.1.1/qemu-linux-x86_64-10.1.1.tar.xz" || \
wget -q "${QEMU_BASE_URL}/10.1.0/qemu-linux-x86_64-10.1.0.tar.xz" || \
echo "QEMU static binaries not available, will use simulation mode"

# List downloaded files
ls -la *.tar.xz 2>/dev/null || echo "No tarball found"

# Verify download succeeded
if [ -f "qemu-linux-x86_64-${QEMU_VERSION}.tar.xz" ]; then
  echo "✅ Successfully downloaded QEMU ${QEMU_VERSION}"
elif [ -f "qemu-linux-x86_64-10.1.2.1.tar.xz" ]; then
  echo "✅ Successfully downloaded QEMU 10.1.2.1"
elif [ -f "qemu-linux-x86_64-10.1.1.1.tar.xz" ]; then
  echo "✅ Successfully downloaded QEMU 10.1.1.1"
elif [ -f "qemu-linux-x86_64-10.1.1.tar.xz" ]; then
  echo "✅ Successfully downloaded QEMU 10.1.1"
elif [ -f "qemu-linux-x86_64-10.1.0.tar.xz" ]; then
  echo "✅ Successfully downloaded QEMU 10.1.0"
else
  echo "❌ Failed to download any QEMU version"
  exit 1
fi

cd ..

echo ""
echo "=== Testing Extraction ==="

mkdir -p qemu-binaries
cd qemu-downloads

# Find and extract the tarball
for file in *.tar.xz; do
  if [ -f "$file" ]; then
    echo "Extracting $file..."
    tar -xJf "$file" -C ../qemu-binaries --strip-components=1 2>/dev/null || \
    echo "Failed to extract $file"
    break
  fi
done

echo "Extracted binaries:"
ls -la ../qemu-binaries/bin/ 2>/dev/null | head -20 || echo "qemu-binaries directory is empty"

cd ..

echo ""
echo "=== Testing Asset Preparation ==="

mkdir -p app/src/main/assets/qemu_binaries

if [ -d "qemu-binaries/bin" ]; then
  # Copy qemu-system-* binaries
  cp qemu-binaries/bin/qemu-system-x86_64 app/src/main/assets/qemu_binaries/ 2>/dev/null && \
    echo "✅ Copied qemu-system-x86_64" || echo "⚠️ qemu-system-x86_64 not found"
  
  cp qemu-binaries/bin/qemu-system-aarch64 app/src/main/assets/qemu_binaries/ 2>/dev/null && \
    echo "✅ Copied qemu-system-aarch64" || echo "⚠️ qemu-system-aarch64 not found"
  
  cp qemu-binaries/bin/qemu-system-riscv64 app/src/main/assets/qemu_binaries/ 2>/dev/null && \
    echo "✅ Copied qemu-system-riscv64" || echo "⚠️ qemu-system-riscv64 not found"
  
  cp qemu-binaries/bin/qemu-system-i386 app/src/main/assets/qemu_binaries/ 2>/dev/null && \
    echo "✅ Copied qemu-system-i386" || echo "⚠️ qemu-system-i386 not found"
  
  # Also copy essential BIOS/firmware files from share/qemu
  if [ -d "qemu-binaries/share/qemu" ]; then
    mkdir -p app/src/main/assets/qemu_binaries/pc-bios
    cp qemu-binaries/share/qemu/*.bin app/src/main/assets/qemu_binaries/pc-bios/ 2>/dev/null || true
    cp qemu-binaries/share/qemu/*.rom app/src/main/assets/qemu_binaries/pc-bios/ 2>/dev/null || true
    cp qemu-binaries/share/qemu/*.img app/src/main/assets/qemu_binaries/pc-bios/ 2>/dev/null || true
    echo "✅ Copied PC-BIOS files"
  fi
  
  # Make all binaries executable
  chmod +x app/src/main/assets/qemu_binaries/qemu-system-* 2>/dev/null || true
  
  echo ""
  echo "Bundled QEMU binaries:"
  ls -lh app/src/main/assets/qemu_binaries/ || echo "No binaries bundled"
else
  echo "❌ qemu-binaries/bin directory not found"
  exit 1
fi

echo ""
echo "=== All tests passed! ==="
