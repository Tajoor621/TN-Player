# TN-Player repository audit release — 17 October 2026

## Source and checks

App source/build: `5a8c4a9e59e757985556f12bd675808cd22215e5`. Later APK-publication/documentation commits do not identify a different app binary.

- [Branch checks](https://github.com/Tajoor621/TN-Player/actions/runs/39002930201): build, lint, unit tests and both native jobs passed.
- [Merged-source Android checks](https://github.com/Tajoor621/TN-Player/actions/runs/39003239181): build plus Android 9/API 28 and Android 14/API 34 jobs passed. Debug and optimized unsigned release assembly succeeded.
- [APK publication and public verification](https://github.com/Tajoor621/TN-Player/actions/runs/39003239426): packages the matching APK, waits for all Android jobs, then publishes to main/root Pages and verifies its revision, byte count and complete SHA-256.

| Check | Recorded result |
|---|---|
| JVM / Robolectric | 42 tests, zero failures/ignored |
| Android 9 ordinary suite | 12 reported, 10 executed, two recovery cases intentionally skipped; zero failures/errors |
| Android 14 ordinary suite | 14 reported, 12 executed, two recovery cases intentionally skipped; zero failures/errors |
| External recovery on each OS | Preparation OK (1 test), force-stop, cold restoration OK (1 test) |
| Lint / packaging | No lint errors; debug and R8 release builds pass. Existing lint warnings remain; abort-on-error stays enabled. |
| Public APK | Passed in GitHub and rechecked directly: source revision, byte count and full APK SHA-256 match. |

The two MediaStore tests requiring API 29/30 are not selected on Android 9. The ordinary suite's two recovery cases run separately through the process-recovery script. Do not count the skipped ordinary cases as executed tests. Machine-readable branch counts are in [validation/audit/branch-results.json](validation/audit/branch-results.json); the [resolved release dependency tree](validation/audit/release-dependencies.txt) comes from that same tested source. It is not a complete license or vulnerability certificate.

## What this update implements

- Moves recheck the source bytes/hash before deleting it. Same-length changes, destination corruption, disk failure and cancellation are regression-tested. A changed/unreadable source or failed deletion leaves both copies. Providers do not expose a universal atomic compare-and-delete transaction; another writer can still race the final check/delete boundary.
- Ordered, bounded pending history replaces the dropping event queue. Immediate lookups wait for writes; latest updates coalesce per URI within the existing 200-file retention policy. Failed writes/failed clear preserve pending positions. History flush/clear and information dialogs use the ordered path. New explicit playback requests retain precedence over startup checkpoint restoration.
- Downloads now lists queued/running/paused/completed/failed jobs, progress and actionable reason text. Retry, confirmed cancel/remove, completed Play/Share/Properties, legacy storage-permission handling and explicit adaptive-manifest rejection are implemented. Polling is lifecycle-owned; unchanged rows/status are not rebound. List limit: 500 records.
- Native download tests import a disposable local fixture, verify its completed record, keep it through cancellation, then confirm removal and verify deletion. URL/MIME/progress/status policy has unit tests. End-to-end Internet downloads, server interruption/redirect behavior and actual full storage still require the physical/network fault matrix.
- Both workflows pin seven Actions dependencies to upstream commit hashes. Monthly grouped/capped Dependabot update configuration and a resolved release dependency artifact are included. Bot pushes are excluded from Android CI to avoid duplicate push/PR runs. Automated update proposals still require review, especially when new libraries raise their minimum Android version.

## UI evidence

[Native screenshot gallery](validation/reference-ui/README.md): Android 9/14 Downloads, selection, portrait/landscape player, tools, folders, view settings and the Me hub. Captures are from the passing branch run of the same source revision. Test patterns and downloaded/selected media are synthetic, temporary fixtures, removed after testing. They are not shipped as the user's media library. No real user media was deleted by validation.

The supplied TN logo, Android package, version, Room schema 2, backup format and existing collections remain unchanged.

## Published testing APK

- [Download TN-Player](https://tajoor621.github.io/TN-Player/downloads/TN-Player-1.0.0-debug.apk?build=5a8c4a9)
- Version 1.0.0 / code 1; package `com.tplayer.mediaplayer`
- Bytes: 12,283,520
- SHA-256: `dfa7097581bbda03e5cc0d0bb197240753751993f3d8d7b0c20e6ddc1c453313`

Debug signing can differ from older testing builds. Preserve needed files/exports before uninstalling an existing build. Production signing and upgrade-install validation remain release work; no private signing key is committed.

## Remaining scope

The [professional audit and prioritized roadmap](AUDIT.md) and [full feature matrix](FEATURE_STATUS.md) distinguish implemented fixes from unfinished work. Current target/compile 34 and API 21 minimum remain the requested development baseline. API 36/toolchain migration, maintained decoder/dependency evaluation, production signing and full notices, external-queue metadata threading, physical Samsung/SD/USB/accessibility coverage and measured startup/seek/memory/battery results remain necessary. Cast/DLNA, network-share clients, universal native decoding, adaptive offline downloads and encrypted vault/app lock are not completed by this update.

Android DownloadManager remains the download backend; migration to an app-controlled HTTP/scheduled-write pipeline is explicitly recommended in the audit. No numerical performance target or whole-product “10/10” certification is claimed.

Prior delivery evidence: [file/player controls](validation/history/file-controls-2026-10-17.md), [folder browser](validation/history/folder-browser-2026-10-17.md), and earlier history folders.
