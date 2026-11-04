# TN-Player validation

**Current integration: 2 of 3 additional feature prompts are on `main`.** See [DEVELOPMENT_PHASE.md](DEVELOPMENT_PHASE.md) for the count and the review branch for the third prompt. Dated entries below are ordered oldest first; test results apply only to the revisions shown.

## Decoder source integration — 2 November 2026

Hardware-first defaults remain in the single service-owned Media3 player. Pinned source-built FFmpeg provides AC3/EAC3/DTS/TrueHD audio; libgav1 and libvpx provide separate AV1/VP9 video fallback. A finite recovery policy remaps the failed track group's platform capabilities to the software renderer, retaining the same player, queue, position and play/pause intent. Unsupported/corrupt/no-output tracks generate non-blocking codec-named notices. More → More → Decoder diagnostics and TNPlayerFormats logs report detected extractor/container, tracks, codec strings and selected decoders without source URLs or titles.

The [formats and license document](docs/FORMATS.md) records the current hardware/software/unsupported matrix, exact dependencies, source/relink distribution and limits. Stereo TrueHD layout and seek-reset fixes and the bounded empty-output guard are documented in [PATCHES.md](software-codecs/PATCHES.md). Earlier platform-only pipeline and requested report review are summarized in FORMATS.md.

## Pre-deferral evidence — 2 November 2026

