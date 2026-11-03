#!/usr/bin/env bash
# Runs PREINSTALLED matching debug/test APKs. Never builds, installs, downloads or publishes APKs.
set -euo pipefail
cd "$(dirname "$0")/.."
assets="test-assets/benchmarks/media"
seconds=1800
tunnel=false
preload=false
baseline_args=()
adb_args=()
while (($#)); do
  case "$1" in
    --baseline) baseline_args=(--baseline "${2:?Baseline CSV required}"); test -f "$2" || { echo "Baseline CSV not found" >&2; exit 2; }; shift 2 ;;
    --device) adb_args=(-s "${2:?Device serial required}"); shift 2 ;;
    --references) assets="${2:?Directory required}"; shift 2 ;;
    --quick) seconds=30; shift ;;
    --tunnel) tunnel=true; shift ;;
    --preload) preload=true; shift ;;
    *) echo "Usage: $0 [--device SERIAL] [--references DIR] [--quick] [--tunnel] [--preload] [--baseline CSV]" >&2; exit 2 ;;
  esac
done
command -v adb >/dev/null || { echo "adb is required; no APK was built." >&2; exit 2; }
python3 scripts/generate-benchmark-fixtures.py --verify-only --directory "$assets"
adb "${adb_args[@]}" get-state >/dev/null
package=com.tplayer.mediaplayer
runner="$package.test/androidx.test.runner.AndroidJUnitRunner"
class="$package.PlaybackBenchmarkTest"
revision=$(git rev-parse HEAD)
out="benchmark-results/$(date -u +%Y%m%dT%H%M%SZ)-${revision:0:8}"
mkdir -p "$out"
remote="/sdcard/Android/data/$package/files/benchmarks"
# Only the explicit manual benchmark methods run, not the ordinary test suite.
adb "${adb_args[@]}" shell am instrument -w -r -e benchmark_prepare true -e class "$class#prepareExternalAssetDirectory" "$runner" | tee "$out/prepare.txt"
grep -Eq 'OK \([0-9]+ test' "$out/prepare.txt" || { echo "Install matching debug + androidTest APKs after the build deferral ends." >&2; exit 1; }
mapfile -t names < <(python3 - "$assets/manifest.json" <<'PY'
import json, re, sys
for ref in json.load(open(sys.argv[1]))["references"]:
    name = ref["name"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", name):
        raise ValueError("Unsafe reference name")
    print(name)
PY
)
for name in "${names[@]}" manifest.json; do adb "${adb_args[@]}" push "$assets/$name" "$remote/input/$name"; done
cp "$assets/manifest.json" "$out/manifest.json"
ffmpeg -version > "$out/host-ffmpeg.txt"
adb "${adb_args[@]}" shell rm -f "$remote/results.csv"
collect() {
  status=$?
  trap - EXIT
  if adb "${adb_args[@]}" pull "$remote/results.csv" "$out/results.csv"; then
    if ! python3 scripts/benchmark_report.py "$out/results.csv" --output docs/BENCHMARKS.md "${baseline_args[@]}"; then status=1; fi
  else status=1; fi
  echo "Benchmark files: $out; summary: docs/BENCHMARKS.md"
  exit "$status"
}
trap collect EXIT
adb "${adb_args[@]}" shell am instrument -w -r -e benchmark true -e expected_revision "$revision" \
  -e long_session_seconds "$seconds" -e tunnel "$tunnel" -e preload "$preload" \
  -e class "$class#measureReferencesAndBackgroundRelease" "$runner" | tee "$out/instrumentation.txt"
grep -Eq 'OK \([0-9]+ test' "$out/instrumentation.txt"
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' "$out/instrumentation.txt"
