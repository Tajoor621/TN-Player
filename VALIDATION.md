# TN-Player — supplied icon and professional audit delivery

Validated application source: [`18c0426e2e683cb0add4bec86607d78e018677ce`](https://github.com/Tajoor621/TN-Player/commit/18c0426e2e683cb0add4bec86607d78e018677ce), integrated into `main` on 18 October 2026.

## Implemented and checked

- Supplied gold/cyan artwork preserved unchanged; separate emblem adaptation for launcher legibility. Density icons, adaptive and themed icons, toolbar, settings/about, widget, shortcuts, website and README use the current identity. Provenance and reproduction instructions: [branding](branding/README.md).
- Atomic authenticated-encrypted recent timed-caption sessions preserve source association, text, offset and speed across finishing the player and process death. Up to ten sessions / 32 MB are retained. Restoration neither opens media nor starts playback. Failed encryption preserves the old committed record; corruption is reported without a plaintext fallback.
- Subtitle speed correction: 50–200%, 0.1% increments; visible cues, navigation and adjusted-SRT export use the same transformation. Embedded-track switching explicitly removes the external association after confirmation. WebVTT metadata handling and malformed-timing rejection have regression tests.
- Gesture side presets, double-tap seek/play-pause choice, bounded caption gesture adjustment and optional clock/battery/remaining-time display. Preferences survive backup round trips; touch-exploration suppression remains enabled.
- Bounded stream and playlist inputs reject oversized requests before Android intent delivery. Original media and subtitle files are retained.

## Evidence

| Check | Result |
| --- | --- |
| [Feature branch CI](https://github.com/Tajoor621/TN-Player/actions/runs/39160566399) | Build, lint, debug/release APK and both emulator jobs passed (lint: 0 errors, 88 warnings) |
| [Main CI](https://github.com/Tajoor621/TN-Player/actions/runs/39160944641) | All three jobs passed |
| JVM/Robolectric | 56 tests, zero failures, zero ignored |
| API 28 ordinary instrumentation | 15 reported, zero failures/errors, two recovery-only tests skipped in this suite; API 29/30-only MediaStore cases excluded |
| API 34 ordinary instrumentation | 17 reported, zero failures/errors, two recovery-only tests skipped in this suite |
| Separate process recovery | Preparation and post-force-stop cold restoration each passed on both OS versions; saved caption text/delay/rate checked after a new process |
| [APK publication and verification](https://github.com/Tajoor621/TN-Player/actions/runs/39160944588) | Package and public checksum verification passed; full APK independently verified again from the workspace |
| Local resource checks | All 33 resource XML files parsed; five launcher densities verified; source/master hashes match provenance manifest; `git diff --check` passed |

JUnit XML is the source for the ordinary-suite counts. Recovery is exercised separately using explicit preparation and cold-restore invocations. The subtitle speed action is selected through its scrollable list in the test; rendering/timing assertions remain enabled.

## Public APK

[Download TN-Player debug APK](https://tajoor621.github.io/TN-Player/downloads/TN-Player-1.0.0-debug.apk?build=18c0426)

- Package: `com.tplayer.mediaplayer`
- Version: 1.0.0 / code 1
- Source: `18c0426e2e683cb0add4bec86607d78e018677ce`
- Bytes: 11,635,069
- SHA-256: `09a591ff763ca33fa23eda4bf8d20d68f78c04a5fe40cee5f587efb4223dd604`

Debug signing can differ from older testing builds. Production signing and upgrade-install validation remain release work; no private signing key is committed.

## Remaining scope

The [18 October audit](AUDIT_2026-10-18.md) gives a provisional **7/10 engineering assessment**, with functional core/data safety weighted most heavily. It is not a benchmark or whole-product certification. Full native codec coverage, a secure vault/app lock, Cast/DLNA and authenticated shares, an app-owned download backend, PCM delay/boost, advanced subtitle coverage and physical-device performance/accessibility testing remain open. Stable production signing and current Play-target/toolchain migration are release blockers. Compile/target 34 and minimum SDK 21 are retained in this update.

Timed imported text-caption sessions now persist; native ASS/SSA/TTML sidecar configurations do not gain persistence in this revision. JSON library backups exclude caption text and network secrets. Caption retention is bounded and is not a permanent archive. Force-stop restoration remains paused until the user explicitly resumes.

The full feature matrix is in [FEATURE_STATUS.md](FEATURE_STATUS.md), with comparison limits in [MX_PLAYER_COMPARISON.md](MX_PLAYER_COMPARISON.md). Previous evidence is preserved in [MX comparison delivery](validation/history/mx-comparison-2026-10-17.md), [repository audit](validation/history/repository-audit-2026-10-17.md), [file/player controls](validation/history/file-controls-2026-10-17.md) and [folder browser](validation/history/folder-browser-2026-10-17.md). The dated reference gallery remains [here](validation/reference-ui/README.md); fresh screenshots are available in this revision's CI device-report artifacts.
