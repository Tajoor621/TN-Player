# Dependency and license review

The source uses AndroidX (Media3, Room, WorkManager, AppCompat, Activity, Lifecycle, RecyclerView, DocumentFile, Core, Multidex), Material Components, Kotlin/coroutines and their transitive dependencies. Test-only dependencies include JUnit, Robolectric and AndroidX Test. Gradle wrapper scripts/JAR are upstream Gradle 8.7 files.

Most listed AndroidX/Material/Gradle libraries use Apache-2.0 licensing; JUnit uses EPL terms. Do not treat this short inventory as a complete distributable notice bundle. Before publishing, generate the complete resolved dependency/license inventory, retain required license/NOTICE files and verify each transitive dependency. No third-party media, downloaded subtitles, copyrighted demo videos, API keys or Firebase project configuration is included. Native decoder binaries are now built from pinned source as described below.

Upstream references:
- https://github.com/androidx/media
- https://github.com/androidx/androidx
- https://github.com/material-components/material-components-android
- https://github.com/gradle/gradle
- https://github.com/JetBrains/kotlin
- https://github.com/Kotlin/kotlinx.coroutines
- https://github.com/junit-team/junit4
- https://github.com/robolectric/robolectric

Glide 4.16.0 (image/video thumbnail loading, compiler and transitive artifacts): BSD-style / Apache-2.0 components; see https://github.com/bumptech/glide/blob/v4.16.0/LICENSE. Audit the resolved distribution before production release.

CI now saves `app/build/reports/release-dependencies.txt`, resolving the exact release classpath. This tree is a starting point for the complete transitive license/NOTICE review, not a substitute for it. Both workflows pin action commit hashes; `.github/dependabot.yml` schedules reviewable dependency updates.


The TN-Player artwork is user supplied; the original upload and generated emblem adaptation are documented in branding/README.md. Pillow is used only by the optional development-time asset packaging script and is not an Android runtime dependency.

## Native software decoder additions — 2 November 2026

Media3 1.4.1 FFmpeg/VP9/AV1 JNI and Java (Apache-2.0), FFmpeg 6.1.4 (LGPL-2.1-or-later with GPL/nonfree/version3 disabled), libvpx 1.15.2 (BSD-3-Clause and PATENTS grant), libgav1 0.20.0 and cpu_features 0.9.0 (Apache-2.0), and NDK cpu-features glue (BSD-style header). AndroidX annotation 1.8.0 is Apache-2.0; checker-qual 3.33.0 is compile-only MIT annotations. Exact source revisions, license implications, modifications, build/relink instructions and platform restrictions are in [docs/FORMATS.md](docs/FORMATS.md) and software-codecs/native-lock.json. Java/JNI license headers are preserved. The build copies upstream license/NOTICE/PATENTS files into APK assets/native-notices, and publishes full corresponding native sources, flags and JNI object files next to the APK. No FFmpeg executables/encoders/networking/demuxers or external GPL codecs are shipped. Synthetic fixtures are generated for instrumentation only. The build-host ffmpeg executable may have a different license configuration; it is not packaged in TN-Player.
