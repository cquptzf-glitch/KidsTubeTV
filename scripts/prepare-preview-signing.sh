#!/usr/bin/env bash
# Private stable preview key when available; otherwise isolate this beta by package.
set -euo pipefail
: "$GITHUB_ENV"
: "$RUNNER_TEMP"
: "$KIDSTUBE_PREVIEW_FALLBACK_SUFFIX"
keys=(KIDSTUBE_PREVIEW_KEYSTORE_BASE64 KIDSTUBE_PREVIEW_STORE_PASSWORD KIDSTUBE_PREVIEW_KEY_ALIAS KIDSTUBE_PREVIEW_KEY_PASSWORD)
count=0
for name in "${keys[@]}"; do
  value="$(printenv "$name" || true)"
  if [[ -n "$value" ]]; then count=$((count + 1)); fi
done
if [[ "$count" -gt 0 && "$count" -lt 4 ]]; then
  echo "::error::All four private preview signing secrets must be configured together." >&2
  exit 1
fi
if [[ "$count" -eq 4 ]]; then
  keypath="$RUNNER_TEMP/kidstubetv-preview.jks"
  (umask 077; printf '%s' "$KIDSTUBE_PREVIEW_KEYSTORE_BASE64" | base64 --decode > "$keypath")
  keytool -list -keystore "$keypath" -storepass "$KIDSTUBE_PREVIEW_STORE_PASSWORD" -alias "$KIDSTUBE_PREVIEW_KEY_ALIAS" >/dev/null 2>&1 || {
    echo "::error::Unable to open the private preview signing key." >&2
    exit 1
  }
  echo "KIDSTUBE_PREVIEW_KEYSTORE_FILE=$keypath" >> "$GITHUB_ENV"
  echo "KIDSTUBE_PREVIEW_PACKAGE_SUFFIX=.preview" >> "$GITHUB_ENV"
  echo "KIDSTUBE_PREVIEW_SIGNING_MODE=stable-private-secret" >> "$GITHUB_ENV"
  echo "Using a stable PRIVATE preview signing key. Not a production key."
else
  if [[ ! "$KIDSTUBE_PREVIEW_FALLBACK_SUFFIX" =~ ^\.preview\.b[0-9]+$ ]]; then
    echo "::error::Fallback preview suffix must be scoped to the specific beta (.preview.bN)." >&2
    exit 1
  fi
  echo "KIDSTUBE_PREVIEW_PACKAGE_SUFFIX=$KIDSTUBE_PREVIEW_FALLBACK_SUFFIX" >> "$GITHUB_ENV"
  echo "KIDSTUBE_PREVIEW_SIGNING_MODE=ephemeral-debug-isolated-package" >> "$GITHUB_ENV"
  echo "::notice::Private preview key absent. The default Android Debug signer will sign this version-scoped package to avoid clashing with old installed betas. This does not enable in-place upgrades."
fi
