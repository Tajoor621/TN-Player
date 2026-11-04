#!/usr/bin/env bash
# Uses already installed app and test APKs. Run only on a dedicated API 29+ device.
set -euo pipefail
out=${1:-app/build/reports/library-benchmark}
mkdir -p "$out"
package=com.tplayer.mediaplayer
report="/sdcard/Android/data/$package/files/benchmarks/library.csv"
adb shell rm -f "$report"
adb shell am instrument -w -r -e class "$package.LibraryDeviceTest#measureFiveThousandFileScanWhileMainThreadKeepsRunning" \
  -e library_benchmark true "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$out/instrumentation.txt"
adb pull "$report" "$out/library.csv"
grep -Eq '^OK \(1 test\)' "$out/instrumentation.txt"
