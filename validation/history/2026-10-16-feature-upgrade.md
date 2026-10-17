# TN-Player feature upgrade — 16 October 2026

## Verified source and checks

Android source/build commit: `ed0eaef7582240cad9c61930f24a3145bcf82c37`.

- [Final branding branch checks](https://github.com/Tajoor621/TN-Player/actions/runs/38949397261): build and emulator jobs succeeded before merging.
- [Main Android checks](https://github.com/Tajoor621/TN-Player/actions/runs/38950067907): unit regressions, lint, debug/release assembly and Android 14 emulator validation succeeded.
- The device suite covers Settings/Help navigation, foreground/background audio, video/PiP, Keystore, verified file moves, multi-share playback queues, widget transport and ZIP round trips with unsafe-path rejection.
- Preparation and cold-restoration tests are intentionally skipped in the ordinary suite. The external process-recovery script reinstalls test APKs, runs preparation, force-stops the app and verifies cold restoration; both explicit cases reported OK (1 test).
- New unit regressions cover JSON backup validation/merge and playlist-name collisions, settings persistence, bounded ZIP extraction, unsafe paths, collisions and directory payload rejection.
- [Pages publish and public verification](https://github.com/Tajoor621/TN-Player/actions/runs/38950067891): publishes only after the matching Android checks succeed. The verification downloads the entire public APK and matches its source commit, byte count and SHA-256.

## Supplied logo

The user-provided original JPEG is preserved unchanged in branding/TN-Player-logo-original.jpeg. A square launcher adaptation removes portrait whitespace and the small Player wordmark. Identical PNG bytes are used in branding, Android and Pages. Public icon SHA-256 matches the repository: `554d804dd9f54ed66e56fd15b305dd98411b02ffef3e1bd3b626c005e4cefb2c`.

## Published testing APK

- [Download](https://tajoor621.github.io/TN-Player/downloads/TN-Player-1.0.0-debug.apk)
- Version: 1.0.0 / code 1; package com.tplayer.mediaplayer
- Bytes: 12,167,604
- SHA-256: `00c9e4047593de904fddf0a29ffed60fdeaa4adc9abde9c66dc0f3e7e652ff65`

## Scope and remaining validation

This verifies the feature upgrade on the Android 14 CI emulator. Physical-device gesture, widget-launcher, audio-effects, removable-provider, accessibility, codec, performance and battery certification remain outstanding. The master specification is not complete; FEATURE_STATUS.md identifies remaining modules, including native universal decoding, network shares/Cast, cloud clients and encrypted vault/app lock.

Debug signing may differ from previously installed testing APKs; preserve needed data before uninstalling an existing build. No private signing key is committed. Source commit identifies the app binary; generated-APK and documentation commits do not imply a different app build. Earlier evidence remains in validation/history/ and Git history.
