# TN-Player repository audit — 17 October 2026

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
Feature count is 2/3; one owner feature prompt remains before final APK validation.


## Benchmark evidence follow-up — 3 November 2026

A report could previously accept a single late background sample. Continuous
sampling/progress and finite-metric checks now prevent that false acceptance.
The runner/reporter supports a matched baseline CSV, checks OS build and file
hashes and labels observed deltas without claiming statistical regressions.
Benchmark-only track overrides are cleared temporarily so a paused user session
cannot disable the audio/video workload. Twenty-four synthetic host tests pass;
physical results and the final release gates remain deferred. No new dependency,
permission or native library is added. This was a follow-up to the performance prompt; see [the current count](DEVELOPMENT_PHASE.md).


## Performance follow-up — 2 November 2026

Reviewed playback ownership and renderer/decoder selection before this change:
`MediaPlaybackService` constructs the only ExoPlayer, `TnRenderersFactory` orders
platform hardware before source-built extensions, `RecoveryTrackSelector` remaps
failed formats and `PlaybackRecovery` retries a finite number of times per file.
The three requested root status/validation/audit documents were read first.

Source changes add opt-in compatible tunneling plus normal-renderer retry,
bounded IO-only local warming, library-query startup deferral, paused-background
resource cleanup and a manual same-engine CSV benchmark. Three offline reference
specifications and generation/probe checks are present; no large media is in git.
No new native dependency/license is introduced. Cached bytes/CPU usage and
reprepare latency are tradeoffs requiring matched physical-device measurements.

Source-only compilation, 66 JVM tests and lint (0 errors / 88 warnings) passed
at `5620a05`, including the final-review counter/cache code. The output audit
confirmed 0 APKs. Remaining release gates: physical 4K/10-bit HDR frame and
seek measurements, the 30-minute cleanup run, optimized-release runtime check and
APK-size delta. See [the current build gate](DEVELOPMENT_PHASE.md). The harness is source implementation, not evidence of
improved speed, lower battery use or universal leak freedom. No 10/10 rating is
supported yet. See [BENCHMARKS.md](docs/BENCHMARKS.md).


## Assessment

TN-Player is a working native Android testing build with a single Media3 playback service, Room collections, local/SAF browsing, foreground playback, PiP, resume, ZIP operations and useful player controls. It is not a production-complete implementation of the original universal-player specification. The principal remaining release blockers are distribution/toolchain modernization, stable production signing, physical-device testing, dependency/license review and unmeasured performance. A numerical “10/10” would not be supported by this evidence.

Baseline reviewed: `4b14e3c48e5fbc12808998c1b3ab80cb253b3547`. Scope: application and manifest, Kotlin playback/UI/data/security/workers/widgets, resources, build/wrapper configuration, unit/instrumentation coverage, CI/Pages scripts and the feature/privacy/validation documents. Generated APKs and historical evidence were checked as delivery artifacts, not treated as application source. This is a source and workflow review with regression testing, not a penetration-test certification or an exhaustive vulnerability scan of transitive libraries.

## Findings and implementation

