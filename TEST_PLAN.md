# TN-Player test plan

## Automated checks

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest
bash scripts/process-recovery-check.sh
```

JUnit tests cover stream rejection (including credentials, malformed URLs, insecure HTTP and unsupported protocols), resume/completed-media policy, loop boundaries, filename traversal rejection, Room history upsert and playlist foreign-key cascade. Robolectric also smoke-tests MainActivity navigation. Additional tests cover encrypted URL storage across collections, legacy database migration, history retention, authenticated checkpoint tampering, verified transfer failures and cancellation, and the Files/playlist-cancel regression. Instrumentation includes navigation/help, real Keystore, local file transfers, audio continuation, pause/screen-awake and video PiP. The separate process-recovery script force-stops the app between preparation and cold restoration. These are targeted checks, not comprehensive playback certification.

The audit update adds source-mutation/cancellation checks before move deletion; intentionally blocked and failed history-writer checks, rapid reopen and burst-update regressions; direct-download URL/MIME/progress/reason validation; and native completed-download confirmation/removal tests. CI runs the instrumentation and separate force-stop recovery workflow on API 28 and API 34. Feature-specific MediaStore tests skip versions below their required APIs. Dependency trees are attached to the build validation artifacts.

## Real-device acceptance matrix (not executed unless stated in VALIDATION.md)

| Area | Exercise | Expected outcome |
|---|---|---|
| Android 5/API21 | Launch, file picker, audio/video, background | No calls to unavailable PiP APIs; basic playback works |
| Android 8/API26 | PiP, return to app, rotate | No player replacement or restart; controls recover |
| Android 11/API30 | SAF folder, hidden files, SD card | Operate only in granted folder; protected locations remain unavailable |
| Android 13/API33 | Deny/regrant audio and video independently | Correct empty/access UI; file picker still works |
| Android 14/API34 | Selected-video access then full grant | List reflects granted scope; no crash after revocation |
| Samsung phone | Lock screen, Bluetooth, home, remove recent task | Audio keeps playing while allowed; media controls work; stop is respected |
| File faults | Read-only provider, duplicate destination, removed SD card, disk full, copy failure | Clear error; no original deleted after failed copy; partial destination removed where provider permits |
| Files | Copy/move/rename/delete on disposable fixtures | Verify destination bytes/hash; move removes source only after copy; no name traversal |
| Subtitle | UTF-8 SRT/VTT, bad file, unknown format | Valid cues render; unsupported format explains restriction |
| Media corpus | H264/AAC MP4, MP3, FLAC, Opus/WebM, unsupported/corrupt media | Supported playback; unsupported cases show error without crash; record device-specific results |
| Streams | HTTPS MP4, HLS, DASH, SS, RTSP, outage, expired URL | Correct playback or actionable error; no false universal-protocol claim |
| Timeline | Seek, pause/resume, speed, A–B, loop/shuffle | Stable state and positions; service loop works in background |
| Lifecycle | Activity recreation, theme switch, screen off, task removal, force-stop, process death | One engine; no duplicate audio; history checkpoint within 5 s; force-stop does not auto-restart |
| Downloads | Direct HTTPS file, no space, offline, m3u8/mpd | Status/progress/failure shown in app; retry works; cancel/remove confirmation preserves on Keep; adaptive links rejected; completed file can play/share; test denied legacy storage access |
| Collections | Favorites, playlist duplicates, play all, delete list | No duplicate URI rows; deleting playlist retains files/favorites |
| Privacy | Inspect merged manifest and network traffic | No analytics/ads/phone/audio recording requests; traffic only to chosen sources/system components |
| Accessibility | TalkBack, 200% font, keyboard, portrait/landscape | Controls named, reachable, legible; test lock/unlock and PiP independently |

## Performance measurement

Use a release build and record phone model, Android version, codec, resolution, file location, network and screen brightness. Measure cold/warm startup (Macrobenchmark), first-frame latency, seek-to-render latency, idle/playback PSS and a one-hour audio/video battery run. Compare with the prompt's targets only after collecting these results. Network/decoder buffering can exceed the desired 500 ms/100 ms thresholds.
