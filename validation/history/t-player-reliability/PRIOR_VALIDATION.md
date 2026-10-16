# T-Player 1.0.0 reliability validation

This is a hardened native playback/library core. It is not the complete master specification and is not rated 10/10.

## Executed checks

- Gradle: `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest` — BUILD SUCCESSFUL on the final source.
- Unit/Robolectric: **20 passed, zero failures, zero errors, zero skipped**.
- Android lint: **0 errors, 33 warnings**. Warnings include localization, API-specific manifest attributes, programmatic views, and an application-context singleton warning; they are not silently suppressed.
- Debug, R8/shrunk unsigned release, and instrumentation APKs built. Debug APK cryptographic verification completed using Android apksigner; detailed signature results are included.
- Package `com.tplayer.mediaplayer`, versionName `1.0.0`, versionCode `1`, minSdk `21`, target/compileSdk `34` retained.

| Test class | Tests | Failures | Errors |
|---|---:|---:|---:|
| DatabaseTest | 4 | 0 | 0 |
| MigrationTest | 1 | 0 | 0 |
| NavigationSmokeTest | 2 | 0 | 0 |
| PlaybackPolicyTest | 4 | 0 | 0 |
| SecureCheckpointTest | 3 | 0 | 0 |
| SettingsPersistenceTest | 1 | 0 | 0 |
| VerifiedTransferTest | 5 | 0 | 0 |

## Device checks

Android 14 software emulator: the first batch completed with **3 of 5 checks passed**: real Android Keystore, verified local file move, and audio continuation/pause-screen-awake. Navigation failed to acquire window focus; video did not start before its timeout. The retry's main navigation/playback batch terminated before completing. Diagnostics show ANRs in Android System UI, Bluetooth, telephony, media provider, and T-Player amid severe system CPU pressure; application defects cannot be excluded. Navigation/video/PiP remain an open validation gate. The separate encrypted-checkpoint preparation and force-stop/cold-restoration sequence passed in the retry. Both attempts and diagnostics are retained. Repeat on a physical device or a hardware-accelerated emulator.

| Attempt | Check | Result |
|---|---|---|
| First | settingsAndHelpAreReachableWithoutMediaPermission | Fail/skip |
| First | realAndroidKeystoreRoundTripsAndRejectsWrongPurpose | Pass |
| First | videoEntersPipAndPlaybackSurvivesTheTransition | Fail/skip |
| First | localFileMoveVerifiesItsBytesBeforeRemovingSource | Pass |
| First | audioContinuesAfterActivityClosesAndPauseClearsScreenAwake | Pass |
| Retry | persistAQueueForTheExternalProcessDeathCheck | Pass |
| Retry | aNewProcessRestoresTheEncryptedQueueAndPosition | Pass |

No physical Samsung/other-phone tests, Android 5 Keystore tests, Bluetooth/automotive checks, 50,000-file benchmark, comprehensive codec/protocol corpus, accessibility matrix, or battery/performance measurements were completed. Numerical targets remain unmeasured. The included GitHub workflow was not published to or run in the user's account.

## Implemented reliability changes

Files navigation survives playlist-dialog cancellation. Collections refresh on return. MediaStore loads bounded keyset pages with background search. A now-playing bar exposes transport controls while browsing. Pausing clears screen-awake and stops A–B polling. Queue, position, MIME, repeat and shuffle state use encrypted checkpoints and explicit resume. Abrupt process loss may still lose recent seconds.

Network URLs in the app's collections use encrypted payloads and keyed opaque identifiers. Database v1→v2 migration preserves collections and resume positions while encrypting URL fields and redacting URL-bearing titles. This does not guarantee erasure of old flash blocks, backups or provider/system copies. Ordinary local URIs and metadata remain app-private plaintext.

Individual file transfers use staging, SHA-256 read-back verification, then final rename; originals are removed only after verification. Progress/cancel state survives rotation. An encrypted journal identifies unfinished temporary destinations. Provider failure/revoked access can prevent cleanup; recursive/multi-select operations are not implemented.

## Installation compatibility

This build's debug certificate differs from the earlier delivered debug APK. Android will reject an in-place update over that APK. Uninstalling the earlier app deletes its app data. Do not uninstall an existing installation containing data you need without preserving that data first. No production signing key or migration/export tool is supplied.

## Artifact

- File: `T-Player-1.0.0-debug.apk`
- Size: 10,816,950 bytes
- SHA-256: `88faa771559c11a3b38f93c517d684b4d27c72f210648ba9b31ac16bacffe794`
- Debug-signed testing build; the separately generated release APK is unsigned.

FFmpeg universal decoding, Cast/DLNA, network shares, cloud sync, advanced audio/video processing, encrypted vault/app lock, widgets and full customization remain unfinished. FEATURE_STATUS.md and TEST_PLAN.md describe the next work. Build success and targeted regression tests do not establish production readiness.
