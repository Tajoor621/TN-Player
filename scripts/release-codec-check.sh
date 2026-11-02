#!/usr/bin/env bash
# Install a temporary CI-signed optimized release; never publish its testing signature.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
bash ./gradlew assembleRelease
signer="$ANDROID_HOME/build-tools/34.0.0/apksigner"
ci_key="${RUNNER_TEMP:?Run this script only on the disposable CI emulator}/tn-codec-test.jks"
keytool -genkeypair -keystore "$ci_key" -storepass android -keypass android -alias tn-codec-ci \
  -dname 'CN=TN-Player CI Codec Test' -keyalg RSA -keysize 2048 -validity 2 -noprompt
"$signer" sign --ks "$ci_key" --ks-pass pass:android --key-pass pass:android \
  --out app/build/outputs/apk/release/ci-codec-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
"$signer" sign --ks "$ci_key" --ks-pass pass:android --key-pass pass:android \
  --out app/build/outputs/apk/release/ci-codec-test.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Ordinary and cold-restore checks already passed. Only disposable emulator fixtures are removed.
adb uninstall com.tplayer.mediaplayer.test || true
adb uninstall com.tplayer.mediaplayer || true
adb install -t app/build/outputs/apk/release/ci-codec-release.apk
adb install -t app/build/outputs/apk/release/ci-codec-test.apk
output=$(adb shell am instrument -w -r -e releaseCodec true -e class com.tplayer.mediaplayer.ReleaseCodecSmokeTest \
  com.tplayer.mediaplayer.test/androidx.test.runner.AndroidJUnitRunner)
printf '%s\n' "$output"
[[ "$output" == *'OK ('* && "$output" != *'FAILURES!!!'* ]]
