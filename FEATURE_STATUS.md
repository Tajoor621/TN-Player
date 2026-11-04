# Master specification: implementation status and solutions

## Gesture controls workflow — 3 November 2026 (feature prompt 2/3)

Read FEATURE_STATUS.md and AUDIT.md before changes. Existing UI is
`ui/PlayerActivity.kt` (Views/Material/Media3 PlayerView); the previous
`ui/PlayerGestures.kt` handled two-finger swipes and limited presets.

The update routes video gestures before child dispatch, preserves transport
buttons and OS edges, adds independent persistent assignments for five slots,
held 2×/speed restoration, bounded zoom/pan/reset, per-file subtitle controls and
long-press-only unlock. Existing service-owned sleep/A–B tools are retained.
Native text timing is isolated from A/V; decoder history/lookahead limitations
and system gesture conflicts are explicit in [docs/GESTURES.md](docs/GESTURES.md).

Mapping/settings/lock/hold/subtitle unit tests and a real-player double-tap device
test are added. A manual preinstalled-build capture runner records each gesture;
**screenshots and instrumented execution are deferred**, not represented by test
source. Source-only Kotlin/JVM/lint checks run without APK packaging. No new
dependency, native library, permission, toolkit or player engine is introduced.
See [the current integration count and build gate](DEVELOPMENT_PHASE.md).


## Performance workflow — 2 November 2026 (feature prompt 1/3)

Playback setup is `MediaPlaybackService.onCreate`: `TnRenderersFactory`,
`RecoveryTrackSelector`, one ExoPlayer/MediaSession and `PlaybackRecovery`.
Platform hardware is first; the existing pinned native extensions provide finite
per-file software recovery. This work adds capability-selected opt-in tunneling
with one non-tunneled retry, bounded local next-item byte warming after first
output, startup scan deferral and opt-in 10-second paused-background cleanup.
All new settings default off and are included in settings backup/restore.

The debug-only manual `PlaybackBenchmarkTest` uses the production service and
PlayerView, checks three hashed offline references (including 4K HEVC and 10-bit
PQ), exports CSV and measures a 30-minute background/paused-release run.
`scripts/benchmark.sh` runs preinstalled matching APKs without building them.
No performance or leak-free acceptance has passed yet: see
[measurement contract and pending results](docs/BENCHMARKS.md). Host fixture
validation and parser checks are distinct from device measurements. The 3 November
follow-up requires continuous progress samples and finite metrics; `--baseline CSV`
compares matched device/build/file hashes without treating single observations as
statistical proof. Twenty-four synthetic parser/CLI tests cover these paths.


“Implemented” means source exists. Build/test results are recorded separately in VALIDATION.md. Device-dependent playback behavior still requires the physical-device matrix. No placeholder button is presented as a completed premium feature.

