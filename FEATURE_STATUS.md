# Master specification: implementation status and solutions

“Implemented” means source exists. Build/test results are recorded separately in VALIDATION.md. Device-dependent playback behavior still requires the physical-device matrix. No placeholder button is presented as a completed premium feature.

| Requirement | Current implementation | Remaining work / concrete solution |
|---|---|---|
| Identity, version, Android baseline | com.tplayer.mediaplayer; 1.0.0; API 21–34 build baseline | Maintain IDs; measure each release on supported Android versions |
| Application / multidex / debug checks | MultiDexApplication, StrictMode, WorkManager | Add DI only when feature boundaries need it |
| Firebase crash/analytics/remote flags | Excluded; no telemetry by default | Add configured Firebase variant, consent persisted before SDK collection, Firebase Analytics (not a duplicate Google Analytics pipeline), consent withdrawal and deletion policy |
| Image loading / thumbnails | Glide local video thumbnails and MediaStore album art; recycled-view cancellation; 16 MB memory/64 MB disk cache | Benchmark thumbnail throughput and removable-provider artwork |
| Universal codecs / FFmpeg | Platform/Media3 decoders only | Build and audit an actively maintained native backend or LGPL-compatible FFmpeg decoder extension; package by ABI, verify 16 KB alignment and licenses; test actual codecs. FFmpeg audio extension alone is not universal video support |
| Splash | Uses normal Android startup window | Add AndroidX SplashScreen with measured startup; do not use a redundant launcher Activity |
| Activities | Main and Player implement working screens | Split by navigation feature when screens grow; avoid two engines or empty shell Activities |
| External intents | VIEW audio/video; SEND and bounded SEND_MULTIPLE queues | Richer file MIME associations, validated app links only for owned domain |
| Videos / music | MediaStore volume-aware folder counts/tree, global metadata sorts, list/grid, 200-row display pages, fields/filter preferences, permission/revocation UI and selected-video access | Lifecycle-scoped debounced change observation implemented; benchmark 50,000-file libraries |
| Playback service | Single MediaSessionService, focus/noisy handling, notification and trusted controllers | Encrypted queue/position restoration and explicit resume implemented; validate OEM restrictions; no automatic restart after force-stop |
| Foreground audio / lock-screen / headset | Delegated to Media3 service/session/audio focus | Test Bluetooth/car/OEM lock-screen paths; optional headset reconnect setting requires explicit behavior design |
| Seek, speed, loop, shuffle | Standard transport; 0.25–4×; repeat modes; playlists | Frame-accurate stepping requires decoder-specific implementation |
| A–B repeat and sleep timer | Service-owned loop and pause timer | Test seekable/live sources; persistence across process death not provided |
| Resume history | Room, checkpoint every 5 s, restarts near-completed files at 0 | Encrypted queue restoration implemented; abrupt termination may lose recent progress |
| Tracks and subtitles | Media3 tracks; external SRT/VTT; persistent size/color/typeface/background/position | Add delay pipeline, ASS/SSA renderer and image subtitles where supported |
| Online subtitle lookup | Not implemented | Use official OpenSubtitles REST API with a provisioned client key, user auth where required, rate limits, consent and cache; do not add unverified Maven coordinates |
| Video display | Persistent fit/fill/crop, rotation, pinch/pan 1–3× and window brightness gesture | Add color-processing shaders, deinterlace pipeline and screenshot capture with DRM restrictions |
| Audio processing | Service-owned device-band-aware Equalizer, capability-gated BassBoost/Virtualizer and saved controls | Add custom PCM processor for delay/boost with limiter and clipping tests. Do not label a device's 5-band EQ as 10-band |
| PiP | API 26+ guard, auto enter API 31+, runtime params, media-session system controls | Custom play/pause/previous/next RemoteActions implemented; add expanded PiP capability checks; measure transitions |
| File browsing | User-chosen SAF folder, nested navigation, hidden toggle, search | No root escalation; system/other-app private folders remain protected |
| File operations | Verified staged individual-file copy/move, rotation-safe progress/cancel UI, encrypted cleanup journal, rename, delete confirmation, share, new folder, properties | Add tree-recursive operations with self-descendant guards and multi-select; test document providers |
| Archives | Cancellable ZIP file/folder creation with CRC read-back; staged SAF extraction with path/collision, 512 MB, 5,000-entry and depth limits; originals retained | Process interruptions may leave hidden staging output; provider fault testing; separately license/test 7z/RAR |
| SD card / USB | Through Android document providers and granted tree URIs | Test removable volumes and provider revocation; direct USB-host support requires explicit UsbManager consent, not a fictional USB_PERMISSION |
| Streams | HTTPS progressive/HLS/DASH/SS; RTSP module | HTTP intentionally blocked. Add per-host opt-in only with explicit UX; RTMP/SMB/FTP/SFTP/NFS/DLNA/WebDAV require protocol-specific data sources and tests |
| Downloads | Android Download Manager for direct HTTPS media; local completed-file list | Add real queue/progress/error UI and Media3 DownloadService/cache for offline adaptive media; reject DRM/offline assumptions |
| Transfers / servers / Wi-Fi Direct | Not implemented | Design authenticated explicit sharing sessions, ephemeral credentials, foreground service only during transfers and tested connection lifecycles |
| Cast | Not implemented | Add Cast SDK with receiver capability checks and separate DLNA discovery/control; local files require explicitly enabled authenticated LAN serving |
| Cloud storage / sync | Not implemented | OAuth via provider SDK or Android document provider; encrypt secrets using Keystore; queue sync with conflict resolution and user opt-in |
| Themes | Light/dark/system and AMOLED with blue/cyan accent palette | Add accent presets and scheduled theme; physical-device contrast QA |
| Gestures / controls | Standard controls, lock, keyboard and configurable-on/off two-finger swipes, double-tap seek and pinch/pan; disabled under touch exploration | Add custom mapping presets; verify unlock using touch and TalkBack |
| TV and D-pad | Some standard focusable controls; no TV launcher claim | TV banner, Leanback UI, D-pad focus graph and emulator/physical TV certification |
| Settings / privacy / help | Reference-style Me hub with working shortcuts, view/layout draft dialog, detailed settings, PiP options, themes, notifications and help | Add settings migration and test full large-font landscape layouts |
| Database / credentials | Room collections with encrypted network URL payloads and opaque identifiers; Keystore-backed encrypted playback checkpoints; embedded credentials rejected | Add entities only with implemented clients; encrypt credentials with Keystore-backed storage and handle key invalidation |
| App lock / vault | Not implemented | BiometricPrompt plus rate-limited credential fallback; encrypt every file with authenticated encryption and Keystore-wrapped keys; use FLAG_SECURE in vault screens |
| Secure deletion | No guarantee made | Flash wear leveling, snapshots and providers prevent reliable overwrite; delete crypto keys for encrypted vault data and document limits |
| Providers | Narrow FileProvider for app exports | Avoid exported DB/settings providers; add secured providers only if a real external integration requires them |
| Widgets | Playback widget with title/progress and service-owned transport via immutable explicit PendingIntents | Add album-art and video/folder widget variants; physical launcher testing |
| Shortcuts | Static new-stream, resume and Files entry points | Add current-folder dynamic/pinned shortcuts with URI validity checks |
| Broad receivers | Delegated to framework/WorkManager/Media3 | Add narrowly scoped runtime receivers only where needed; never auto-play or launch UI on boot |
| Notifications | Media3 media playback; Android Download Manager | Add extra channels only for implemented user-visible events; no fabricated update channel |
| R8 / packaging | Optimized unsigned release build configuration, consumer rules | Production signing, dependency/license inventory; ABI splits only after native libraries exist |
| Backups | OS backup disabled; versioned 2 MB JSON export and previewed transactional collection merge via SAF; excludes network sources/credentials, media bytes and URI grants | Large/cross-provider round trips and interrupted preference-application testing |
| Tests and performance | Unit/database tests, navigation instrumentation, CI definition | Real-device codec corpus, fault injection, lifecycle and battery benchmarks required; numerical targets are unmeasured |

## Recommended sequence to finish the full product

1. Validate this native core on Android 5/8/11/13/14 and a Samsung phone; fix playback, permission and lifecycle defects first.
2. Improve large-library UX and file-operation progress/cancellation; expand backup migration, archive operations and provider fault testing.
3. Build an audited native decoder backend, publish an honest codec/protocol matrix and add delay/DSP/subtitle rendering with fixture-based tests.
4. Add authenticated network shares, transfers, adaptive downloads and Cast/DLNA as separate clients with explicit lifecycle and credential handling.
5. Build and review the encrypted vault, app lock, widgets, full gesture customization and TV UI.
6. Measure startup, seek latency, memory, thermal load and battery using representative hardware; complete signing and current distribution checks.

Do not turn every declaration in an extracted third-party manifest into a required TN-Player component. Add only components that own actual behavior.
