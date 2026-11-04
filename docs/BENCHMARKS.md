# TN-Player playback benchmarks

Status on 3 November 2026: **No connected-device results recorded.** This harness was added in the first integrated feature prompt. See [the current source-only gate](../DEVELOPMENT_PHASE.md). No measured speed, power or leak-free claim is made.

<!-- DEVICE_RESULTS_START -->
Device model: pending. Android version: pending. APK source revision: pending.

| Required measurement | Status |
|---|---|
| 1080p H.264 TTFF / seek / dropped frames / CPU | Pending device run |
| 4K HEVC TTFF / seek / dropped frames / CPU | Pending device run |
| 10-bit HDR TTFF / seek / dropped frames / CPU | Pending device run |
| 30-minute background playback then paused decoder/wake-lock release | Pending device run |
| Matched baseline comparison / performance regressions | Not measured |
<!-- DEVICE_RESULTS_END -->

## Host synthetic

**Device library scan timing is still pending.** These are host JVM results from
[GitHub Actions source-only run 41810642418](https://github.com/Tajoor621/TN-Player/actions/runs/41810642418),
using the Ubuntu runner, Java 17 and Robolectric for preference access. The
benchmark test took **2.247s**. No real media, Android device, APK, MediaStore
provider or filesystem scan was used.

The generated catalogs have 5,000 and 50,000 metadata rows, respectively, with
271 and 503 distinct folders across two volumes, mixed depths, duplicate titles
and missing metadata. Each number is the median wall-clock time of three samples
after one warm-up, excluding fixture construction and preference seeding.
Each sort row measures the first 200 results with that key; two-page rows measure
both scans together. Search measures the first page plus the total match count.
The lookup row measures a batch of 1,000 reads through the real settings code.
Each median must be below 1,500 ms and the timed workload suite below 55 seconds.
The 5,000-row workloads run first in the same JVM; JIT warm-up and scheduling
can make later, larger samples faster. These medians are regression checks,
not a controlled comparison of scaling between the two sizes.

| Workload | 5,000 rows (ms) | 50,000 rows (ms) |
|---|---:|---:|
| Title sort | 18.009 | 9.159 |
| Date sort | 9.864 | 3.955 |
| Played time sort | 15.681 | 9.408 |
| Status sort | 12.479 | 11.385 |
| Length sort | 11.164 | 5.374 |
| Size sort | 8.887 | 9.132 |
| Resolution sort | 14.168 | 5.705 |
| Path sort | 79.052 | 49.913 |
| Capture frame rate sort | 6.265 | 6.969 |
| Type sort | 5.850 | 5.830 |
| Files: first and second pages | 17.346 | 24.425 |
| Folders: first and second pages | 12.417 | 13.223 |
| Whole-catalog search | 8.408 | 11.605 |
| Per-folder sort: 1,000 lookups | 32.118 | 18.480 |

The old collector retained the entire prefix through the requested offset, and
the UI copied and filtered all previously loaded rows on every page. That UI
work was quadratic over a full walk. Next/Previous now replaces the displayed
page. The ordered heap and adapter each hold at most 200 result rows; only the
cursor boundaries are kept for back navigation. The folder scan still needs one
aggregate per distinct folder for exact counts, and each page still scans the
candidate index. A full page-by-page walk repeats that scan; the numbers above do
not claim provider-side keyset queries. Per-folder lookup also no longer copies
the preference map or formats each hash byte through a formatter.

The deep-page JVM test streams 50,000 inputs without constructing a fixture
list and asserts the heap cap after every input. A separate test covers the
folder/file boundary across pages. For a source-only rerun, use the source-review
workflow or run `./gradlew :app:testDebugUnitTest --tests '*CatalogHostBenchmarkTest' -x :software-codecs:verifyNativeCodecs`
with a configured Android SDK. The Gradle HTML test report contains the
`HOST_CATALOG` measurements.

Still needed on a device: real 5,000- and 50,000-file MediaStore query-to-display
timing (cold/warm and local/removable storage), first/deep page and search latency,
peak memory and UI responsiveness while scanning, and the playback measurements
listed above.

## Run after the final build is authorized

1. Build/install matching **debug and androidTest APKs** from the same source;
   set `GITHUB_SHA=$(git rev-parse HEAD)` for a local build so the APK records its
   exact revision. The runner rejects an APK from another revision. Do not do
   this during the current build deferral. Release benchmarks are a remaining
   gap: the debug instrumentation adds overhead and cannot establish optimized
   release performance.
2. Generate offline references: `python3 scripts/generate-benchmark-fixtures.py`.
   See [reference specification](../test-assets/benchmarks/README.md). Files and
   results are gitignored and never packaged in the APK. No download is used.
3. Pause existing playback; clear A-B repeat/sleep timers; select Automatic
   decoders and record any enabled audio effects; enable Background playback and
   video display;
   connect/unlock a physical device, keep it at stable temperature and record
   battery/charging/power-saver/thermal state with the run. Disable networking
   manually if verifying offline operation; the harness opens file URIs only.
4. Run `bash scripts/benchmark.sh --device SERIAL`. It does **not build or install
   APKs**, and does not alter networking. It pushes references, runs the manual
   instrumentation workload for 1,800 seconds, pulls `results.csv`, preserves
   raw output/manifest under `benchmark-results/`, and replaces only the marked
   results block above. Failed/partial results are preserved and return failure.
5. Optional separate matched runs: `--tunnel`, `--preload`, or both. Default
   benchmark tuning is hardware-first, tunnel/preload off, idle cleanup on.
   `--quick` is a 30-second harness smoke check and **never** satisfies the
   30-minute criterion; the runner intentionally returns nonzero for it.
6. To compare with a prior CSV, add `--baseline benchmark-results/OLD/results.csv`.
   The reporter matches device model, Android build fingerprint and exact
   reference hashes/details; the operator must confirm the same physical device
   was used. It shows baseline-to-current TTFF, seek, CPU and
   dropped-frame values. Requested/active tunnel and preload settings are shown
   for both runs. Missing or mismatched evidence is inconclusive and returns
   failure. Percentage changes with a zero baseline are explicitly unavailable.
   These are single observations, not statistical regression or speedup claims.
7. Keep original CSV, manifest, `host-ffmpeg.txt`, instrumentation output and this
   report. Run baseline/candidate on the same physical device, identical hashes,
   settings, power/thermal conditions and multiple repeats. Compare TTFF/seek
   distributions and dropped frames before claiming improvement. A single
   reference observation is insufficient for a statistical speedup claim.

## What is measured

- **TTFF:** service open command immediately before setting the playlist through
  Media3's rendered-first-frame timestamp (elapsed realtime). Includes prepare,
  demux, decode and delivery to the existing PlayerView surface; excludes the
  chooser/UI launch and initial filename/path validation. OS caches are not
  cleared; these are file-open measurements, not cold-start measurements.
- **Seek:** service seek request to the next rendered-first-frame callback after
  position reset. One forward seek to 7 s in each 15 s reference. A command or
  ready callback alone never counts as a rendered frame. Tunneling can change
  frame callback availability on some devices; a timeout is a failed measurement.
- **Dropped frames:** actual Media3 DecoderCounters droppedBufferCount for the
  active video's measured open/seek/play interval. Each reference starts new
  counters; background samples report current interval counters, not a summed
  multi-file total. Tunneling reporting may differ by vendor; compare separately.
- **CPU:** app-process elapsed CPU ms / elapsed wall ms ×100. One fully occupied
  core is 100%; multiple cores can exceed 100%. Includes the real UI, metrics and
  app I/O; excludes remote codec processes, GPU and total system energy. Audio
  volume is zero during benchmarks. This is not a battery-drain measurement.
- **Long session:** loop all three local files in the real service for ≥1,800 s
  with the Activity stopped, checking playing state, progress and errors every
  10 s. Then pause while still backgrounded, wait the opt-in 10 s idle grace and
  require active audio/video decoder initialization-minus-release counts = 0
  plus current app-owned `ExoPlayer:WakeLock` entries in `dumpsys power` = 0.
  Unrecognized/permission-denied dumps are inconclusive, not zero. The test
  fails on stalled playback, missing frames, unmatched source or unknown locks.
  Report acceptance also requires ordered samples starting within 30 s, no
  interval longer than 30 s, at least 1,800 s of samples, playback progress and
  finite, nonnegative CPU/timing fields. A lone late sample never proves a long
  run. Release evidence must follow the final sample within 60 s. OS build
  fingerprint, playing state and position are included in the CSV for inspection.
- **Leak evidence limits:** PSS and FD counts accompany every sample; inspect
  trends and repeat with a profiler. Lifecycle counters plus OS wake locks can
  detect unreleased player resources, but cannot prove absence of all native,
  vendor-decoder, Java heap or GPU leaks. Background video without a surface can
  use Media3's dummy surface; this does not represent 30 minutes of HDR display.
- The debug service accepts benchmark commands only from its own package. The
  harness rejects an actively playing user session, suppresses benchmark history
  and checkpoint writes, uses in-memory tuning overrides, and restores the prior
  paused queue/options in `finally`. Benchmark mode temporarily clears audio/video
  selection overrides and enables both track types, restoring the exact prior
  parameters afterwards; disabled user tracks cannot silently bypass the workload. Process death keeps the previous persisted
  queue and does not persist test tuning. Use a dedicated test device regardless.

## Changes and possible tradeoffs to measure

Hardware remains the default first choice, with the existing finite per-file
native software fallback. New tunneling is opt-in: Media3 selects it only for a
compatible audio/video pair; an actual tunneled decoder failure retries ordinary
playback once before software fallback, and software fallback disables tunneling.
The decoder diagnostics report requested/active tunneling separately.

Opt-in next-local-item warming reads at most 1 MiB after current playback starts
into an 8 MiB LRU cache. It creates no second decoder, does not warm network/DRM
sources, and never writes the playback read path to cache. Cache initialization
and byte reads run on IO. Warming may spend CPU/storage/battery without helping
scattered-keyframe or end-index files; it is not full decoded-frame preloading.
Files modified while queued can invalidate the warmed bytes; use immutable
reference files. Playback falls back to upstream on optional cache failures.

File metadata/grant work stays off the main thread; leaving the library cancels
its scan and fresh library queries wait until first video/audio output (or an
8-second failure timeout). External content providers and filesystem open calls
can still be slow on the Media3 loader thread; no universal <500 ms claim is made.

Opt-in paused-background cleanup stops the player and disables its wake mode after
10 seconds while retaining queue/position; foreground return or play prepares it
again. This trades decoder residency for resume latency. All three new settings
are off by default, preserving ordinary controls and playback behavior.

No new native library or network dependency is added. Media3's existing Apache-2.0
cache/database code is used transitively; decoder licenses remain documented in
[FORMATS.md](FORMATS.md). APK-size delta for these Kotlin/script changes is pending
the final authorized build. No observed regression can be reported without a
matched device baseline; all tradeoffs above remain measurement questions.

## Source follow-up — 3 November 2026

The report validator now checks continuous evidence and finite measurements.
The optional baseline comparison and partial-report preservation are covered by
24 synthetic host tests. These tests do not create device results or demonstrate
performance improvement. The APK/device/publication deferral is unchanged.
