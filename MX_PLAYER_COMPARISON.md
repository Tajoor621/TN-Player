# MX Player feature comparison — 17 October 2026

The requested package `com.mxtech.videoplayer` could not be retrieved. This comparison uses Amazon Mobile LLC's accessible Google Play listing for **com.mxtech.videoplayer.ad**, updated 25 September 2026:
https://play.google.com/store/apps/details?id=com.mxtech.videoplayer.ad&hl=en_GB

This is an independent implementation of functional behavior. No MX Player source, branding, assets, APK extraction or proprietary HW+ implementation is included. A store description is not an exhaustive behavioral specification; regional and version differences remain possible.

| Published feature | TN-Player implementation | Remaining limits |
| --- | --- | --- |
| Hardware acceleration / HW+ | Hardware-first MediaCodec with configurable platform decoder preference and bundled codec-specific fallback; [decoder matrix](docs/FORMATS.md#current-decoding-policy) | MX's proprietary HW+ and universal software decoding are not implemented; codec availability depends on device |
| Multi-core decoding | Platform decoding plus bundled AV1/VP9 renderers with decoder threading | [No measured performance equivalence](docs/BENCHMARKS.md#changes-and-possible-tradeoffs-to-measure) |
| Pinch zoom, zoom and pan | Pinch, bounded pan and two-finger double-tap reset; mirror/flip preserved | Texture surface/device dependent; gestures disabled during TalkBack touch exploration |
| Subtitle gestures | Explicit subtitle mode: vertical swipe moves text, pinch changes text size; per-file controls and ±0.1 s sync | Timed external imports only for cue navigation; size 50–200% and position 0–40%; turn mode off for video gestures. See docs/GESTURES.md for native cue availability limits |
| Privacy Folder | No PIN folder on `main`; [review branch status](DEVELOPMENT_PHASE.md) | An [encrypted vault](AUDIT.md#features-worth-building-next) is separate work; hiding names is not encryption |
| Kids lock | Existing control lock plus new Android screen-pinning action | User must approve Android's pinning UI; Android controls escape/emergency behavior; not a parental-control password |
| SRT / WebVTT | External load, normalized timed cues, delay ±10 minutes, previous/next cue seek, adjusted-SRT export and removal | UTF-8 and BOM-marked UTF-16; WebVTT cue positioning/styling is not retained by normalization |
| SSA / ASS | New external loading through Media3's native renderer | Media3 styling subset with an embedded-styles toggle; no full libass fonts/animation guarantee; text-only native delay scheduling with bounded history/lookahead; no cue navigation for these tracks |
| MicroDVD | New external `.sub` import | Explicit `{1}{1}25` (or `{0}{0}`) frame-rate header required; no guessed FPS or advanced styling |
| SubViewer 2.0 | New `.sub` timed text import | Basic `hh:mm:ss.xx,hh:mm:ss.xx` timing and `[br]` line breaks |
| MPL2 / PJS / TMPlayer | New `.mpl`, `.pjs`, timed `.txt` import | Plain text and line breaks; TMPlayer ends each cue at next start, final cue at +5 seconds |
| DVD / DVB subtitle tracks | Existing Media3 embedded-track selection | Container/renderer dependent; not a blanket DVD file playback claim |
| SAMI ruby / VobSub / Teletext | Not added | Requires additional parsing/rendering and fixtures; `.sub` binary files are rejected |
| Nearby file transfer | Existing Android content-URI sharing only | No Wi-Fi Direct/hotspot/QR transfer service |
| PiP | Existing Android native PiP with playback actions | Android 8+ and device support; not an overlay window on older Android |
| Video and music playback | Existing local media library, SAF folders, playback service, playlists, history, resume, file operations and streaming | See FEATURE_STATUS.md for protocol/codec and file-operation boundaries |

## New implementation details

- Decoder preference applies through the playback service, reinitializes current playback while retaining queue/position and play/pause state, and persists in settings/backup. Hardware/software classification is the platform/Media3 classification, including its older-device heuristics. Unsupported codecs do not become supported by changing this preference.

- Text imports are bounded to 2 MB, 20,000 cues, 16,384 characters per line and seven days of timestamps. Frame rates and malformed encodings/timestamps are checked. Original files are never rewritten.
- Timed imports render in an app-owned overlay tied to playback position, including paused seeks; native ASS/SSA/TTML imports use Media3. Timed overlays display at most eight simultaneous cues/eight lines/8,192 characters to bound layout work. Adjusted SRT can be exported through Android's save picker. Native caption copies are stored only in app-private cache, named by SHA-256. Cached imports older than seven days are eligible for cleanup. Android may evict cached captions; reload the source if needed.
- Current external-caption timeline/delay survives Activity recreation through a ViewModel. Up to ten recent timed-caption sessions now persist encrypted across closing/reopening and process death, within a 32 MB budget. Original files are not moved. Native ASS/SSA/TTML session persistence remains separate work; caption text is excluded from the plain library backup. The playback queue/history recovery behavior remains unchanged.
- Subtitle import and shared-file display-name queries run off the UI thread. Cancellation retains pending input for the next Activity start; a new media request cancels the old request.
- External caption imports replace the current external subtitle; simultaneous external tracks are not implemented. Embedded tracks remain selectable.

## Validation

New JVM tests check stable decoder preference ordering without dropping fallback candidates and settings/backup round-trip. A decoder device test switches preference while paused, checks queue/position, resumes video and rejects an invalid mode. Subtitle JVM tests exercise text-format conversion, clipping and offset navigation, UTF-16/UTF-8 decoding, oversized input, ambiguous frame rate, malformed/reversed timing and cue-count limits. A device test imports MPL2 into real Media3 playback, checks rendered text, seeks by caption, shifts timestamps by +1000 ms, checks cue visibility and recreates the Activity while paused. Android API 28/34 device checks and release builds are gated; normal pushes run source checks only. See [recorded evidence](VALIDATION.md).

Screen-pinning approval and unpin gestures are owned by Android and still require manual checks on physical OEM devices. No all-features-complete or 10/10 claim is made.

## Additional historical manual cross-check

The older public [MX Player Features manual](https://sites.google.com/site/mxvpen/features) explicitly marks itself outdated. It is useful for further acceptance criteria, not proof that every current regional build has the same behavior.

| Historical feature | TN-Player status / next acceptance criterion |
| --- | --- |
| Variable playback speed, dynamic audio tracks, A–B repeat, aspect selection, keyboard seek, background playback | Existing functional controls; gesture/button mappings differ |
| Decoder-specific hi10p support, NEON optimizations and deinterlacing | AV1 has an ARMv7 NEON requirement and VP9 has a high-bit-depth build; neither establishes H.264 Hi10P or deinterlacing support. See [video codec limits](docs/FORMATS.md#video-codecs) |
| Preview frames while seeking | Not implemented; needs bounded background frame extraction, cache and seek-bar preview UI |
| Toggle elapsed/remaining time | The controller shows elapsed time and duration. Display Settings can toggle the separate remaining-time overlay. |
| Optional browser-link handling | HTTPS/RTSP associations exist; no per-scheme association setting; HTTP remains blocked by default |
| Movie folders, artwork, search, editing, themes and per-file resume | Existing; no scan-time performance guarantee; file-operation limits are in FEATURE_STATUS.md |
| Associated-subtitle tags and new-file tags | Not implemented as a complete sidecar-indexing feature |
| Online subtitle discovery, simultaneous subtitle files | Not implemented; online integration needs provisioned API/auth and multiple tracks need a rendering design |
| Custom TTF/OTF fonts, subtitle playback-rate correction, fade effects, slash-prefixed italics | Speed correction 50–200% and built-in font families/offset are implemented; custom fonts/fade effects/slash-specific styling remain |
| Clock/battery overlay, mouse-wheel handling and joystick-specific controls | Optional clock, battery and remaining-time overlays are implemented; dedicated mouse-wheel/joystick controls remain; ordinary media/keyboard/D-pad seek controls exist |
| Corner-sequence kids unlock and animated touch effects | Not implemented; TN-Player offers control lock and Android screen pinning with different escape behavior |
| Volume amplification beyond 100% | Not implemented; hardware equalizer/bass/virtualizer are not a PCM limiter or volume booster |
| Multiwindow | Resizeable activities exist; OEM-specific Samsung behavior has not been certified |

This checklist deliberately preserves missing items rather than presenting menu labels as completed functionality.
