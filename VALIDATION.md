# TN-Player workflow repair — 16 October 2026

## Verified results

Android source/build commit: `d294386f67cff75651a8405c9609a055b6174935`.

- [Android checks](https://github.com/Tajoor621/TN-Player/actions/runs/38932507526): build and emulator jobs both succeeded. Debug/release assembly, unit regressions and lint passed.
- Device coverage passed for Settings/Help navigation, background audio and pause screen-awake behavior, video/PiP playback, Android Keystore and verified local file moves.
- The parameterized preparation/cold-restoration cases are intentionally skipped in the ordinary suite. The external recovery script then ran each case explicitly: preparation **OK (1 test)**, followed by force-stop, then cold restoration **OK (1 test)**.
- [Pages publishing and verification](https://github.com/Tajoor621/TN-Player/actions/runs/38932507472): package and public verification jobs succeeded.
- Public verification downloaded the complete APK, checked its byte count and SHA-256 against build-info.json, and confirmed the source commit.

## Repairs

1. Prevent Settings switches from measuring null on/off text. The navigation regression now exercises view measurement.
2. Reinstall app/test APKs after Gradle's device-test cleanup before running external process-recovery instrumentation.
3. Publish the generated APK, checksum and build manifest into the configured main/root Pages source. Request a branch Pages build, avoiding a separate artifact deployment being overwritten by the branch publisher.
4. Fail the publishing workflow when the public site is stale or the APK is missing/mismatched. Preserve device test reports even on failure.

## Published testing APK

- [Download](https://tajoor621.github.io/TN-Player/downloads/TN-Player-1.0.0-debug.apk)
- Version: 1.0.0 / code 1; package com.tplayer.mediaplayer
- Bytes: 11,822,087
- SHA-256: `41648b22a6fa93df9fe022c81d4275f2a148d411ab51cb305c55c19949f3e5ec`

## Scope

This verifies the repair on the Android 14 CI emulator; it is not physical-device, performance, battery or universal-codec certification. Unfinished master-spec features remain in FEATURE_STATUS.md. Earlier reports are preserved under validation/history/. Debug signing can differ from earlier testing APKs; preserve needed data before uninstalling any existing build. No private signing key is committed.

The source-commit field identifies the app build. Later generated-APK or documentation commits do not imply a different app binary.
