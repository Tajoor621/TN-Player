# TN-Player 1.0.0 rebrand validation

## Current checks

- Rebrand build: `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest` — BUILD SUCCESSFUL.
- Unit/Robolectric: **20 passed, 0 failures, 0 errors**.
- Lint: **0 errors, 41 warnings**; full report in validation/branding/.
- Built debug, R8-optimized unsigned release and instrumentation APKs. Android apksigner verifies the delivered debug APK; detailed scheme/certificate results are retained.
- Packaged label confirmed as **TN-Player**, application class as **TNPlayerApplication**, package ID as com.tplayer.mediaplayer, version 1.0.0/code 1, minSdk 21 and targetSdk 34.
- Packaged logo resource confirmed; legacy and Android 8+ adaptive launcher resources reference the new artwork. About settings includes the same logo.
- Original generated PNG and generation prompt included under branding/.

## Scope and continuity

This revision changes branding, application-class/theme names and icon presentation. Playback, encrypted URL/checkpoint formats, database schema and internal storage/key identifiers retain the previous reliability implementation. All active display-name references were updated; historical reports retain their original names so past evidence is not rewritten.

Native playback/UI tests were **not rerun for this rebrand**. Historical native audio, Keystore, local move and force-stop/cold-restoration passes are retained under validation/history/. Prior navigation/video/PiP failures and system-wide emulator ANRs remain open validation gates. This is a testing build, not production certification or a complete 10/10 implementation. FFmpeg, casting, network shares, vault, advanced processing and other unfinished modules remain documented in FEATURE_STATUS.md.

## Signing and installation

The debug certificate differs from the previously delivered T-Player APK, so Android will reject an in-place update. Uninstalling the earlier app deletes its app data; preserve data you need before any uninstall.

Debug builds are for testing. Production distribution needs a stable privately managed signing key. No private signing material is included in the complete project ZIP.

## Delivered APK

- Filename: TN-Player-1.0.0-debug.apk
- Bytes: 11860209
- SHA-256: `4c40c9bf5424f85de92b30bc59e84642732491ceb17ce5a442f355a54789b13e`
- Signing certificate SHA-256: `1df6964f4d705deab8ea4f38a056ab2bf33eae56603e1a1f18ab45d62078a7a9`

The complete ZIP merges the current source, Gradle wrapper, CI workflow, tests/fixtures, original logo, reports and this APK under apk/. Build caches, SDK/JDK installations and obsolete source copies are excluded. Payload inventory and hashes are in BUNDLE_MANIFEST.json and CHECKSUMS.sha256.