| Requirement | Current implementation | Remaining work / concrete solution |
|---|---|---|
| Identity, version, Android baseline | com.tplayer.mediaplayer; 1.0.0; minSdk 21; compileSdk and targetSdk 36 | Maintain IDs; measure each release on supported Android versions |
| Application / multidex / debug checks | MultiDexApplication, StrictMode, WorkManager | Add DI only when feature boundaries need it |
| Firebase crash/analytics/remote flags | Excluded; no telemetry by default | Add configured Firebase variant, consent persisted before SDK collection, Firebase Analytics (not a duplicate Google Analytics pipeline), consent withdrawal and deletion policy |
| Image loading / thumbnails | Glide local video thumbnails and MediaStore album art; recycled-view cancellation; 16 MB memory/64 MB disk cache | Benchmark thumbnail throughput and removable-provider artwork |
| Universal codecs / FFmpeg | Hardware-first Media3 plus source-built FFmpeg AC3/EAC3/DTS/TrueHD and separate libvpx VP9/libgav1 AV1 extensions; bounded runtime renderer recovery and diagnostics | Four-ABI/16 KB verification and synthetic codec runs are recorded in [VALIDATION.md](VALIDATION.md#pre-deferral-evidence--2-november-2026). [Physical codec and release checks remain open](docs/FORMATS.md#build-size-and-evidence). |
| Splash | Uses normal Android startup window | Add AndroidX SplashScreen with measured startup; do not use a redundant launcher Activity |
| Activities | Main and Player implement working screens | Split by navigation feature when screens grow; avoid two engines or empty shell Activities |
| External intents | VIEW audio/video; SEND and bounded SEND_MULTIPLE queues | Richer file MIME associations, validated app links only for owned domain |
| Videos / music | MediaStore volume-aware folder counts/tree, global metadata sorts, list/grid, 200-row display pages, fields/filter preferences, permission/revocation UI and selected-video access | Lifecycle-scoped debounced change observation implemented; benchmark 50,000-file libraries |
| Playback service | Single MediaSessionService, focus/noisy handling, notification and trusted controllers | Encrypted queue/position restoration and explicit resume implemented; validate OEM restrictions; no automatic restart after force-stop |
| Foreground audio / lock-screen / headset | Delegated to Media3 service/session/audio focus | Test Bluetooth/car/OEM lock-screen paths; optional headset reconnect setting requires explicit behavior design |
| Seek, speed, loop, shuffle | Standard transport; 0.25–4×; repeat modes; playlists | Frame-accurate stepping requires decoder-specific implementation |
| A–B repeat and sleep timer | Service-owned loop and pause timer | Test seekable/live sources; persistence across process death not provided |
| Resume history | Room, ordered pending-history reads, checkpoint every 5 s, restarts near-completed files at 0 | Encrypted queue restoration implemented; abrupt termination may lose recent progress |
| Tracks and subtitles | Media3 tracks; manually loaded external SRT/VTT, MPL2, PJS, MicroDVD/SubViewer, TMPlayer; encrypted recent-caption restoration, timed-import delay/speed correction and cue navigation/export; external native SSA/ASS/TTML; subtitle gesture mode; persistent style | Text-only native cue scheduling now supports per-file delay with bounded history/lookahead; full libass styling, image sidecars, SAMI ruby and multi-external tracks remain; see MX_PLAYER_COMPARISON.md |
| Online subtitle lookup | Not implemented | Use official OpenSubtitles REST API with a provisioned client key, user auth where required, rate limits, consent and cache; do not add unverified Maven coordinates |
| Video display | Persistent fit/fill/crop, rotation, pinch/pan 1–3× and window brightness gesture | Mirror/vertical flip/night display, explicit portrait/landscape/fullscreen and bounded PNG frame export implemented; add color-processing shaders and deinterlace pipeline |
| Audio processing | Service-owned device-band-aware Equalizer, capability-gated BassBoost/Virtualizer and saved controls | Add custom PCM processor for delay/boost with limiter and clipping tests. Do not label a device's 5-band EQ as 10-band |
| PiP | API 26+ guard, auto enter API 31+, runtime params, media-session system controls | Custom play/pause/previous/next RemoteActions implemented; add expanded PiP capability checks; measure transitions |
| File browsing | User-chosen SAF folder, nested navigation, hidden toggle, search | No root escalation; system/other-app private folders remain protected |
| File operations | Verified staged individual-file copy/move with pre-delete source recheck, rotation-safe progress/cancel UI, encrypted cleanup journal, rename, delete confirmation, share, new folder, properties | Individual and select-all file controls, confirmed batch deletion with Android approval and multi-share implemented; add tree-recursive copy/move with self-descendant guards and test document providers |
| Archives | Cancellable ZIP file/folder creation with CRC read-back; staged SAF extraction with path/collision, 512 MB, 5,000-entry and depth limits; originals retained | Process interruptions may leave hidden staging output; provider fault testing; separately license/test 7z/RAR |
| SD card / USB | Through Android document providers and granted tree URIs | Test removable volumes and provider revocation; direct USB-host support requires explicit UsbManager consent, not a fictional USB_PERMISSION |
| Streams | HTTPS progressive/HLS/DASH/SS; RTSP module | HTTP intentionally blocked. Add per-host opt-in only with explicit UX; RTMP/SMB/FTP/SFTP/NFS/DLNA/WebDAV require protocol-specific data sources and tests |
| Downloads | Android Download Manager for direct HTTPS media; lifecycle-scoped status/progress/reasons, retry and confirmed cancel/remove; completed playback/share/details | Migrate the system backend to app-owned HTTP/scheduled writes per Android security guidance; add Media3 offline adaptive cache/DRM handling |
| Transfers / servers / Wi-Fi Direct | Not implemented | Design authenticated explicit sharing sessions, ephemeral credentials, foreground service only during transfers and tested connection lifecycles |
| Cast | Not implemented | Add Cast SDK with receiver capability checks and separate DLNA discovery/control; local files require explicitly enabled authenticated LAN serving |
| Cloud storage / sync | Browse folders granted by installed Android document providers | [Direct cloud clients and sync remain open](#recommended-sequence-to-finish-the-full-product); the provider controls login, availability and offline access |
| Themes | Light/dark/system and AMOLED with blue/cyan accent palette | User-supplied gold/cyan identity integrated across icons, widget, settings and web; add accent presets/scheduled theme and physical-device contrast QA |
| Gestures / controls | One-finger swipes, five persistent assignments, held 2× restoration, zoom/pan/reset, long-hold unlock; disabled under touch exploration | Central thresholds, encrypted per-file subtitle controls and screenshot capture runner implemented; physical edge/TalkBack/lock and native-caption corpus validation remain |
| TV and D-pad | Some standard focusable controls; no TV launcher claim | TV banner, Leanback UI, D-pad focus graph and emulator/physical TV certification |
| Settings / privacy / help | Reference-style Me hub with working shortcuts, view/layout draft dialog, detailed settings, PiP options, themes, notifications and help | Add settings migration and test full large-font landscape layouts |
| Database / credentials | Room collections with encrypted network URL payloads and opaque identifiers; Keystore-backed encrypted playback checkpoints; embedded credentials rejected | Add entities only with implemented clients; encrypt credentials with Keystore-backed storage and handle key invalidation |
| App lock / private folder / vault | No PIN folder or encrypted vault on `main`; player control lock and Android screen pinning are separate | [PIN folder is under review](DEVELOPMENT_PHASE.md); [encrypted vault and app lock remain open](AUDIT.md#features-worth-building-next) |
| Secure deletion | No guarantee made | Flash wear leveling, snapshots and providers prevent reliable overwrite; delete crypto keys for encrypted vault data and document limits |
| Providers | Narrow FileProvider for app exports | Avoid exported DB/settings providers; add secured providers only if a real external integration requires them |
| Widgets | Playback widget with title/progress and service-owned transport via immutable explicit PendingIntents | Add album-art and video/folder widget variants; physical launcher testing |
| Shortcuts | Static new-stream, resume and Files entry points | Add current-folder dynamic/pinned shortcuts with URI validity checks |
| Broad receivers | Delegated to framework/WorkManager/Media3 | Add narrowly scoped runtime receivers only where needed; never auto-play or launch UI on boot |
| Notifications | Media3 media playback; Android Download Manager | Add extra channels only for implemented user-visible events; no fabricated update channel |
| R8 / packaging | Optimized unsigned release build configuration, consumer rules | Production signing, dependency/license inventory; ABI splits only after native libraries exist |
| Backups | OS backup disabled; versioned 2 MB JSON export and previewed transactional collection merge via SAF; excludes network sources/credentials, media bytes and URI grants | Large/cross-provider round trips and interrupted preference-application testing |
| Tests and performance | Unit/database/race/fault regressions, Android 9/14 native CI, pinned Actions, monthly dependency-update configuration and resolved release inventory | Real-device codec corpus, fault injection, lifecycle and battery benchmarks required; numerical targets are unmeasured |

## Recommended sequence to finish the full product

1. Validate this native core on Android 5/8/11/13/14 and a Samsung phone; fix playback, permission and lifecycle defects first.
2. Improve large-library UX and file-operation progress/cancellation; expand backup migration, archive operations and provider fault testing.
3. Validate the existing native decoder extensions on physical hardware, and extend audio delay/DSP and subtitle format support with fixture-based tests.
4. Add authenticated network shares, transfers, adaptive downloads and Cast/DLNA as separate clients with explicit lifecycle and credential handling.
5. Build and review the encrypted vault, app lock, additional widgets and TV UI.
6. Measure startup, seek latency, memory, thermal load and battery using representative hardware; complete signing and current distribution checks.

Do not turn every declaration in an extracted third-party manifest into a required TN-Player component. Add only components that own actual behavior.

Latest prioritized source/security/release findings: [AUDIT.md](AUDIT.md). This source branch targets and compiles against API 36 while retaining minSdk 21. See the [toolchain changes, device checks and tracked deprecations](docs/ANDROID_36.md). The published APK has not been replaced.

## MX Player comparison additions

See [MX_PLAYER_COMPARISON.md](MX_PLAYER_COMPARISON.md) for the source-based gap matrix, platform decoder preferences with fallback, external subtitle formats, timing and navigation, subtitle gesture mode, Android screen pinning, and explicit remaining limitations. These additions do not provide full MX Player feature parity.


## 18 October audit delivery

The latest icon integration and prioritized source review are in [AUDIT_2026-10-18.md](AUDIT_2026-10-18.md). Added local encrypted retention of up to ten recent timed-caption sessions (32 MB total budget), speed correction, selectable embedded-track switching, gesture presets and optional clock/battery/remaining-time overlays. Native ASS/SSA/TTML session persistence is still separate work; this is not a full product completion claim.

## 2 November software decoder delivery

See [docs/FORMATS.md](docs/FORMATS.md) for exact codecs/containers, hardware/software/unsupported paths, pinned dependencies, licenses, source/relink distribution and size/testing limits. Four FFmpeg audio decoders and separate AV1/VP9 native extensions are added; existing H.264/MP4 paths are retained. Automated JNI/PCM/recovery and release checks must pass before publication. Physical speaker audibility and a comprehensive format corpus remain separate acceptance work.
