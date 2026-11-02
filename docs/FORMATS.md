# TN-Player formats and decoder paths

## Pipeline reviewed before this change — 2 November 2026

Read first: FEATURE_STATUS.md, VALIDATION.md and AUDIT.md. Baseline `c39e9ff` uses one service-owned Media3 1.4.1 ExoPlayer; MediaController drives the player screen. Media3 extractors/HLS/DASH/SS/RTSP modules demux streams. DefaultRenderersFactory uses MediaCodec, with compatible initialization fallback. The saved decoder preference orders platform hardware/software candidates only. No bundled software extension or runtime cross-renderer retry existed; the player displayed a blocking generic error dialog. Existing regression fixtures contain H.264 in MP4 and test seek, PiP, background playback and resume.

## Delivery phase

**Source integration only; final APK publication is deferred.** On 2 November the owner requested three additional feature prompts before the final APK build. See [DEVELOPMENT_PHASE.md](../DEVELOPMENT_PHASE.md). The public download remains the prior 18 October build and does not contain these decoder additions. Pre-deferral test builds and corresponding native sources are available in the [recorded CI artifacts](https://github.com/Tajoor621/TN-Player/actions/runs/41427521942). The later final publication workflow will place the source archive beside the new APK; it has not done so yet.

## Current decoding policy

1. Hardware MediaCodec decoders are ordered first in Automatic and Prefer hardware modes. An explicit Prefer platform software setting is retained. Extension mode is ON, not PREFER: bundled software decoders are fallback renderers.
2. Media3 tries compatible MediaCodec alternatives during initialization. If the platform cannot support a format at all, its matching extension is selected directly.
3. For an eligible fatal initialization/decoding error from the platform renderer, the service gates only that selected track group's platform-renderer capabilities and invalidates track selection so Media3 remaps it to the extension and prepares the same player/queue at its retained position (where seekable). A finite policy allows one software retry per track, up to 32 per media item. Other track groups and the other track type retain their platform renderer. Merely disabling the old renderer would not remap Media3's track group; capability remapping is essential. The software decoder may restart at the preceding keyframe. Changing to a different media file or an explicit decoder-preference change resets the policy; source/network/DRM errors and failed software renderers are not retried in a loop.
4. Unsupported tracks, including an omitted audio track in otherwise playable video, produce a non-blocking codec-named notice. A failed software decoder also names the codec. No second player, download transcoding or server is used.
5. More → More → **Decoder diagnostics** shows detected extractor/container, all advertised tracks, MIME/codec strings, dimensions/sample rate/channels, support/selection, DRM presence, active decoder names and native versions. `TNPlayerFormats` log entries contain the same bounded format data, without URLs, tokens, filenames or media titles. Adaptive segment containers can be undetected; declared MIME/filename is not presented as sniffed proof.

## Audio codecs

| Codec | Platform path | Bundled software fallback | Limits |
| --- | --- | --- | --- |
| AC3 | MediaCodec/passthrough where supported | FFmpeg `ac3` | Decoded PCM; output channel count depends on Android sink/device |
| EAC3 / EAC3 JOC | MediaCodec/passthrough where supported | FFmpeg `eac3` | EAC3 channel bed; no Atmos object-renderer claim |
| DTS / DTS-HD | MediaCodec/passthrough where supported | FFmpeg `dca` | Supported core/extensions depend on FFmpeg and input; not every DTS profile certified |
| TrueHD | MediaCodec/passthrough where supported | FFmpeg `truehd` | PCM channel bed; no Atmos object-renderer claim |
| AAC, MP3, Opus, Vorbis, FLAC, PCM, AMR | Device MediaCodec/Media3 PCM path | Not added in this build | Remain device/profile dependent; no extra FFmpeg decoders enabled |
| WMA, APE, other unlisted codecs | Device support only if exposed and demuxed | Unsupported by bundled extension | No universal-codec claim |

The software audio path currently outputs PCM16 by default. Bit-perfect 24-bit output and Atmos object rendering are not claimed.

## Video codecs

| Codec | Preferred path | Bundled software fallback | Limits |
| --- | --- | --- | --- |
| H.264 / AVC | Hardware MediaCodec | Platform software only | MP4/H.264 regression coverage retained; FFmpeg video renderer is not included |
| AV1 | Hardware MediaCodec | Media3 Libgav1VideoRenderer + libgav1 0.20.0 | CPU/resolution/profile dependent; not secure DRM/HDR tone mapping certification |
| VP9 | Hardware MediaCodec | Media3 LibvpxVideoRenderer + libvpx 1.15.2 | VP9 high-bit-depth build enabled; CPU/resolution dependent |
| HEVC, VP8, MPEG-4 Part 2, H.263 | Compatible platform decoder | Platform software only, if present | Device dependent |
| VC-1, MPEG-2, Theora, VVC and other unlisted codecs | Device decoder if exposed | No bundled software path | May be unsupported even when container opens |

Software video uses Media3's default ANativeWindow/SurfaceView path, preserving hardware playback surfaces. libgav1's ARMv7 JNI path requires NEON; the library can be unavailable on non-NEON ARMv7 devices. Protected tracks are not sent to unprotected extensions. Smooth high-resolution software playback, Dolby Vision and all HDR combinations are not promised.

## Containers and protocols

Media3 handles demuxing; FFmpeg avformat, networking, muxers and demuxers are deliberately **not** packaged. Opening a container does not guarantee support for every codec/profile inside it.

| Container/source | Demux path | Qualification |
| --- | --- | --- |
| MP4 / M4A / MOV / fragmented MP4 | Media3 MP4 extractors | H.264/MP4 regression fixtures; other codecs follow tables above |
| Matroska / MKV / MKA / WebM | Media3 Matroska extractor | Synthetic AC3/EAC3/DTS/TrueHD (stereo and six-channel) and AV1/VP9 fixtures |
| MPEG-TS, MPEG-PS, ADTS, AC3 elementary, MP3, Ogg, WAV, FLAC, AMR, FLV, AVI | Media3 corresponding extractor, where supported | Container-specific codec restrictions; not a full physical-device corpus |
| HLS, DASH, SmoothStreaming | Existing Media3 streaming modules | Segment codecs follow decoder tables; DRM restrictions still apply |
| RTSP/RTP | Existing Media3 RTSP module | Codec/transport restrictions remain; RTSP may be unencrypted |
| ASF/WMV, RealMedia, VOB variants, MXF and unlisted containers | No newly added demuxer | Unsupported unless an existing Media3 path can recognize the particular source |
| RTMP, SMB, FTP/SFTP, NFS, DLNA/WebDAV | No new protocol client | Outside this change |

## Dependencies, licenses and source/relink distribution

Pinned source IDs and enabled decoders: [`software-codecs/native-lock.json`](../software-codecs/native-lock.json). Vendored JNI/Java remains aligned with Media3 1.4.1; no unverified third-party AAR repository is used.

| Added code/library | License | Distribution implication |
| --- | --- | --- |
| Media3 FFmpeg, VP9 and AV1 extension Java/JNI, 1.4.1 | Apache-2.0 | Preserve headers/license; note patches. FFmpeg experimental video stub excluded; VP9 loader changed to load one statically linked JNI library |
| FFmpeg 6.1.4: avcodec, avutil, swresample | LGPL-2.1-or-later | Only `ac3,eac3,dca,truehd`; GPL, nonfree and version3 disabled. Retain exact corresponding source, build flags, changes and relink material. No x264/x265/FDK-AAC or FFmpeg executable is packaged |
| libvpx 1.15.2 | BSD-3-Clause plus upstream PATENTS grant | Retain copyright/license/PATENTS; VP9 decoder/high-bit-depth only; no VP8 or encoder |
| libgav1 0.20.0 | Apache-2.0 | Preserve license and notices; tests/examples off, standard mutex threadpool avoids Abseil |
| google/cpu_features 0.9.0 | Apache-2.0 | Retain license/notices; CPU feature detection for AV1 |
| Android NDK libc++/libc++abi/compiler runtime | LLVM/Android component licenses, including Apache-2.0 WITH LLVM-exception | Statically linked C++ runtime; pinned NDK NOTICE and NOTICE.toolchain are retained in assets and source archive |
| Android NDK cpu-features glue | Android BSD-style notice in source | Source retained via pinned NDK; JNI archive includes the source used for VP9 CPU detection |
| AndroidX annotation 1.8.0 | Apache-2.0 | Explicit annotation dependency for the standalone Java module |
| checker-qual 3.33.0 | MIT | Compile-only annotations, not a new native/runtime decoder |

FFmpeg's static archives are linked with the open JNI wrapper into **replaceable** `libffmpegJNI.so`; the Android application loads that shared library. Publication includes the exact full sources, vendored wrapper/build scripts, per-ABI config files and wrapper object file, sufficient to rebuild/relink the wrapper and replace it in a re-signed APK. The final publication workflow will serve `downloads/TN-Player-native-sources.tar.xz` next to the matching APK, with both hashes in build-info.json. Until that deferred publication, the archive is in the native-codecs artifact of the recorded CI runs. Native license text is packaged in `assets/native-notices`, and About and the download page credit FFmpeg and link sources. To modify/relink, extract the archive at a fresh project root, set `ANDROID_HOME` to a matching SDK/NDK installation, and run `CODEC_SOURCE_MODE=local bash scripts/build-native-codecs.sh`; local mode preserves the provided/modified sources without fetching or resetting them. Replace the appropriate `lib/<ABI>/libffmpegJNI.so` in a copy of the APK, zipalign and sign it with your own testing key; it will not carry the original publisher signature. No restriction on reverse engineering for LGPL modification/debugging is added. This is a technical compliance record, not legal certification; codec patent/trademark/commercial distribution obligations must be reviewed for the destination markets separately from source licenses.

## Build, size and evidence

Install Java 17, Android platform 34/build-tools 34, NDK **27.2.12479018**, CMake **3.22.1**, yasm and ffmpeg (the latter only generates synthetic test fixtures). Run:

```sh
bash scripts/build-native-codecs.sh
bash ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Four ABIs are retained: armeabi-v7a, arm64-v8a, x86 and x86_64. No native executable or encoder ships in the APK. Native wrapper modifications, including stereo TrueHD layout initialization, seek-reset safety and bounded no-output errors, are recorded in [PATCHES.md](../software-codecs/PATCHES.md). Shared objects are linked for 16 KB maximum page size; ELF inspection records are in the source archive. FFmpeg x86 assembly is disabled for Android Clang register compatibility; ARM builds keep optimized assembly. The source tarball and synthetic fixtures are **not** bundled in the application. A release AAB can deliver only the device ABI; the testing APK remains universal.

The [pre-deferral measured report](codec-size-report-2026-11-02.json) records debug APK 19,229,539 bytes (+7,594,470 over the prior published APK) and optimized unsigned release APK 11,333,383 bytes, measured from source `67d85a22c6fde055c7e7e3386ecb2d9ce40f83bc`. ARM64 native libraries add 1,680,432 compressed bytes in the release APK. These are verification builds, not a final publication. CI records compressed/uncompressed per-ABI native bytes and debug/release APK sizes in `codec-size-report.json`. The previous verified debug APK was 11,635,069 bytes; this is a historical comparison rather than a reproducible same-toolchain control. See [VALIDATION.md](../VALIDATION.md) for actual run results and measured increase after this revision is validated.

Tests require real JNI loading; the Gradle build fails if any expected ABI/library is missing. Tests generate original sine waves/test patterns, force absence of platform codecs, exercise stereo/six-channel TrueHD and seek, verify non-silent PCM before the real AudioTrack sink and playback-position advancement, inject initialization/runtime platform-renderer failures, exercise unsupported/corrupt-codec notices and render AV1/VP9 frames on a SurfaceView. Automated PCM/AudioTrack checks cannot certify that a physical phone's speaker is audible; a listening test on the target Samsung/other hardware remains necessary. Damaged-profile, multichannel/passthrough, high-resolution, power/thermal and physical 16 KB-device qualification remain gaps.

Primary references: [Media3 software extension instructions](https://github.com/androidx/media/tree/1.4.1/libraries), [supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats), [FFmpeg licensing](https://ffmpeg.org/legal.html), [libvpx](https://chromium.googlesource.com/webm/libvpx/), [libgav1](https://chromium.googlesource.com/codecs/libgav1/), [cpu_features](https://github.com/google/cpu_features).