Application source tested: [`67d85a22c6fde055c7e7e3386ecb2d9ce40f83bc`](https://github.com/Tajoor621/TN-Player/commit/67d85a22c6fde055c7e7e3386ecb2d9ce40f83bc). Subsequent commits corrected the release test signing/log command and source-only workflow policy; they are not claimed to have received another full APK validation.

| Check | Recorded result |
| --- | --- |
| [Pre-deferral CI](https://github.com/Tajoor621/TN-Player/actions/runs/41427521942) | Native/build/API 28 jobs passed; API 34 ordinary tests passed, but its extra release-smoke infrastructure failed |
| JVM/Robolectric | **61 tests**, zero failures, zero ignored |
| App lint | **0 errors, 88 warnings**; native module lint passed after explicit unstable-API opt-in |
| Build | `assembleDebug` and optimized `assembleRelease` passed |
| API 28 ordinary instrumentation | **22 reported**, zero failures/errors; three separate-mode tests skipped |
| API 34 ordinary instrumentation | **24 reported**, zero failures/errors; three separate-mode tests skipped |
| Separate process recovery | Preparation and post-force-stop cold restore passed on both APIs |
| Software audio | AC3, EAC3, DTS and stereo/six-channel TrueHD produced non-silent PCM and AudioTrack playback progress with platform decoders excluded; TrueHD seek checked |
| Recovery/error handling | Initialization/runtime renderer-failure injection, unsupported Vorbis notice and corrupt EAC3 notice passed |
| Software video | Native AV1 and VP9 displayed frames on a SurfaceView with platform decoders excluded |
| Native build | All four ABIs built; only AC3/EAC3/DCA/TrueHD FFmpeg decoders enabled; shared libraries use 16 KB load alignment |
| Optimized-release runtime | **Pending final validation.** Initial test expected a keystore at the wrong path; a later attempt built release successfully but ran after the emulator action had shut the device down. Source now uses a temporary matching test signature and runs the smoke test inside the emulator action. No further APK run is started during the owner's deferral. |

JUnit XML supplies the ordinary-suite counts. The two force-stop cases and one release-only case are skipped in the ordinary suite and run separately in their appropriate modes. Tests check actual PCM, position, frame display and named notices; codec availability alone is not counted as successful playback. Physical speaker audibility still requires a listening test on a target device.

## Measured verification-build sizes — 2 November 2026

These measurements are from the completed pre-deferral build, not a newly published/final APK. [Machine-readable report](docs/codec-size-report-2026-11-02.json).

| Artifact | Bytes |
| --- | ---: |
| Universal four-ABI debug APK | 19,229,539 |
| Increase over prior published debug APK | 7,594,470 |
| Optimized unsigned release APK | 11,333,383 |
| ARM64 compressed native libraries in release | 1,680,432 |
| ARM64 uncompressed native libraries | 3,640,672 |

Four ABIs remain supported by the build. Only the requested FFmpeg audio decoders are enabled; encoders, executables, avformat demuxers/networking, GPL/nonfree/version3 components and Abseil are excluded. Native licenses and NDK runtime notices are packaged as assets. The matching full native source/relink archive is present in the CI native-codecs artifact and will accompany the final APK publication.

## Performance integration — 2 November 2026

This was the first integrated feature prompt. No APK build,
instrumented device execution, optimized-release performance measurement or Pages
publication is authorized during this phase. Source compilation/JVM tests/lint
are permitted and the source workflow rejects APK-producing tasks in its graph.
Only this compile-only path excludes `:software-codecs:verifyNativeCodecs` because
JNI binaries are not needed to type-check source. The full APK/native build keeps
its mandatory verification unchanged.

Local checks: `git diff --check`, benchmark Bash/Python syntax and **9 synthetic
report-parser tests passed**. Offline FFmpeg generation and ffprobe verification
passed for all three 15-second references: 1080p H.264/8-bit BT.709, 3840×2160
HEVC/8-bit BT.709 and 1080p HEVC Main10/BT.2020 PQ. Large generated media is ignored,
not committed or packaged. Fixture verification is not playback validation.

New Kotlin policy tests cover idle-release conditions, software/tunnel exclusion
and unknown/current OS wake-lock parsing. Instrumentation is opt-in only and the
long session is skipped by ordinary CI. Source-only CI run [41438006368](https://github.com/Tajoor621/TN-Player/actions/runs/41438006368)
passed application/test Kotlin compilation, JVM tests and lint at
`c8bce7eeac1c90a9d66717452292e34a31e5d677`; native/APK/emulator jobs were skipped.
Final review adds cache-database closure, reused-decoder drop-counter baselines
and two Robolectric metric regression tests. Source-only run
[41439163304](https://github.com/Tajoor621/TN-Player/actions/runs/41439163304) at
`5620a051d9bc4dc00d349f846b812f9e9a592f24` passed **66 JVM tests (0 failures /
0 errors), lint (0 errors / 88 warnings), application/instrumentation Kotlin
compilation and the zero-APK output audit**. The final CSV-only follow-up keeps
explicit failure text from being overwritten by an empty engine error. Earlier APK evidence above does not cover this performance work. **Device TTFF, seek, drops, CPU, 30-minute release and
regressions are pending**, as is final optimized-release validation. See
[docs/BENCHMARKS.md](docs/BENCHMARKS.md).

## Benchmark evidence follow-up — 3 November 2026

Closed a reporting gap that could accept one sample timestamped at 1,800 s as a
continuous run. Acceptance now requires ordered samples no more than 30 s apart,
advancing playback, valid CPU/timing fields, and cleanup after the final sample.
CSV includes build fingerprint, position and playing state. Benchmark selection
temporarily clears audio/video overrides and enables both renderers; prior
selection parameters are restored. Video-display mode must be enabled first.

Optional `--baseline CSV` compares only matching device/Android build/reference
bytes. Single-observation deltas are labelled explicitly; missing/mismatched
baseline evidence cannot produce a passing comparison. Invalid/nonfinite values,
duplicate or gapped samples, unknown locks, stalled playback and incomplete runs
have failure-path coverage. **24 synthetic host tests**, Python/Bash syntax and
`git diff --check` passed locally. Current-source Kotlin/JVM/lint checks run in
[the APK-free source-review job](https://github.com/Tajoor621/TN-Player/actions/workflows/android.yml).
No device measurements, native builds, APKs or publication were performed for
this follow-up. This was a follow-up to the performance prompt and did not add to the feature count.

## Gesture controls workflow — 3 November 2026 (feature prompt 2/3)

Initial gesture source CI [41626695699](https://github.com/Tajoor621/TN-Player/actions/runs/41626695699) at `0d4786811da2edcc91675d97f33bc14fac4cb904` passed **87 JVM tests**, application and instrumentation Kotlin compilation, lint **0 errors / 98 warnings**, and the **0 APK** audit. The ten added warnings are new label translation hints; the follow-up replaces those labels with resources. It also retains exact-ms entry and tests pending-read/edit ordering. Physical screenshots/device execution remain deferred.

Follow-up CI [41627966767](https://github.com/Tajoor621/TN-Player/actions/runs/41627966767) at `60420fc66e1f31f3eb8d9151483e9374b221cb60` passed **88 JVM tests**, Kotlin compilation, lint **0 errors / 88 warnings**, and **0 APKs**. Resource-backed labels removed all ten new warnings. Final source review forwards Media3's subtitle stream-end bound through the text scheduler and adds a subtitle-mode routing unit test plus an overlong-native-caption device test. Actual device execution remains deferred.

Final source CI [41629189632](https://github.com/Tajoor621/TN-Player/actions/runs/41629189632) at `45e5da3` passed 89 JVM tests, application and test Kotlin compilation, and lint (0 errors / 88 warnings), with 0 APKs produced. Gesture captures and the new instrumented tests remain unrun. See [gesture evidence](docs/GESTURES.md#evidence-and-acceptance).

## Current public download

The landing-page Android download still points to [the previously published debug APK](downloads/TN-Player-1.0.0-debug.apk). Its [build metadata](build-info.json) identifies source `18c0426e2e683cb0add4bec86607d78e018677ce`, 11,635,069 bytes, SHA-256 `09a591ff763ca33fa23eda4bf8d20d68f78c04a5fe40cee5f587efb4223dd604`. The checked-in download matches the recorded size and SHA-256. It predates the bundled decoder, performance and gesture work on `main`. The web player has a separate launch link. Its earlier validation is preserved in [the 18 October delivery record](validation/history/supplied-icon-audit-2026-10-18.md).

## Remaining acceptance work

Resolve the third prompt and its [review-branch source checks](https://github.com/Tajoor621/TN-Player/actions/runs/41789815642). Final APK, release runtime, device and publication checks require separate authorization; this documentation review is source-only. Confirm physical speaker/headphone audibility on a device lacking the relevant hardware codecs. Qualify high-resolution/HDR, real DTS-HD/Atmos profiles, multichannel/passthrough and ARM/physical 16 KB devices; no universal-format or bit-perfect/Atmos-object-rendering claim is made. Stable production signing, wider Android/physical-device coverage and the other audit roadmap items remain outside this codec subset.