| Priority | Finding and consequence | Change / evidence |
|---|---|---|
| High | Move verified the destination but could delete an original modified during verification, including same-length edits. | Re-read and hash the original immediately before deletion. Retain both copies if the original differs, cannot be read or deletion fails. Tests cover same-length mutation and cancellation during recheck. SAF has no universal compare-and-delete transaction: a provider can still modify a source after the final check. Use Copy when another app is actively editing the original. |
| High | Resume history used a 64-event dropping channel independently of disk reads; rapid return to a file could read a stale position. | Ordered, bounded per-file pending history; latest positions coalesce by URI within the existing 200-file retention policy. Lookups wait for writes, failed writes retain pending updates, flush includes history, and Clear history uses the same serialized path. Tests cover an intentionally blocked writer, immediate reopen, burst updates, retry after write failure, successful clear and preservation after a failed clear. |
| High | An explicit file opened during initial service restoration could miss its stored per-file resume point. | Distinguish applying a saved checkpoint from handling a new explicit item. New requests can restore history while startup queue loading remains protected from overwriting them. Existing native resume/force-stop checks remain required. |
| Medium | Downloads displayed only completed files, hiding failures and pending/paused/running transfers. | Downloads now displays state, transferred/total size, percentage where known and actionable Android failure/wait reasons. Periodic refresh runs only while started; unchanged rows and status summaries are not rebound. Completed files can play/share/show details; failures can retry; removal/cancellation requires explicit confirmation. Native fixture checks confirmation and removal. |
| Medium | Adaptive manifests could be downloaded as if they were complete offline media, especially extensionless HLS/DASH sources. | Validate HTTPS URLs and reject adaptive MIME types and known manifest paths. Tests cover extensionless HLS, query strings, SmoothStreaming, invalid schemes and embedded credentials. This is conservative request validation, not proof of the server's eventual response type. |
| Medium | Legacy DownloadManager operations had no separate storage-permission request path. | Android 9 and earlier request storage write permission for queue/retry operations. Pending intent survives Activity rotation in a ViewModel. Denial never queues a new download. The existing declaration remains capped at API 28. |
| Medium | Native CI covered only Android 14. | Android 9/API 28 and Android 14/API 34 matrix, independent artifact names, failure screenshots and existing external force-stop recovery check. OS-specific tests are intentionally skipped below their supported APIs. API 21/22, modern API 35/36 and Samsung hardware remain additional work. |
| Medium | Workflow actions followed movable major-version tags. | Both workflows now pin all seven action dependencies to resolved upstream commit hashes. Monthly grouped/capped Dependabot configuration checks Gradle and Actions dependencies; bot branch pushes are excluded so PRs validate once. This is a review workflow, not automatic acceptance of major upgrades. |
| Medium | The dependency notice file is an incomplete inventory. | Build artifacts now include the resolved release dependency tree. THIRD_PARTY_NOTICES.md still requires transitive license/NOTICE collection; the dependency tree does not itself certify license compliance or vulnerability absence. |

Implementation and publication results are recorded in [VALIDATION.md](VALIDATION.md). The Android package, version, API baseline, Room schema 2, backup format, supplied logo and existing collections are preserved.

## Features worth building next

| Priority | Area | Concrete completion criteria |
|---|---|---|
| Release blocker | Modern Android/toolchain and dependency migration | Source targets API 36 with minSdk 21. Validate edge-to-edge, media focus/foreground behavior, permissions and large-screen behavior on API 35/36; see [migration notes](docs/ANDROID_36.md). |
| Release blocker | Production signing and delivery | Owner-controlled signing outside the repository, monotonic version codes, signed release AAB/APK, upgrade-install tests retaining Room/preferences/grants, recovery/rotation plan and complete notices. Current debug signing may change between runners. |
| High | Downloads backend replacement | Android's security guidance recommends an app-controlled HTTP client plus scheduler instead of DownloadManager. Keep legacy records readable while migrating to bounded streaming writes, HTTPS-only redirect validation, authenticated source storage, atomic finalization, network/storage fault tests and cancel/retry/process-recovery semantics. The current update improves legacy management; it does not replace the system backend. |
| High | External share/import queues | Shared-file display-name lookup already runs off the UI thread. Add slow-provider and rotate-during-resolution tests for the bounded share queue. |
| High | Large libraries and accessibility | Benchmark 50,000 files; preserve search/focus/selection during refresh, reduce whole-list rebinding, optimize repeated completed-download URI queries, test 200% fonts, TalkBack, RTL, keyboard and D-pad. Split MainActivity into feature controllers/screens before further large UI expansion. |
| High | Provider/removable-media reliability | Test Samsung/My Files, DocumentsUI, SD, USB and installed cloud providers; revocation during copy/delete/ZIP, provider renames, read-only/slow storage and full disks. Implement recursive copy/move only with self-descendant guards and a recoverable manifest/journal. |
| High | Measured performance | Establish cold-start, time-to-first-frame, seek latency, peak memory, thermal and battery results with named physical hardware and representative codecs. The original numerical targets have not been demonstrated. |
| Feature | Universal codecs and advanced rendering | Audit a maintained native backend, ABI size/alignment, licenses and codec corpus. Native ASS/SSA loading and bounded subtitle delay exist. Extend styling fidelity and image sidecar support; add audio delay and tested PCM limiting. A platform decoder or FFmpeg audio extension does not establish universal video support. |
| Feature | Network shares, Cast and offline adaptive media | Implement SMB/SFTP/WebDAV clients with encrypted credentials; Cast and DLNA as separate capability-tested paths; authenticated explicit LAN serving for local files. Media3 offline HLS/DASH needs a real cache/download lifecycle and DRM constraints. |
| Feature | Encrypted vault and app lock | Threat model, BiometricPrompt and rate-limited fallback, authenticated file encryption, Keystore-wrapped keys, interrupted-import recovery, metadata protection and secure-screen handling. No fake vault or secure-overwrite guarantee. |
| Feature | Customization and integrations | Five gesture slots are already configurable. Remaining work includes accent/scheduled themes, album-art and folder widgets, dynamic/pinned folder shortcuts and an intentional TV focus layout. Each should retain data on upgrade and have a focused device test. |

