#!/usr/bin/env bash
# Builds the native engines UC Plus ships in app/src/main/jniLibs (sing-box, xray, hev, and the SSTP bridge):
#   libsingbox.so          sing-box extended core, built from source with the Lumen patches
#   libxray.so             Xray-core; arm64/x86_64 are the official verified releases, armeabi-v7a is built here
#   libhev-socks5-tunnel.so  hev-socks5-tunnel, built from source
# Needs: git, go (1.27.x), python3, Android NDK (ANDROID_NDK_HOME or ANDROID_NDK_ROOT).
# Files that already exist are kept, so a cache of jniLibs skips the rebuild.
set -euo pipefail

ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI="$ANDROID_DIR/app/src/main/jniLibs"
WORK="${NATIVE_WORK_DIR:-$ANDROID_DIR/.native-build}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
[ -n "$NDK" ] && [ -d "$NDK" ] || { echo "Set ANDROID_NDK_HOME to an NDK r28 directory" >&2; exit 1; }
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$WORK"

SINGBOX_REF="v1.14.3-extended-2.7.3"
XRAY_REF="v26.10.31"
HEV_REF="180cda8b304b71b9d9ef8ea93aeb0e4e00e15f7d"

# abi GOARCH GOARM clang-triple-for-api-23 clang-triple-for-api-26
targets=(
  "arm64-v8a arm64 '' aarch64-linux-android23 aarch64-linux-android26"
  "armeabi-v7a arm 7 armv7a-linux-androideabi23 armv7a-linux-androideabi26"
  "x86_64 amd64 '' x86_64-linux-android23 x86_64-linux-android26"
)

have() { [ -s "$JNI/$1/$2" ]; }

# ---------- sing-box extended ----------
need_singbox=0
for t in "${targets[@]}"; do read -r abi _ <<<"$t"; have "$abi" libsingbox.so || need_singbox=1; done
if [ "$need_singbox" = 1 ]; then
  src="$WORK/sing-box-extended"
  rm -rf "$src"
  git clone --filter=blob:none --depth 1 --branch "$SINGBOX_REF" https://github.com/shtorm-7/sing-box-extended.git "$src"
  for patch in sing-box-extended-direct-masque.patch sing-box-extended-openvpn-readiness.patch; do
    git -C "$src" apply --unidiff-zero --check "$ANDROID_DIR/patches/$patch"
    git -C "$src" apply --unidiff-zero "$ANDROID_DIR/patches/$patch"
  done
  # The same feature set the Lumen extended CLI binaries already ship with.
  tags="with_gvisor,with_quic,with_dhcp,with_wireguard,with_utls,with_acme,with_clash_api,with_tailscale,with_masque,with_openvpn,with_mtproxy,with_trusttunnel,with_call,with_sudoku,with_manager,with_profiler,badlinkname,tfogo_checklinkname0"
  shared_ldflags="$(tr -d '\r' < "$src/release/LDFLAGS" | sed 's/^ *//;s/ *$//')"
  ldflags="-X github.com/sagernet/sing-box/constant.Version=${SINGBOX_REF#v}-lumen.2 $shared_ldflags -s -w -buildid="
  for t in "${targets[@]}"; do
    read -r abi goarch goarm _ api <<<"$t"; goarm="${goarm//\'/}"
    have "$abi" libsingbox.so && continue
    mkdir -p "$JNI/$abi"
    ( cd "$src" && \
      CGO_ENABLED=1 GOEXPERIMENT=nodwarf5 GOOS=android GOARCH="$goarch" GOARM="$goarm" \
      CC="$TOOLCHAIN/${api}-clang" CXX="$TOOLCHAIN/${api}-clang++" \
      go build -trimpath -tags "$tags" -ldflags "$ldflags" -o "$JNI/$abi/libsingbox.so" ./cmd/sing-box )
    echo "built libsingbox.so for $abi"
  done
fi

