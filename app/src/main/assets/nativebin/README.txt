QEMU BINARIES FOR ANDROID
=========================

These binaries are from Termux (https://termux.dev) and are compiled for
Android's bionic libc. They require Termux's shared libraries to run.

For a self-contained Android app, you need to:
1. Build QEMU with static linking using Android NDK
2. OR bundle all required .so libraries and set LD_LIBRARY_PATH

Termux binaries source:
https://github.com/termux/termux-packages/tree/master/packages/qemu-system-x86-64

Required shared libraries (for Termux binaries):
- libglib-2.0.so, libz.so, libpng16.so, libpixman-1.so
- libnettle.so, libgnutls.so, libcurl.so, libssh.so
- libusb-1.0.so, libslirp.so, libdtc.so, libbz2.so
- liblzma.so, libzstd.so, libspice-server.so, libncurses.so
- libgmp.so, libhogweed.so, libtasn1.so, libp11-kit.so
- libffi.so, libintl.so, libiconv.so, libdw.so
- libelf.so, libcap.so, libattr.so, libresolv.so

For production use, compile static binaries:
1. Install Android NDK
2. ./configure --static --target-list=aarch64-softmmu,x86_64-softmmu
3. make

Then replace these binaries with your static builds.
