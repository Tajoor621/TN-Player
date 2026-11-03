# TN-Player integration phase — updated 3 November 2026

Owner instruction: integrate workflows and features in the repository now; defer the final APK build until the owner supplies three further feature prompts.

- **Additional feature prompts integrated: 1 of 3.** A “continue workflow” or status message is not a new feature prompt.
- Feature prompt 1: hardware-first performance tuning, opt-in tunneling/local preloading/paused-background release and the offline benchmark harness are integrated in source. Device measurements remain pending; see docs/BENCHMARKS.md. Two further feature prompts remain.
- 3 November source follow-up: continuous benchmark-evidence validation, finite metrics, optional matched baseline comparison and temporary benchmark track-selection normalization. This continues feature prompt 1; it does not count as a second prompt.
- Decoder source integration is complete. Existing pre-deferral validation evidence and known pending checks are in VALIDATION.md.
- Do not start another APK-producing/device-test/publication run during this phase. Normal repository pushes perform source/script checks, Kotlin compilation, JVM unit tests and lint only; their task graph is checked to reject APK packaging/assembly/install tasks.
- After the three features are integrated, run the full Android workflow with `build_apk=true` against the final main commit; resolve failures, including the optimized-release runtime check. Then run Pages publication with `publish_apk=true` against that same commit. Its gate requires successful source, native, build and both device jobs; a source-only green run cannot authorize publication.
- Keep the published download page aligned with the previously published APK until final delivery. FFmpeg source/license links are inserted atomically with the final matching APK and source archive.
- Update this record as real feature prompts are completed. No final build or distribution has been requested yet for the three future features.
