#!/usr/bin/env bash
set -euo pipefail
VERSION="0.3.13"
SHA="c6c2787e5dd888a8f9193501506dfa3a4f8a38fbd51adefccf67d84d0c8e0fb5"
DEST="app/libs/libmihomo-android-v${VERSION}.aar"
URL="https://github.com/oviron/libmihomo-android/releases/download/v${VERSION}/libmihomo-android-v${VERSION}.aar"
mkdir -p app/libs
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
curl -fL --retry 3 --connect-timeout 15 "$URL" -o "$TMP"
ACTUAL="$(sha256sum "$TMP" | cut -d ' ' -f 1)"
if [ "$ACTUAL" != "$SHA" ]; then
  echo "Mihomo AAR checksum mismatch: $ACTUAL" >&2
  exit 1
fi
mv "$TMP" "$DEST"
echo "Verified Mihomo AAR: $DEST"
