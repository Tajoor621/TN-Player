# TN-Player gesture controls — source integration, 3 November 2026

This is feature prompt **2 of 3**. Existing Views/Material/Media3 PlayerView are
retained. No Compose, second player, dependency, native library or permission is
added. Source compilation/JVM tests/lint are allowed; APK/device builds and actual
screenshots/recordings remain deferred under the owner's three-prompt instruction.

## Controls

| Input | Fresh-install default | Customization / feedback |
|---|---|---|
| Single tap on video | Show/hide controls | Assign any fixed action below |
| Double-tap left / right | −10 s / +10 s | Independent assignments; seek clamps to media duration |
| Vertical swipe left / right | Window brightness / music volume | Independent assignments; live percentage overlay |
| Horizontal swipe | Preview target time, seek on release | Source must be seekable; cancel does not seek |
| Pinch | Zoom 1–3× | Drag while zoomed to pan; bounded to image |
| Two-finger double-tap | Reset zoom/pan | Preserves mirror/vertical flip settings |
| Long press while playing | 2× while held | Prior speed restored on up, cancel, second pointer, item change or Activity stop |
| Subtitle mode: vertical swipe / pinch | Caption position / text size | Explicit mode prevents brightness/zoom conflicts; per-file state |
| Subtitle timing: earlier / later | −0.1 s / +0.1 s per tap | Reset and exact-ms entry available; per-file delay saved locally |
| Lock button | Block all in-player touch controls | Hold the visible Unlock target for 550 ms; a tap cannot unlock |
| Sleep timer / A–B repeat | Existing service-owned tools | Tools → More; timer Off/15/30/60 min; B ≥ A+0.5 s on seekable media |

The persistent speed badge shows current speed and subtitle-mode status. Normal
buttons/seek bar retain their own touch handling; gestures start only on the video
surface. Nonseekable/live media reports that seek is unavailable.

Settings → Gesture controls and Player → More → More → Gesture controls share
the same native panel. Each of five tap/vertical slots can be assigned: controls,
play/pause, seek by direction, explicit seek back/forward 10 s, brightness, volume,
speed or none. Tap brightness/volume uses a 5% step; tap speed uses 0.25×; single
tap direction is forward/up, double tap direction follows the half. Continuous
swipes use drag distance. Controls/play-pause mapped to a swipe triggers once at
threshold crossing; None does nothing. Speed remains 0.25–4×. Explicit legacy
swipe-side/double-tap presets are preserved unless reassigned; Reset restores the
new defaults. New assignments survive restarts and library preference backup.

All application gesture distances/timings/steps/ranges are in
`ui/GesturePolicy.kt` (`GestureThresholds`). Android's own navigation and accessible
button long-click thresholds remain OS controlled. No exclusion rectangle steals
Android back/home gestures. Touch exploration disables surface gestures while
normal accessible buttons remain available. Unlock exposes an accessibility
long-click action. Controls have at least 48 dp touch targets.

## Subtitle state and limits

Offset, size and position are saved per media ID in app-private encrypted atomic
records, with opaque keyed filenames and a 200-file retention limit. Saves are
serialized/coalesced; ordered reads see pending edits. Imported caption documents
retain their existing separate ten-session limit. Sources/subtitle files are not
modified. Global style is the fallback for a file with no saved state. Position is
0–40%, text scale 50–200%; styles with fixed coordinates may clip when moved.
Native embedded font sizes are preserved until the user pinches or selects a size
for that file. Bitmap captions retain their bitmap resolution rather than
acquiring a text font.

Timed imports apply delay to their cue lookup. Embedded/native Media3 text uses
a text-only scheduler: decode ahead for negative offsets and retain bounded cue
history for positive offsets; A/V clocks and decoder selection are unchanged.
History is limited to 2,048 groups / an 8 MiB budget (one currently needed large
bitmap can exceed that budget). Very large offsets, unavailable decoder lookahead,
and a seek into text samples not retained/available to the extractor can leave
captions blank until the needed cues are decoded. Range is ±600 s, not a promise
that every format can reconstruct ten minutes of captions after an arbitrary seek.
Native ASS/TTML, image cues, seek/reset and large offsets require corpus/device
validation during the final phase; full libass fidelity is not claimed.

## System gesture conflicts

- Starts within max(24 dp, reported system gesture/bar inset) of any edge pass to
  Android/normal child handling. Start brightness/volume swipes inside that band.
- Home, recents, status shade, back and OEM edge panels may cancel an in-progress
  gesture. Cancellation restores held speed and does not commit a seek. OEM
  unreported edge panels may need an even wider constant on that device.
- Lock is an in-app touch lock, not a device kiosk. Android navigation, physical
  volume, notification/headset controls and system accessibility remain OS/session
  functions. Back/keyboard commands inside the player do not bypass the lock.
- In PiP, system controls own the surface. Multi-finger accessibility and OEM
  screenshot gestures may win; touch exploration disables the app recognizer.

## Evidence and acceptance

| Evidence | State |
|---|---|
| Unit mapping for all five customizable slots | Added; source-only CI executes it |
| Settings instance + backup round trip | Added |
| Held speed/cancel, lock routing and subtitle state/scheduling tests | Added |
| Instrumented double-tap seek on the real service/PlayerView | Added, execution deferred |
| Screenshots of each gesture | Capture suite added; **not captured yet** |
| Physical Android 10+ edge/back/TalkBack test | Deferred |

After final build authorization, install matching debug/androidTest APKs on a
dedicated unlocked test device, then run:

```sh
bash scripts/capture-player-gestures.sh --device SERIAL
```

The script never builds/installs APKs. It checks the installed source revision,
runs the manual capture method, pulls evidence and preserves instrumentation output
under ignored `gesture-evidence/`. The suite captures double taps, single tap,
brightness, volume, seek preview, held speed, pinch, pan, reset, subtitle position,
size/sync, lock and unlock. The ordinary device test separately asserts ±10 s
seeking without enabling the screenshot gallery. Screenshots must be reviewed on
the actual device; test source or generated mockups are not runtime evidence.
Use the final Android workflow for normal instrumented tests; do not dispatch it
while the owner's build deferral remains in force.

Initial source validation: [41626695699](https://github.com/Tajoor621/TN-Player/actions/runs/41626695699) passed 87 JVM tests and application/instrumentation compilation, with 0 lint errors and 0 APKs. Follow-up retains exact-ms subtitle entry, adds a pending-edit/read-race test, and moves new labels into resources to remove the ten added translation warnings. Device evidence is still pending.
