# Dependency and license review

The source uses AndroidX (Media3, Room, WorkManager, AppCompat, Activity, Lifecycle, RecyclerView, DocumentFile, Core, Multidex), Material Components, Kotlin/coroutines and their transitive dependencies. Test-only dependencies include JUnit, Robolectric and AndroidX Test. Gradle wrapper scripts/JAR are upstream Gradle 8.7 files.

Most listed AndroidX/Material/Gradle libraries use Apache-2.0 licensing; JUnit uses EPL terms. Do not treat this short inventory as a complete distributable notice bundle. Before publishing, generate the complete resolved dependency/license inventory, retain required license/NOTICE files and verify each transitive dependency. No third-party media, FFmpeg binaries, subtitles, logos, copyrighted demo videos, API keys or Firebase project configuration is included.

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
