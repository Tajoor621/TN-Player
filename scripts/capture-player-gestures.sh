#!/usr/bin/env bash
# Manual evidence collection after APK/device validation is authorized. Never builds or installs APKs.
set -euo pipefail
cd "$(dirname "$0")/.."
adb_args=()
if (($#)); then
  [[ "$1" == --device && $# == 2 ]] || { echo 'Usage: capture-player-gestures.sh [--device SERIAL]' >&2; exit 2; }
  adb_args=(-s "$2")
fi
command -v adb >/dev/null || { echo 'adb and preinstalled matching debug/test APKs are required; no APK was built.' >&2; exit 2; }
adb "${adb_args[@]}" get-state >/dev/null
revision=$(git rev-parse HEAD)
out="gesture-evidence/$(date -u +%Y%m%dT%H%M%SZ)-${revision:0:8}"
mkdir -p "$out"
collect() {
  status=$?
  trap - EXIT
  adb "${adb_args[@]}" pull /sdcard/Android/data/com.tplayer.mediaplayer/files/gesture-evidence "$out/" || status=1
  echo "Gesture screenshots and instrumentation output: $out"
  exit "$status"
}
trap collect EXIT
adb "${adb_args[@]}" shell am instrument -w -r -e capture_gestures true -e expected_revision "$revision" \
  -e class com.tplayer.mediaplayer.GestureDeviceTest#captureGestureGalleryOnAPreinstalledBuild \
  com.tplayer.mediaplayer.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
grep -Eq 'OK \([0-9]+ test' "$out/instrumentation.txt"
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "$out/instrumentation.txt"
