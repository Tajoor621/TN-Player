# TN-Player 1.0.0 reliability revision

## 2 November 2026 — decoder source integration (APK deferred)

- Add pinned source-built FFmpeg AC3/EAC3/DTS/TrueHD audio and libgav1 AV1/libvpx VP9 fallback to the existing service-owned Media3 player.
- Add bounded track-group renderer recovery, codec-named non-blocking notices, corrupt/no-output guards and decoder/container diagnostics.
- Correct stereo TrueHD channel-layout/seek handling; add stereo/six-channel PCM, fallback, unsupported/corrupt audio, AV1/VP9 frame and optimized-release test workflows.
- Document native licenses, source/relink material and measured APK overhead.
- Per owner instruction, integrate source while three further feature prompts are pending; disable automatic APK production/publication and retain explicit manual final-build controls. Existing public APK remains unchanged.

- Keep Files navigation intact after cancelling the playlist chooser; refresh collection screens on return.
- Retain folder navigation and transfer progress during rotation; keep a now-playing control bar available while browsing.
- Add bounded MediaStore pages and background search instead of materializing the whole library.
- Encrypt saved network URL payloads and playback checkpoints with Keystore-backed keys; use opaque database identifiers, migrate legacy URLs, and redact URL-bearing titles.
- Restore queue, selected item, position, repeat/shuffle state and explicit streaming MIME type after process loss. Restoration is paused until the user requests playback.
- Stop A–B polling while paused; keep the screen awake only during active playback. Show setting success only after the service accepts the command.
- Stage and verify individual file transfers using SHA-256 read-back before committing and removing an original. Add progress, cancellation and encrypted interrupted-transfer tracking.
- Extend regression coverage for navigation, Room migration, encryption/tampering, retention, checkpoints and failed/cancelled transfers. Add Android playback/PiP/Keystore and external process-recovery test sources.

See VALIDATION.md for executed checks and remaining limitations. This revision preserves versionName 1.0.0, versionCode 1 and the requested SDK levels. It is a debug testing build, not a signed production release.

## 2 November 2026 — performance source workflow (1/3)

- Add off-by-default compatible tunneling, local next-item warming and paused-background decoder release.
- Defer library work until playback starts; preserve existing hardware-first software fallback.
- Add offline production-engine CSV benchmarking, reference generation/verification, a no-build device runner and honest pending-results documentation.
- Keep APK builds/publication deferred; source-only CI now compiles Kotlin and runs JVM tests/lint with an APK-task guard.

## 3 November 2026 — complete benchmark reporting source follow-up

- Require continuous background evidence, playback progress and finite metrics before accepting a 30-minute run.
- Add optional same-device/build/reference baseline comparison, with explicit single-observation and zero-baseline limits.
- Export OS build/position/playing state; normalize benchmark-only audio/video selections and restore the original parameters.
- Expand host parser/CLI tests to 24. Keep APK, device and publication work deferred; feature count remains 1 of 3.

## 3 November 2026 — gesture controls workflow (2/3)

- Add one-finger brightness/volume/seek, independent persistent gesture assignments, held 2× speed and a visible speed badge.
- Add bounded pinch/pan/two-finger reset and subtitle gesture mode with per-file encrypted position/size/sync state.
- Route lock touches before children; require a held Unlock target and restore transient speed on lifecycle cancellation.
- Keep service-owned sleep/A–B controls, native UI, hardware-first decoding and the APK/device/publication deferral.
- Add mapping/persistence/lock/scheduler tests, real-player double-tap instrumentation and a no-build screenshot capture runner. Device evidence remains pending.
