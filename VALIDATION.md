# Current branding revision

The application display name remains TN-Player. This revision replaces the image in the launcher, adaptive launcher, toolbar and About screen and adds the GitHub Pages landing/download page.

Android package `com.tplayer.mediaplayer`, SDK levels, version and storage identifiers are unchanged. No private keys are committed.

## Evidence

Previous 20-test/zero-lint-error results are historical, preserved under `validation/history/t-player-rebrand/`. They are not claimed as a test run of this new icon revision. GitHub Actions runs unit tests, lint and debug/release builds for the current commit. Native emulator checks remain in their original CI job.

The website workflow publishes only the rebuilt debug APK from the current commit. The previous APK has been omitted from the repository to avoid presenting its older icon as the current build. The generated website's `build-info.json` records the current commit and APK SHA-256.

Debug signing uses the runner's development key; compatibility with previously installed APKs is not guaranteed. Do not uninstall without preserving needed data. Production signing and open device-validation gates remain outside this branding revision.
