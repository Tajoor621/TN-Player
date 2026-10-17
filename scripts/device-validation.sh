#!/usr/bin/env bash
set -u
test_status=0
bash ./gradlew connectedDebugAndroidTest || test_status=$?
mkdir -p app/build/reports/reference-ui
image_status=0
adb pull /sdcard/Download/tn-player-validation app/build/reports/reference-ui || image_status=$?
if [ "$test_status" -ne 0 ]; then
  exit "$test_status"
fi
if [ "$image_status" -ne 0 ]; then
  exit "$image_status"
fi
bash ./scripts/process-recovery-check.sh
