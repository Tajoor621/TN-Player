#!/usr/bin/env bash
set -euo pipefail
runner=com.tplayer.mediaplayer.test/androidx.test.runner.AndroidJUnitRunner
# connectedDebugAndroidTest cleans up its installed APKs. The separate
# force-stop check needs both packages installed again, from this same build.
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
adb install -r -t "$project_root/app/build/outputs/apk/debug/app-debug.apk"
adb install -r -t "$project_root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
run_check() {
  local output
  output=$(adb shell am instrument -w -r "$@" "$runner")
  printf '%s\n' "$output"
  [[ "$output" == *'OK ('* && "$output" != *'FAILURES!!!'* ]]
}
run_check -e prepare true -e class com.tplayer.mediaplayer.PrepareRecoveryTest
adb shell am force-stop com.tplayer.mediaplayer
run_check -e coldRestore true -e class com.tplayer.mediaplayer.ColdRestoreTest
