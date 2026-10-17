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

The GitHub Actions workflow builds debug/release, runs unit tests/lint, and includes Android 9 and Android 14 emulator navigation, playback, Keystore, PiP and process-recovery checks. Both workflows are committed to this repository and run in GitHub Actions. Check the Actions tab for current results; historical validation is kept separately.

## Branding and GitHub Pages

The original TN icon is integrated into Android and the root Pages page. The Pages workflow builds and tests the current Android source, then publishes a matching debug APK and checksum. Publishing supports this repository's configured **main / root** Pages source. The workflow commits the rebuilt APK, checksum and build manifest to that source, requests a Pages build, and verifies the public APK against its SHA-256 before reporting success. Workflow results are available in the Actions tab.

Debug APKs may use different signing keys between runs. Android may reject an in-place update; preserve needed data before uninstalling an existing build. Do not commit private development or release keys.

## TN-Player feature upgrade

- **Settings → Export/Restore library backup:** versioned JSON for local favorites, playlists, bookmarks and UI preferences, with a restore preview. Merge preserves existing collections. The export is unencrypted metadata; network sources/credentials, media bytes and Android access grants are excluded. Limits: 2 MB, 10,000 entries and 200 playlists. Rechoose files when access is unavailable after import.
- **Library:** debounced media-change refresh, local video thumbnails and MediaStore album art. Glide caches are bounded to 16 MB memory / 64 MB disk; no network URLs are used for thumbnails.
- **Player → More:** persistent subtitle size/color/typeface/background/position and aspect ratio. Audio enhancements expose the actual device EQ band count and supported bass/virtualizer controls; unavailable outputs are reported. There is no claim of a universal 10-band DSP or volume boost.
- **Gestures:** double-tap either side to seek. Two-finger horizontal swipe seeks; vertical left adjusts volume and vertical right adjusts window brightness. Pinch zooms 1–3×; two-finger drag pans when zoomed. More → Reset zoom returns to 1×. Existing buttons remain available. Disable gestures in Settings; touch exploration, control lock and PiP suppress gestures.
- **Files → item options:** create ZIP from a file/folder or extract ZIP to a new sibling folder. Originals are retained; no overwrite. ZIP operations are cancellable and bounded to 512 MB, 5,000 entries and 24 directory levels. Extraction rejects unsafe/colliding paths; creation checks written entry CRCs. Process interruption may leave a hidden `.tn-archive-*` staging result to review.
- **Share to TN-Player:** multiple media files become a queue (maximum 1,000 entries). Temporary source permissions remain controlled by Android.
- **Home screen:** add the TN-Player playback widget using your launcher's widget picker. The title opens the app; buttons control the service and can resume a saved queue. Long-press the launcher icon for Resume, Files or New stream. PiP has custom transport actions.
- **Theme:** Your supplied black TN and blue/cyan play identity, light/dark/system and an AMOLED option. Package, version and Room schema remain unchanged.

## Folder browser and reference-style controls

- Videos and Music start in **Folders** mode, with media counts and full storage paths. Tap a folder to open its media; **Up** or Back returns to the previous level. Same-named folders on different storage volumes stay separate.
- **View & layout** opens a draft panel: **All folders** browses the indexed directory tree, **Files** shows a flat media list, and **Folders** lists immediate media-containing directories. Choose **List / Grid**, a sort and ascending/descending order, then **Done**. Cancel leaves stored preferences unchanged.
- Sort by title, modified date, last played, playback status, length, size, resolution, path, indexed capture frame rate or type. Fields and Advanced expand to show metadata toggles, hidden indexed entries and a minimum-duration filter. Missing capture-rate values are unknown; this is not a measured encoded frame rate. Playback status uses the retained 200-item local history.
- Folder counts include accessible indexed videos or audio for the active tab, after hidden/minimum-duration filters. **All folders** counts descendants; **Folders** counts direct media. Empty/unindexed/.nomedia and protected directories may not appear. Use **Files → Choose folder** for document-provider storage, SD/USB or an installed cloud provider.
- Indexed scanning runs on IO with cancellation. It retains folder summaries and a bounded page prefix for global sorting, displays 200 rows per page, and debounces search/change refresh. Large-library throughput is not yet benchmarked.
- **Settings** now opens a **Me** hub with shortcut tiles, App Theme, full Settings, Custom Pop-up Play and View & layout. Full playback/preferences/backup controls remain under Settings. Cloud Drive uses Android's installed document providers; File Transfer means local file copy/move. Private Folder, Local Network and Recycle Bin are visibly unavailable, rather than fake functional tools. No MX Player assets or branding are included.

