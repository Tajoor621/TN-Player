#!/usr/bin/env bash
set -euo pipefail
runner=com.tplayer.mediaplayer.test/androidx.test.runner.AndroidJUnitRunner
run_check() {
  local output
  output=$(adb shell am instrument -w -r "$@" "$runner")
  printf '%s\n' "$output"
  [[ "$output" == *'OK ('* && "$output" != *'FAILURES!!!'* ]]
}
run_check -e prepare true -e class com.tplayer.mediaplayer.PrepareRecoveryTest
adb shell am force-stop com.tplayer.mediaplayer
run_check -e coldRestore true -e class com.tplayer.mediaplayer.ColdRestoreTest
