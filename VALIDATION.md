# TN-Player file controls and player upgrade — 17 October 2026

## Verified source and checks

App source/build commit: `8abcd28b95983d7eebad2ece18701e65b16f3070`.

- [Feature branch checks](https://github.com/Tajoor621/TN-Player/actions/runs/38995652352): unit regressions, lint, debug/release assembly and Android 14 emulator checks passed. The native suite reported 13 cases: 11 executed and two recovery cases intentionally skipped in that ordinary run. The external process-recovery script separately runs those cases before/after a force-stop.
- Selection regressions cover scope changes, individual/all selection, deduplication, folder exclusion and bounded all-or-nothing selection. Persistence regressions cover retained duration, display settings, orientation and shortcut backup/restore.
- Native deletion tests use isolated test files and test-owned MediaStore content. No user library files are deleted by the validation workflow.
- Native rotation tests also assert visible time and skip controls on the compact landscape layout; the tools test waits for the sheet to be expanded before tapping.

- [Main Android checks](https://github.com/Tajoor621/TN-Player/actions/runs/38995962418): build and emulator jobs passed for this exact app source. The external preparation and cold-restore checks each reported OK (1 test).
- [APK publication and public verification](https://github.com/Tajoor621/TN-Player/actions/runs/38995962384): builds the matching debug APK, waits for Android validation, then publishes and verifies the public download.

## Published testing APK

- [Download TN-Player](https://tajoor621.github.io/TN-Player/downloads/TN-Player-1.0.0-debug.apk?build=8abcd28)
- Version 1.0.0 / code 1; package `com.tplayer.mediaplayer`
- Bytes: 12,264,493
- SHA-256: `f2663d2b0568682f09e6d76444851404aa39d668014d10764cb4ad62333bef00`
- Build/source revision: `8abcd28b95983d7eebad2ece18701e65b16f3070`. Later generated-APK and documentation commits do not identify a different app binary.

## UI evidence

[Seven native emulator screenshots](validation/reference-ui/README.md) show selection, portrait/landscape playback, tools, folders, view settings and the Me hub. The controls remain visible on the compact landscape emulator. The tools panel starts expanded and scrolls to its lower options.

## Changes verified

- Individual selection, selected count, Select all across matching pages, deselection and selected-file sharing. Folder changes clear selection; batch selection excludes folders and is bounded to 1,000 files.
- Explicit permanent-delete confirmation. Tests cancel the dialog and verify both fixtures survive, then delete only the selected fixture. Indexed Android 11+ media additionally requires the Android approval screen; cancelling the request leaves the file intact.
- Per-file information includes size, location, type, modified date, available duration/resolution/bitrate/tracks and last-played/resume details. History retains a known duration during transient updates.
- Player tools include audio/subtitle tracks, playing queue, aspect ratio, display settings, information, share, help and advanced actions. Shortcuts can be customized, and video display can be disabled while audio continues.
- Portrait/landscape/device orientation, fullscreen, lock/unlock, mute, mirror, vertical flip, night display and bounded PNG frame export. Tools scroll on short displays. Settings are included in backup/restore.
- Resume tests seed a per-file history point, open it, pause at a new point, play another file, rotate the video without resetting it, and return to the original file at its saved position. Existing background playback, PiP and force-stop restoration checks remain included.

## Limits

Testing covers an Android 14 emulator and JVM/Robolectric tests. Android 5 compatibility is lint-checked, not certified on physical API 21 devices. Provider/SD/USB behavior, large fonts, physical devices, codec coverage and performance still need device testing. No claim of universal codec/protocol support or a fully finished master feature list is made; see FEATURE_STATUS.md.

Deletion is permanent and has no recycle bin or rollback. A killed process never silently replays a deletion. History uses the existing 200-item retention policy; a near-complete item starts from the beginning. Frame export requires an accessible decoded frame and does not burn UI/display transformations into the image. The supplied TN logo remains unchanged.

Prior folder-browser evidence is preserved in [validation/history/folder-browser-2026-10-17.md](validation/history/folder-browser-2026-10-17.md). Debug signing can differ from older test builds; preserve needed data before uninstalling an existing build. No private signing key is committed.
