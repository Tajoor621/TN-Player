# Offline benchmark reference set

Run `python3 scripts/generate-benchmark-fixtures.py` from the repository root on a
host with Python 3.11+, FFmpeg/ffprobe, libx264, libx265 and zscale (libzimg).
This generates three 15-second, 24 fps MP4/AAC files under `media/` and an actual
ffprobe/SHA-256 manifest. It needs no network. Generation is CPU intensive for 4K.
Use `--verify-only --directory PATH` to validate a previously generated set.

The contract is `references.json`: 1080p H.264/8-bit BT.709, 3840×2160 HEVC/8-bit
BT.709, and 1080p HEVC Main10/10-bit BT.2020 PQ with HDR10 mastering metadata.
The HDR source is a synthetic SDR pattern converted into PQ; it exercises the
10-bit/HDR decode path but does not validate artistic HDR fidelity or panel output.
All include a synthetic stereo AAC sine track. Content is generated locally, not
copied from a movie. Encoder binaries are host tooling, not app dependencies.
Record host FFmpeg version with results if comparing re-encoded sets; hashes must
match for before/after comparisons. Do not commit `media/`, APKs or run output.
These files are pushed to app external files for manual benchmarking, never bundled
in the app or instrumentation APK. See docs/BENCHMARKS.md for measurements/limits.
