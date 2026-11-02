# TN-Player playback benchmarks

Status on 2 November 2026: **NOT RUN on a connected device. No measured speed,
power or leak-free claim is made for this source change.** This is feature prompt
1 of the owner's 3-prompt integration phase. APK builds, device execution and
publication remain deferred. This environment has no Android SDK/adb/device.

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
3. Pause existing playback; enable the existing Background playback setting;
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
6. Keep original CSV, manifest, `host-ffmpeg.txt`, instrumentation output and this
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
- **Leak evidence limits:** PSS and FD counts accompany every sample; inspect
  trends and repeat with a profiler. Lifecycle counters plus OS wake locks can
  detect unreleased player resources, but cannot prove absence of all native,
  vendor-decoder, Java heap or GPU leaks. Background video without a surface can
  use Media3's dummy surface; this does not represent 30 minutes of HDR display.
- The debug service accepts benchmark commands only from its own package. The
  harness rejects an actively playing user session, suppresses benchmark history
  and checkpoint writes, uses in-memory tuning overrides, and restores the prior
  paused queue/options in `finally`. Process death keeps the previous persisted
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