## Permissions, privacy and storage review

The manifest uses scoped media access, user-selected SAF grants and the implemented playback permissions. The system-only/fictitious permissions from the original prompt are not added. The playback service rejects untrusted external controllers; FileProvider is non-exported and scoped to app exports. Network URLs/checkpoints use authenticated Keystore-backed encryption; local media references and ordinary metadata remain app-private rather than encrypted vault data. Backup exports are explicitly unencrypted metadata, omit network URLs/media bytes/grants, and use bounded input plus a previewed merge. No telemetry, account requirement or ads were added.

Remaining privacy work includes a user-facing recovery path for invalidated secure keys and a complete production privacy/retention policy. RTSP may be unencrypted; HTTPS source tokens are still sent to the chosen server. No broad root/system-file access or universal secure deletion is promised.

## Primary references checked

- [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk): as checked on 17 October 2026, new phone-app submissions/updates require API 36, with stated exceptions and extension provisions. The API 36 source migration is documented [here](docs/ANDROID_36.md); device and release validation are still open.
- [Android DownloadManager security guidance](https://developer.android.com/privacy-and-security/risks/unsafe-download-manager): recommends an app-owned HTTP-client/scheduling implementation. HTTPS validation and app-specific destinations reduce some exposure but do not remove system-backend risks.
- [DownloadManager reference](https://developer.android.com/reference/android/app/DownloadManager): status, reason, byte counters and removal behavior used by the legacy management screen.

[FEATURE_STATUS.md](FEATURE_STATUS.md) remains the complete master-specification gap matrix. This audit's implemented fixes are not a claim that every remaining feature in that matrix has been completed.


## MX Player comparison follow-up

Source `7b32affb39d66929ff8b0a04d5c0d049395faa42` addresses the previously listed shared-file display-name queries on the UI thread and adds platform codec preferences, bounded timed subtitle imports/rendering/navigation/offset/export, subtitle gestures, native styled-caption loading and Android screen pinning. See [the current comparison](MX_PLAYER_COMPARISON.md) and [validation evidence](VALIDATION.md). Historical findings above describe the audited baseline; universal native decoding, full libass/image formats, encrypted vault and production release work remain open.

Latest follow-up: [18 October 2026 audit and implementation](AUDIT_2026-10-18.md).


## 2 November software decoder follow-up

The former platform-only decoder gap is addressed for AC3/EAC3/DTS/TrueHD audio and AV1/VP9 video with pinned source-built Media3 extensions. Runtime per-track renderer retry, non-blocking codec notices, diagnostics, license/source distribution and optimized-release checks are described in [docs/FORMATS.md](docs/FORMATS.md). Universal codec/profile coverage, hardware listening tests, performance, signing and Play migration remain outside this subset. Current validation evidence is maintained in VALIDATION.md.
