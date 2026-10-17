# TN-Player repository audit — 17 October 2026

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
| Release blocker | Modern Android/toolchain and dependency migration | Upgrade compile/target and the compatible AGP/Gradle/library set together; test edge-to-edge insets, Android 15/16 media focus/foreground behavior and permissions on API 35/36. Keep the API 21 requirement explicit when evaluating dependencies. Current target 34 is not a new Google Play phone-app submission target. |
| Release blocker | Production signing and delivery | Owner-controlled signing outside the repository, monotonic version codes, signed release AAB/APK, upgrade-install tests retaining Room/preferences/grants, recovery/rotation plan and complete notices. Current debug signing may change between runners. |
| High | Downloads backend replacement | Android's security guidance recommends an app-controlled HTTP client plus scheduler instead of DownloadManager. Keep legacy records readable while migrating to bounded streaming writes, HTTPS-only redirect validation, authenticated source storage, atomic finalization, network/storage fault tests and cancel/retry/process-recovery semantics. The current update improves legacy management; it does not replace the system backend. |
| High | External share/import queues | Move content-provider name/type resolution off the main thread. Use a bounded, rotation-safe request model so a slow provider or a large shared queue cannot stall the UI or replay an intent. Add slow-provider and rotate-during-resolution tests. |
| High | Large libraries and accessibility | Benchmark 50,000 files; preserve search/focus/selection during refresh, reduce whole-list rebinding, optimize repeated completed-download URI queries, test 200% fonts, TalkBack, RTL, keyboard and D-pad. Split MainActivity into feature controllers/screens before further large UI expansion. |
| High | Provider/removable-media reliability | Test Samsung/My Files, DocumentsUI, SD, USB and installed cloud providers; revocation during copy/delete/ZIP, provider renames, read-only/slow storage and full disks. Implement recursive copy/move only with self-descendant guards and a recoverable manifest/journal. |
| High | Measured performance | Establish cold-start, time-to-first-frame, seek latency, peak memory, thermal and battery results with named physical hardware and representative codecs. The original numerical targets have not been demonstrated. |
| Feature | Universal codecs and advanced rendering | Audit a maintained native backend, ABI size/alignment, licenses and codec corpus. Add actual ASS/SSA/image subtitle support, subtitle/audio delay and tested PCM limiting. A platform decoder or FFmpeg audio extension does not establish universal video support. |
| Feature | Network shares, Cast and offline adaptive media | Implement SMB/SFTP/WebDAV clients with encrypted credentials; Cast and DLNA as separate capability-tested paths; authenticated explicit LAN serving for local files. Media3 offline HLS/DASH needs a real cache/download lifecycle and DRM constraints. |
| Feature | Encrypted vault and app lock | Threat model, BiometricPrompt and rate-limited fallback, authenticated file encryption, Keystore-wrapped keys, interrupted-import recovery, metadata protection and secure-screen handling. No fake vault or secure-overwrite guarantee. |
| Feature | Customization and integrations | Gesture mapping presets, accent/scheduled themes, album-art and folder widgets, dynamic/pinned folder shortcuts and an intentional TV focus layout. Each should retain data on upgrade and have a focused device test. |

## Permissions, privacy and storage review

The manifest uses scoped media access, user-selected SAF grants and the implemented playback permissions. The system-only/fictitious permissions from the original prompt are not added. The playback service rejects untrusted external controllers; FileProvider is non-exported and scoped to app exports. Network URLs/checkpoints use authenticated Keystore-backed encryption; local media references and ordinary metadata remain app-private rather than encrypted vault data. Backup exports are explicitly unencrypted metadata, omit network URLs/media bytes/grants, and use bounded input plus a previewed merge. No telemetry, account requirement or ads were added.

Remaining privacy work includes a user-facing recovery path for invalidated secure keys and a complete production privacy/retention policy. RTSP may be unencrypted; HTTPS source tokens are still sent to the chosen server. No broad root/system-file access or universal secure deletion is promised.

## Primary references checked

- [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk): as checked on 17 October 2026, new phone-app submissions/updates require API 36, with stated exceptions and extension provisions. The requested API 34 development baseline was preserved in this change; migration is an outstanding release blocker.
- [Android DownloadManager security guidance](https://developer.android.com/privacy-and-security/risks/unsafe-download-manager): recommends an app-owned HTTP-client/scheduling implementation. HTTPS validation and app-specific destinations reduce some exposure but do not remove system-backend risks.
- [DownloadManager reference](https://developer.android.com/reference/android/app/DownloadManager): status, reason, byte counters and removal behavior used by the legacy management screen.

[FEATURE_STATUS.md](FEATURE_STATUS.md) remains the complete master-specification gap matrix. This audit's implemented fixes are not a claim that every remaining feature in that matrix has been completed.


## MX Player comparison follow-up

Source `7b32affb39d66929ff8b0a04d5c0d049395faa42` addresses the previously listed shared-file display-name queries on the UI thread and adds platform codec preferences, bounded timed subtitle imports/rendering/navigation/offset/export, subtitle gestures, native styled-caption loading and Android screen pinning. See [the current comparison](MX_PLAYER_COMPARISON.md) and [validation evidence](VALIDATION.md). Historical findings above describe the audited baseline; universal native decoding, full libass/image formats, encrypted vault and production release work remain open.
