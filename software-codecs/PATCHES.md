# Standalone module changes from Media3 1.4.1

Upstream Java/JNI base: `androidx/media` commit `c35a9d62baec57118ea898e271ac66819399649b`. Original Apache-2.0 headers are preserved.

- FFmpeg experimental video stub excluded; only the real audio renderer is packaged. AV1/VP9 use their separate Media3 extensions.
- VP9 `VpxLibrary` loads `vpxV2JNI` only: libvpx is statically linked into that library instead of shipped as a separate shared object.
- CMake unifies the three JNI builds, binds FFmpeg AArch64 assembly symbols locally, enables 16 KB shared-library alignment and links the Android API 21 toolchain. libgav1 uses standard mutex threading instead of Abseil. FFmpeg x86 assembly is disabled for Android Clang compatibility.
- The standalone Gradle module supplies AndroidX annotations and compile-only checker annotations. Package-private `FfmpegAudioDecoder` explicitly opts into Media3's unstable API; lint remains enabled.
- **Stereo TrueHD:** `createContext` initializes known sample rate/channel layout before opening a TrueHD decoder. Positive values are required. `ffmpegReset` flushes the same context rather than using the old TrueHD recreation branch, which discarded layout and read a freed context. This independently implemented correction follows the reproducible findings in [upstream issue 1843](https://github.com/androidx/media/issues/1843). Tests include stereo and six-channel synthetic signals and seek-after-decode. FFmpeg's own source is unmodified.
- **Silent failure guard:** FFmpeg still tolerates ordinary priming and occasional invalid packets. After 128 consecutive empty/invalid output buffers or 4 MiB of input without output, the wrapper raises a decoder error rather than discarding forever. A short stream that ends without AudioTrack ever advancing is also reported by the owning service. Corrupt EAC3 instrumentation verifies a codec-named notice.

Build configuration, sources and all modifications are included with the distribution in TN-Player-native-sources.tar.xz. `config/FFmpeg-changes.diff` is empty because FFmpeg itself is unmodified; this file describes JNI/Java wrapper adaptations. Per-ABI FFmpeg configs, ELF records and the JNI object file are included for review/relinking. APK assets preserve native licenses and NDK runtime notices. No GPL/nonfree/version3 FFmpeg component, encoder, network client or avformat demuxer is enabled.
