# TN-Player folder browser and layout upgrade — 17 October 2026

## Verified source

App source/build commit: `703cbcc313b21ec61984bd8fb70d2daa3e922b5d`.

- [Branch validation](https://github.com/Tajoor621/TN-Player/actions/runs/38990520186): unit regressions, lint, debug/release assembly and Android 14 device tests succeeded. A lint-discovered Comparator.reversed API-24 dependency was replaced with API-21-compatible comparators.
- [Main Android validation](https://github.com/Tajoor621/TN-Player/actions/runs/38990844980): build and emulator jobs succeeded for the final app source, including the compact shortcut labels and visible first-run permission action.
- Unit coverage now includes same-named folders on different volumes, direct/descendant counts, hierarchy boundary checks, global numeric sorting across pages, hidden/minimum-duration filters, empty results, unknown frame-rate handling and layout preferences through backup/restore.
- The Android 14 suite includes a real MediaStore fixture: two indexed folders, accurate counts, opening the selected folder, preserving aggregate media URI identity, Cancel versus Done in the view dialog, grid persistence after recreation and the Me hub. Existing playback, PiP, navigation, Keystore and file-transfer tests remain included.
- The ordinary instrumentation suite reported 12 cases (10 executed, two process-recovery cases intentionally skipped there). The external script separately ran preparation, force-stop and cold restoration; both explicit recovery cases reported OK (1 test).
- [Pages build and public verification](https://github.com/Tajoor621/TN-Player/actions/runs/38990844995) waits for matching Android validation before publishing. Verification downloads the full public APK and matches the source commit, SHA-256 and byte count.

## UI evidence

[Native emulator screenshot gallery](validation/reference-ui/README.md) contains the final folder list, view dialog and Me hub. These are real 320 dp Android emulator captures, not mockups. Temporary media fixtures are removed after testing and are not shipped as the user's library.

The supplied TN logo is unchanged. The original JPEG remains in branding/TN-Player-logo-original.jpeg; Android and Pages use the square adaptation.

## Published testing APK

- [Download](https://tajoor621.github.io/TN-Player/downloads/TN-Player-1.0.0-debug.apk)
- Version: 1.0.0 / code 1; package com.tplayer.mediaplayer
- Bytes: 12,214,559
- SHA-256: `8575c01943c2faee1924951492ae51f4cb9e1d01fa62140fc31b910b0b162917`

## Scope and limits

Folder counts cover accessible indexed videos/audio for the active tab, after hidden and minimum-duration filters. Selected-video access on Android 14 exposes only the granted selection. Empty/unindexed/.nomedia and protected directories may be absent; Files → Choose folder handles document-provider folders. Metadata sorting scans on IO and keeps folder summaries and a page prefix; large-library throughput has not been benchmarked.

Capture fps is Android's indexed capture frame rate, not a guaranteed encoded frame rate. Missing values remain unknown. Playback status/last-played sorting uses the retained 200-item local history. Folder size/duration are sums; date and last-played are the newest member values.

The Me hub intentionally labels Private Folder, Local Network and Recycle Bin unavailable. Cloud Drive uses an installed Android document provider; it is not a built-in OAuth/sync client. File Transfer refers to the existing local file copy/move workflow. Full product gaps remain listed in FEATURE_STATUS.md.

Validation covers the Android 14 emulator and Robolectric tests; API 21 compatibility was lint-checked, not certified on Android 5 hardware. Physical Samsung/SD/USB/provider, large-font/accessibility, performance, battery and codec validation remain necessary. Debug signing can differ from earlier testing APKs; preserve needed data before uninstalling an existing build. No private signing key is committed.

Generated APK and documentation commits do not identify a different app binary. Prior upgrade evidence is preserved under validation/history/.
