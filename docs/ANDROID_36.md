# Android API 36 source migration

| Component | Before | After | Reason |
|---|---|---|---|
| App compileSdk / targetSdk | 34 / 34 | 36 / 36 | Compile against and opt in to Android 16 behavior |
| Codec module compileSdk | 34 | 36 | Use the same Android platform stubs as the app |
| App and codec minSdk | 21 | 21 | Keep Android 5.0 compatibility; no maximum SDK is declared |
| Android Gradle Plugin (application and library) | 8.5.2 | 8.9.1 | [Minimum supported AGP for API 36](https://developer.android.com/build/releases/about-agp#api-level-support) |
| Gradle wrapper distribution | 8.7 | 8.11.1 | Required by AGP 8.9; distribution SHA-256 pinned to the [published checksum](https://gradle.org/release-checksums/) |
| CI build tools | 34.0.0 | 35.0.0 plus 34.0.0 | AGP 8.9 default; retain 34.0.0 for the existing release-smoke apksigner path |
| Kotlin and kapt | 1.9.24 | 1.9.24 | No source compilation upgrade needed |
| AndroidX, Media3, Material, Java and NDK | Existing pins | Unchanged | Preserve minimum SDK and native decoder compatibility |

CI provisions platform 36; its source-only and opt-in packaging gates, emulator matrix and release signing are unchanged.

## Runtime compatibility

- The hub owns its status/navigation bars, cutout and IME insets on API 35+. The player uses bar/cutout safe areas in normal mode, cutout clearance in fullscreen and no activity padding in PiP. Earlier Android versions retain their current window layout.
- The player tools bottom sheet opts into Material’s edge-to-edge inset handling on API 35+. Ordinary Material alert dialogs remain floating and use their framework dialog bounds.
- Activity back uses the AndroidX dispatcher with platform predictive back enabled. The hub intercepts only internal navigation and selection; the player intercepts only its control lock. Ordinary activity exits go to the system.
- The foreground service continues to declare `mediaPlayback` and its matching permission. Downloads run in the system DownloadManager, so TN-Player does not declare an app-owned `dataSync` foreground service. Android 15+ restricts audio focus to the top app or a foreground service; verify cold resume from notifications, Bluetooth, PiP and widgets on a target device.
- Video requests both full and selected access on API 34+, keeps the current scope after a permission result, and queries MediaStore only for Android-visible results. Audio and legacy storage retain separate permissions. JVM tests cover full, partial, denied and audio-only grant decisions; the system picker/reselection and revocation lifecycle need a device.
- Android 16 may ignore requested orientation on large displays; landscape and portrait requests remain on phones. No compatibility opt-out was added.

## Native verification

The existing four-ABI decoder artifact from [pre-migration CI](https://github.com/Tajoor621/TN-Player/actions/runs/41427521942) was inspected without building an APK or recompiling native code. All 12 shared objects had `PT_LOAD` alignment `0x4000` in every segment. The native build script now enforces the same threshold on newly linked objects. This verifies the ELF files, not packaged APK ZIP alignment or playback on a 16 KiB device.

## Deprecations

Production changes replace untyped share-intent extras with AndroidX typed accessors, deliver subtitle output as `CueGroup`, keep the default extraction-time subtitle parser without the obsolete toggler, and remove unused FFmpeg renderer constructors that built a context-free audio sink. Media3’s reflective constructor taking an existing `AudioSink` is retained.

| Remaining use | Reason |
|---|---|
| `Virtualizer` type and constructor, `getStrengthSupported()`, and both `setStrength(short)` calls in `AudioEffects.kt` | Retain the existing optional device effect. Android’s [Spatializer guidance](https://developer.android.com/reference/android/media/audiofx/Virtualizer) provides system capability and playback-policy APIs, not a replacement for the per-session strength control. |
| `KeyPairGeneratorSpec.Builder` in `SecretCodec.kt` | Deliberately retained in the guarded API 21–22 compatibility helper; the newer Keystore API is used from 23 |
| `Bundle.get(String)` in `PlaybackBenchmarkTest.kt` | Test-only CSV export of a mixed-type metrics bundle |
| Three-argument Espresso `GeneralClickAction` constructor in `PlayerControlsDeviceTest.kt` | Test-only tap helper; no production dependency upgrade needed |
| Theme `android:statusBarColor` / `android:navigationBarColor` | Legacy appearance before Android 15. Modern activities place their content using insets; the API 35+ sheet requests a transparent navigation region. |
| MediaStore `_data` fallback in catalog/subtitle lookup | Restricted to pre-29 devices, where `relative_path` is unavailable. It is not used on API 36. |

The [initial API 36 source run](https://github.com/Tajoor621/TN-Player/actions/runs/41808815346) compiled both modules and passed 97 JVM tests and lint with 0 errors / 170 warnings; 0 APKs were produced. Compiler output and the pinned Media3 source identified the obsolete subtitle callbacks/toggle, context-free FFmpeg sink, legacy Keystore import, and Virtualizer/test APIs inventoried above.

The [validated migration code](https://github.com/Tajoor621/TN-Player/actions/runs/41809273727) passed application and instrumentation source compilation (including the codec Java module), all 97 JVM tests, and lint with 0 errors and 170 warnings, compared with 88 warnings on the previous toolchain. Lint’s biggest category is 77 `UseKtx` suggestions. CI produced 0 APKs and skipped native rebuilding and emulator jobs. The final compiler reported six Virtualizer warnings covering the calls above and one warning each for the benchmark and Espresso test helpers. The guarded legacy Keystore call remains intentionally suppressed.

Gradle plugin and CI runner diagnostics are tracked separately from Android API calls. No APK, install, device test, or publication was requested for this migration.