## File selection and expanded player tools

- Use **Select files** or long-press a file. Tap files/check boxes one by one; the counter shows selected/shown files. **Select all** selects all matching files in the current media folder or flat Files view, across display pages; up to 1,000 files per action. Narrow the search when more match. Folders are excluded from batch selection. Changing browser location clears selection.
- **Delete** names the selection and requires confirmation. Android 11+ indexed-media deletion also uses Android's deletion approval; Android 10 handles per-file recoverable approval, and older indexed storage may require write permission. SAF operations use the folder's grant. No recycle bin or rollback is implied. Cancellation stops subsequent operations; completed deletions remain deleted. Results report failures/unverified items. Deletion state survives rotation; a killed process never silently replays it.
- **Share selected** grants read access to selected content URIs through Android's sharesheet. File-scheme sharing requires rechoosing through Android's picker.
- **Properties / Information** reads each local file's name, URI/path, size, type, modified time, available duration/resolution/bitrate/track metadata, last played and saved resume position. Unsupported provider metadata is reported unavailable; network streams are not downloaded for inspection. Resume indicators also appear on previously played library/SAF files.
- **Player → More** opens a dark tools grid: Audio Track, Subtitle, Playing Queue, Aspect Ratio, Display Settings, Information, Share, Tutorial and More. Existing advanced controls, bookmarks, A–B repeat, sleep timer, favorites and audio enhancements remain available.
- **Customise items** chooses shortcut buttons, including rotation, speed, background play, mute, EQ/effects, sleep, loop/shuffle, A–B, night display, screenshot, mirror and vertical flip. **Show shortcuts** hides only the quick row; Tools retains access. Video display can disable the video track while audio continues. Night display dims the texture only. Mirror/flip preserve pinch zoom.
- **Rotate** offers device-controlled rotation, portrait or landscape. Playback stays in the foreground service through orientation changes. **Full screen** toggles system bars; swipe from an edge to reveal them. **Lock** has a visible accessible Unlock button as well as the long-press fallback.
- **Screenshot** exports an available decoded frame via a user-chosen PNG destination, up to 1,920 pixels on its longest edge. UI, mirror/flip/night rendering transforms are not burned into the exported frame; protected/unavailable video may prevent capture.
- Resume/history and encrypted queue checkpoints remain in place. Queue transitions with unknown duration no longer overwrite a known duration. Background play remains on by default; turning it off pauses when the player leaves the foreground.

## Repository audit improvements

- Read the [professional audit and prioritized roadmap](AUDIT.md) for findings, fixes and release blockers.
- Move operations recheck the source hash before deleting it; a changed/unreadable source leaves both copies intact. The final provider delete is not a universal atomic compare-and-delete operation.
- Per-file resume lookups now wait for pending history writes. Pending updates coalesce by file within the existing 200-file history policy; clear/flush use that same ordered writer.
- **Library → Downloads** shows queued/running/paused/completed/failed transfers, bytes/percentage and failure reasons. Failed downloads can retry. Cancel/remove requires confirmation and removes the record plus downloaded/partial bytes. Completed entries offer Play, Share and Properties. The list refreshes while visible and displays up to 500 records.
- Direct HTTPS requests reject known adaptive manifest paths and MIME types; offline HLS/DASH remains unfinished. Android 9 and earlier request the storage permission needed for legacy downloads. Android DownloadManager remains the backend; migration to an app-controlled download worker is recommended in the audit.
- Both CI workflows pin action commit hashes; monthly Dependabot updates and resolved release-dependency artifacts support maintenance. Native checks now run on API 28 and API 34.

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
- `MediaCatalog`: volume-aware media folders, global metadata sorting, 200-row display pages and cancellable scanning off the main thread; permission-aware including Android 14 selected-video access.
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
