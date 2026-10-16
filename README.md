# TN-Player 1.0.0

<img src="branding/TN-Player-logo.png" width="112" alt="TN-Player TN monogram and play icon">

Native Android media-player core built from the TN-Player master specification.

**Package:** `com.tplayer.mediaplayer` · **minSdk:** 21 · **target/compileSdk:** 34

This is a hardened implementation of the playback and local-library core. It does not yet implement the entire master specification. See [FEATURE_STATUS.md](FEATURE_STATUS.md) for exact boundaries and concrete next steps. The tagline is the product vision; it is not a guarantee that every codec or DRM service can be played.

The original logo is in `branding/`. GitHub Actions rebuilds the current APK for the Pages download page; see `CONTENTS.md` and `VALIDATION.md`.

## Build and run

1. Install Android Studio with JDK 17 and Android SDK platform/build tools 34.
2. Extract this folder and open it as a Gradle project. Let Gradle download dependencies.
3. Connect an Android 5.0+ device with USB debugging, or select an emulator.
4. Run the `app` configuration. No Firebase account, API key or backend is needed.

Command line (set `ANDROID_HOME` to your SDK, or create `local.properties` with `sdk.dir=/your/sdk`):

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew installDebug
```

Windows: use `gradlew.bat`. The real Gradle 8.7 wrapper JAR and scripts are included, and its distribution has a pinned SHA-256 checksum.

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
`assembleRelease` produces an unsigned, R8-optimized release APK; configure your own signing key before distribution. Never commit keystores or signing passwords.

The GitHub Actions workflow builds debug/release, runs unit tests/lint, and includes Android 14 emulator navigation, playback, Keystore, PiP and process-recovery checks. Both workflows are committed to this repository and run in GitHub Actions. Check the Actions tab for current results; historical validation is kept separately.

## Branding and GitHub Pages

The original TN icon is integrated into Android and the root Pages page. The Pages workflow builds and tests the current Android source, then publishes a matching debug APK and checksum. GitHub Pages must use **GitHub Actions** as its source in repository Settings → Pages. Workflow results are available in the Actions tab.

Debug APKs may use different signing keys between runs. Android may reject an in-place update; preserve needed data before uninstalling an existing build. Do not commit private development or release keys.

## First use

- **Videos / Music:** grant the corresponding media-library access. If access was permanently denied, use **Settings → App permissions** to change it. Or tap **Open file** to play an individual file through Android's picker without granting library access.
- **Files:** choose a folder. Tap a subfolder to enter; use **Up** to return. Tap ⋮ to copy, move, rename, share, inspect or delete. Copy/move works for individual files; navigate to the destination and tap **Paste here / Move here**. Deletes require confirmation.
- **Streams:** add a direct HTTPS or RTSP media URL. HLS, DASH and SmoothStreaming modules are included. Choose the explicit stream format when the URL has no useful extension. A website address is usually not a media stream. HTTP is deliberately blocked in this build.
- **Player:** standard Media3 controls offer seeking, audio/text tracks and playback settings. **More** adds external SRT/VTT subtitles, speed 0.25–4×, aspect ratio, favorites, bookmarks, A–B repeat, repeat/shuffle, rotation, control lock and a sleep timer. Long press video to unlock controls.
- To use **A–B repeat**, set A, seek forward, then select the option again to set B. The service enforces the loop while playing, including in the background.
- Use **PiP** on a supported Android 8+ phone. Auto PiP is configurable in Settings. Audio playback continues when leaving the player; stop it using player or system media controls.
- The **Library** menu contains favorites, history, playlists and downloads. Add tracks to playlists from an item's ⋮ menu. Playlists can play as a queue.
- Stream downloads support direct HTTPS files through Android Download Manager, not offline HLS/DASH. App downloads are removed on uninstall.
- Seek interval changes apply to keyboard controls immediately and to built-in service controls after the playback service restarts.

## Architecture

- `TNPlayerApplication`: theme setup, debug StrictMode, WorkManager scheduling.
- `MainActivity`: five tabs, collections and settings. Related screens are consolidated instead of creating 22 empty Activities.
- `PlayerActivity`: MediaController/PlayerView client, external VIEW/SEND handling, subtitle picker and PiP.
- `MediaPlaybackService`: a single MediaSessionService owns ExoPlayer, audio focus, media notification, background playback, encrypted queue/position restoration, resume history, A–B loop and sleep timer. No duplicate playback engines or services.
- `MediaDatabase`: Room history, favorites, streams, playlists, playlist items and bookmarks. Foreign-key cascade prevents orphan playlist items.
- `LibraryRepository`: MediaStore keyset pagination and background search off the main thread; permission-aware including Android 14 selected-video access.
- `DocumentOperations`: SAF operations and cancellable IO. Individual transfers stage the destination, verify its bytes with SHA-256 read-back, and then rename it. A move removes the original only after verification. A visible progress/cancel banner survives rotation; an encrypted journal supports unfinished-transfer cleanup.
- `CleanupWorker`: removes disposable app cache exports older than seven days; it never scans or deletes user media.

Settings use app-private SharedPreferences. Media bytes remain in place. Network URI payloads and playback checkpoints are encrypted using Android Keystore-backed keys; Room references those payloads using keyed opaque identifiers. Local URIs and ordinary metadata remain in app-private storage; playback access can be revoked by Android or a provider. Rechoose a file/folder if access expires.

## Privacy and compatibility

There is no Firebase, telemetry, advertising ID collection, background phone monitoring or account requirement. Android backup is disabled. A chosen network stream contacts its server; RTSP itself is generally unencrypted. Embedded username/password credentials are rejected. Network URLs can still contain access tokens, so their stored payloads are encrypted and their query strings are hidden in properties. This does not stop the chosen server from receiving its URL or protect an unlocked device from every threat.

Codec/container support depends on Media3, platform decoders, device capabilities and source. DRM handling, universal software decoders, native FFmpeg, cast and encrypted vault are not implemented. External subtitles currently accept SRT and VTT. Hardware performance targets are goals until measured on representative phones; see [TEST_PLAN.md](TEST_PLAN.md).

Pinned libraries preserve the requested API 21 / compile 34 compatibility baseline. Before a production release, review maintained dependency versions, security fixes, current distribution requirements and the supported Android range together.

## Documents

- [FEATURE_STATUS.md](FEATURE_STATUS.md): implementation matrix and development sequence.
- [SPEC_CORRECTIONS.md](SPEC_CORRECTIONS.md): permission, codec and platform corrections with primary-source links.
- [TEST_PLAN.md](TEST_PLAN.md): automated and physical-device validation.
- [VALIDATION.md](VALIDATION.md): checks actually completed for this delivery.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): dependencies and license review requirements.

## Reliability improvements

Cancelling the playlist chooser preserves Files navigation. Collection screens refresh when revisited. A now-playing bar keeps controls available while browsing. Pausing clears the screen-awake flag and stops A–B polling. Queue, position, MIME type, repeat and shuffle state are checkpointed; playback resumes only after an explicit user action. Abrupt process termination can still lose the latest few seconds. Filesystem permissions and provider behavior remain device-dependent.
