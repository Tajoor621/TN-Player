# TN-Player — MX Player comparison delivery

Validated source: [`7b32affb39d66929ff8b0a04d5c0d049395faa42`](https://github.com/Tajoor621/TN-Player/commit/7b32affb39d66929ff8b0a04d5c0d049395faa42), merged into `main` on 17 October 2026. This is an independently implemented feature update, not full MX Player parity.

## Implemented and checked

- Platform codec preference: automatic / prefer hardware / prefer platform software, compatible initialization fallbacks, settings/backup persistence, service-owned reinitialization preserving paused queue position.
- External text subtitle imports: SRT, WebVTT, MPL2, PJS, MicroDVD with explicit FPS, SubViewer 2.0, TMPlayer. Input size, encoding, cue count, line length, timestamp and FPS limits are checked.
- Timed imports render in a bounded overlay driven by player position. Delay ±10 minutes, previous/next caption seek, overlapping-cue handling, subtitle gesture mode, styles and adjusted-SRT export are implemented. Android tests verify actual on-screen caption text, paused seeks, +1000 ms timing changes and Activity recreation.
- Native external ASS/SSA and TTML/DFXP loading uses Media3 with an embedded-styles toggle. This is a styling subset; these formats still require dedicated physical-device fixtures. It is not a full libass implementation.
- Android screen pinning is exposed for kids lock, alongside existing control lock. Android owns approval and unpin behavior; physical OEM tests remain required.
- Shared-file display-name lookups and subtitle reads/parsing run off the UI thread. Original subtitle/media files are not rewritten by import or timing changes.

## Evidence

| Check | Result |
| --- | --- |
| [Feature branch CI](https://github.com/Tajoor621/TN-Player/actions/runs/39022223508) | Build, lint, debug/release APK and both emulator jobs passed |
| [Main CI](https://github.com/Tajoor621/TN-Player/actions/runs/39022517661) | All three jobs passed |
| JVM/Robolectric | 48 tests, zero failures, zero ignored |
| API 28 ordinary instrumentation | 14 reported, zero failures/errors, two recovery-only tests skipped in this suite; API 29/30-only MediaStore cases excluded |
| API 34 ordinary instrumentation | 16 reported, zero failures/errors, two recovery-only tests skipped in this suite |
| Separate process recovery | Preparation and post-force-stop cold restoration each passed on both OS versions |
| [APK publication and verification](https://github.com/Tajoor621/TN-Player/actions/runs/39022517701) | Publication and public full-APK SHA-256 verification passed; independently verified again from this workspace |

The initial caption tests exposed metadata-only subtitle replacement and unreliable paused native text output. The final timed-import path renders from the validated cue timeline and tests the visible TextView. Rendering assertions were retained; tests were not disabled to obtain a green run.

## Public APK

[Download TN-Player debug APK](https://tajoor621.github.io/TN-Player/downloads/TN-Player-1.0.0-debug.apk?build=7b32aff)

- Package: `com.tplayer.mediaplayer`
- Version: 1.0.0 / code 1
- Source: `7b32affb39d66929ff8b0a04d5c0d049395faa42`
- Bytes: 12,319,805
- SHA-256: `f6ff8d4a72aeb87d792097d78bd37bdfb84fa08a2fd823b76f0e1d830b37cb4a`

Debug signing can differ from older testing builds. Preserve needed files/exports before uninstalling an existing build. Production signing and upgrade-install validation remain release work; no private signing key is committed.

## Remaining scope

The [current feature comparison](MX_PLAYER_COMPARISON.md), [master feature matrix](FEATURE_STATUS.md) and [professional audit](AUDIT.md) keep remaining work explicit. Universal native codecs/HW+, a secure private folder/app lock, Wi-Fi Direct/QR transfer, native image/SAMI/Teletext subtitle coverage, simultaneous external subtitles, online subtitle search, seek previews, custom fonts, subtitle speed correction, clock/battery overlay and PCM volume amplification are not completed by this update.

Timed external-caption state survives Activity recreation; finishing the player or process death discards this imported-caption session. Reload its source on return. Playback queue/history recovery is separately persisted and tested. Gesture mappings differ from MX Player. Physical OEM screen-pinning, accessibility, SD/USB, native styled-caption and codec/format matrix checks remain necessary.

Target/compile SDK 34 and minimum SDK 21 remain the requested baseline. Play-target migration, dependency/native-backend licensing, release signing, network-share/Cast clients, adaptive downloads and measured performance targets remain release/roadmap work. No whole-product 10/10 certification is claimed.

Earlier audit evidence: [repository audit](validation/history/repository-audit-2026-10-17.md), [file/player controls](validation/history/file-controls-2026-10-17.md), [folder browser](validation/history/folder-browser-2026-10-17.md). Existing device screenshots are in [the reference gallery](validation/reference-ui/README.md).
