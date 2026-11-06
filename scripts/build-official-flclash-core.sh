#!/usr/bin/env bash
# Build FlClash's OWN Go mobile interface from the pinned GPLv3 source.
# Isolated from the running KidsTubeTV backend until the native adapter is ready.
set -euo pipefail
FLCLASH_TAG="${FLCLASH_TAG:-v0.9.3}"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/kidstubetv-flclash-source"
OUT="$ROOT/dist/flclash-core"
mkdir -p "$OUT"
rm -rf "$WORK"
git clone --depth 1 --branch "$FLCLASH_TAG" https://github.com/chen08209/FlClash.git "$WORK"
# FlClash's .gitmodules uses an SSH-only URL, but CI has no SSH key.
# Change only the disposable checkout (not the upstream source revision).
git -C "$WORK" submodule set-url core/Clash.Meta \
  https://github.com/chen08209/Clash.Meta.git
git -C "$WORK" -c url.https://github.com/.insteadOf=git@github.com: \
  submodule update --init --recursive core/Clash.Meta
GIT_SHA="$(git -C "$WORK" rev-parse HEAD)"
MIHOMO_SHA="$(git -C "$WORK/core/Clash.Meta" rev-parse HEAD)"
echo "FlClash source: $FLCLASH_TAG @ $GIT_SHA"
echo "FlClash Mihomo submodule: $MIHOMO_SHA"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${SDK_DIR:?Set ANDROID_HOME before running}"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_NDK_HOME="$SDK_DIR/ndk/$NDK_VERSION"
test -d "$ANDROID_NDK_HOME" || { echo "Missing Android NDK $NDK_VERSION" >&2; exit 1; }
mkdir -p "$WORK/buildtools"
cd "$WORK/core"
go version
go build -o "$WORK/buildtools/" golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
export PATH="$WORK/buildtools:$PATH"
gomobile bind \
  -target=android/arm,android/arm64 \
  -androidapi=28 \
  -javapkg=com.follow.clash.core \
  -tags=with_gvisor \
  -ldflags="-w -s -X=github.com/metacubex/mihomo/constant.Version=v1.19.33" \
  -o "$OUT/flclash-core-$FLCLASH_TAG.aar" \
  ./mobile
unzip -Z -1 "$OUT/flclash-core-$FLCLASH_TAG.aar" | grep -E '^(jni/.+\.so|classes.jar)$'
sha256sum "$OUT/flclash-core-$FLCLASH_TAG.aar" > "$OUT/SHA256SUMS.txt"
cp "$WORK/LICENSE" "$OUT/FlClash-LICENSE"
printf 'FlClash tag: %s\nFlClash commit: %s\nMihomo submodule commit: %s\nBuild target: Android API 28, armeabi-v7a + arm64-v8a\n' \
  "$FLCLASH_TAG" "$GIT_SHA" "$MIHOMO_SHA" > "$OUT/SOURCE-INFO.txt"
echo "Official FlClash Go core build artifact: $OUT"
