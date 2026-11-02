#!/usr/bin/env bash
# Install a temporary CI-signed optimized release; never publish its testing signature.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
bash ./gradlew assembleRelease
signer="$ANDROID_HOME/build-tools/34.0.0/apksigner"
"$signer" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out app/build/outputs/apk/release/ci-codec-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
adb install -r -t app/build/outputs/apk/release/ci-codec-release.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
output=$(adb shell am instrument -w -r -e releaseCodec true -e class com.tplayer.mediaplayer.ReleaseCodecSmokeTest \
  com.tplayer.mediaplayer.test/androidx.test.runner.AndroidJUnitRunner)
printf '%s\n' "$output"
[[ "$output" == *'OK ('* && "$output" != *'FAILURES!!!'* ]]