# ---------- Xray ----------
if ! have arm64-v8a libxray.so || ! have x86_64 libxray.so; then
  python3 "$ANDROID_DIR/tools/fetch_xray.py"
fi
if ! have armeabi-v7a libxray.so; then
  src="$WORK/xray-core"
  rm -rf "$src"
  git clone --depth 1 --branch "$XRAY_REF" https://github.com/XTLS/Xray-core.git "$src"
  mkdir -p "$JNI/armeabi-v7a"
  ( cd "$src" && \
    CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=7 GOMAXPROCS=2 \
    CC="$TOOLCHAIN/armv7a-linux-androideabi26-clang" \
    go build -p 2 -trimpath -buildmode=pie -ldflags="-s -w -buildid= -checklinkname=0" \
      -o "$JNI/armeabi-v7a/libxray.so" ./main )
  echo "built libxray.so for armeabi-v7a"
fi

# ---------- hev-socks5-tunnel ----------
need_hev=0
for t in "${targets[@]}"; do read -r abi _ <<<"$t"; have "$abi" libhev-socks5-tunnel.so || need_hev=1; done
if [ "$need_hev" = 1 ]; then
  src="$WORK/hev-socks5-tunnel"
  if [ ! -d "$src/.git" ]; then
    rm -rf "$src"
    git clone https://github.com/heiher/hev-socks5-tunnel.git "$src"
  fi
  git -C "$src" checkout --quiet "$HEV_REF"
  git -C "$src" submodule update --init --recursive --depth 1 >/dev/null 2>&1 || true
  "$NDK/ndk-build" -C "$src" NDK_PROJECT_PATH="$src" APP_BUILD_SCRIPT="$src/Android.mk" \
    APP_ABI="armeabi-v7a arm64-v8a x86_64" APP_PLATFORM=android-26 \
    APP_CFLAGS="-O3 -DPKGNAME=org/amnezia/awg/hevtunnel -DCLSNAME=TProxyService" \
    APP_LDFLAGS="-Wl,-z,max-page-size=16384" -j"$(nproc)"
  for abi in armeabi-v7a arm64-v8a x86_64; do
    mkdir -p "$JNI/$abi"
    cp "$src/libs/$abi/libhev-socks5-tunnel.so" "$JNI/$abi/libhev-socks5-tunnel.so"
  done
  echo "built libhev-socks5-tunnel.so"
fi

# ---------- SSTP bridge (pure Go executable, no NDK needed) ----------
# The bridge is a plain static binary run as a process, like sing-box and xray.
need_sstp=0
for t in "${targets[@]}"; do read -r abi _ <<<"$t"; have "$abi" libsstpbridge.so || need_sstp=1; done
if [ "$need_sstp" = 1 ]; then
  bridge="$ANDROID_DIR/native/sstp-bridge"
  # armv7 Android requires cgo external linking, so every ABI uses the NDK clang
  # exactly like the sing-box and xray builds above.
  build_sstp() {
    local abi=$1 goarch=$2 goarm=$3 clang=$4
    mkdir -p "$JNI/$abi"
    (cd "$bridge" && go mod tidy && CGO_ENABLED=1 GOOS=android GOARCH="$goarch" GOARM="$goarm" \
      CC="$TOOLCHAIN/$clang" go build -trimpath -ldflags="-s -w" -o "$JNI/$abi/libsstpbridge.so" .)
  }
  build_sstp arm64-v8a arm64 "" aarch64-linux-android26-clang
  build_sstp armeabi-v7a arm 7 armv7a-linux-androideabi26-clang
  build_sstp x86_64 amd64 "" x86_64-linux-android26-clang
  echo "built libsstpbridge.so"
fi

for abi in armeabi-v7a arm64-v8a x86_64; do
  for lib in libsingbox.so libxray.so libhev-socks5-tunnel.so libsstpbridge.so; do
    have "$abi" "$lib" || { echo "missing $abi/$lib after build" >&2; exit 1; }
  done
done
echo "All native engines are in $JNI"
