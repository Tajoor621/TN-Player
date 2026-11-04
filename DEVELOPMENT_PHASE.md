# TN-Player integration phase — updated 3 November 2026

Owner instruction: integrate workflows and features in the repository now; defer the final APK build until the owner supplies three further feature prompts.

- **Additional feature prompts integrated: 2 of 3.** A “continue workflow” or status message is not a new feature prompt.
- Feature prompt 1: hardware-first performance tuning, opt-in tunneling/local preloading/paused-background release and the offline benchmark harness are integrated in source. Device measurements remain pending; see docs/BENCHMARKS.md. One further feature prompt remains.
- 3 November source follow-up: continuous benchmark-evidence validation, finite metrics, optional matched baseline comparison and temporary benchmark track-selection normalization. This continues feature prompt 1; it does not count as a second prompt.
- Feature prompt 2: one-finger/custom gestures, held speed, subtitle position/size/per-file sync, long-hold unlock and gesture evidence tests/capture runner. Source is integrated; actual gesture screenshots/device checks remain deferred. See docs/GESTURES.md.
- The scoped third-prompt implementation is in source review on this branch; see [Library](docs/LIBRARY.md). The earlier, broader library changes and PIN-protected folder are on the [review branch](https://github.com/Tajoor621/TN-Player/tree/library-workflow-review). Its [source check failed](https://github.com/Tajoor621/TN-Player/actions/runs/41789815642); the changes are not integrated into `main`. No device scan result is recorded.
- Decoder source integration is complete. Existing pre-deferral validation evidence and known pending checks are in VALIDATION.md.
- Do not start another APK-producing/device-test/publication run during this phase. Normal repository pushes perform source/script checks, Kotlin compilation, JVM unit tests and lint only; their task graph is checked to reject APK packaging/assembly/install tasks.
- After the three features are integrated, run the full Android workflow with `build_apk=true` against the final main commit; resolve failures, including the optimized-release runtime check. Then run Pages publication with `publish_apk=true` against that same commit. Its gate requires successful source, native, build and both device jobs; a source-only green run cannot authorize publication.
- The landing-page Android download still serves the previously published APK from source `18c0426e`; see [the size and checksum](VALIDATION.md#current-public-download). FFmpeg source/license links are inserted atomically with the final matching APK and source archive.
- Update this record as real feature prompts are completed. No final build or distribution has been requested yet for the three feature workflows.
