# TN-Player repository

| Path | Purpose |
|---|---|
| app/ | Android app, tests and media fixtures |
| branding/ | User-supplied original logo, square adaptation and editing notes |
| assets/ | Shared Pages icon |
| index.html | GitHub Pages landing page |
| downloads/ | Built testing APK and its SHA-256 checksum |
| build-info.json | APK source commit, byte count and SHA-256 |
| .nojekyll | Publish root assets without Jekyll processing |
| .github/workflows/android.yml | Android regression, build and emulator checks |
| .github/workflows/pages.yml | Build APK, publish to main/root and verify the public download |
| scripts/package-pages.py | Assemble matching page, APK and manifest |
| scripts/verify-pages.py | Verify the public source commit and full APK checksum |
| scripts/process-recovery-check.sh | Prepare, force-stop and verify cold restoration |
| validation/history/ | Preserved historical evidence |
| validation/reference-ui/ | Native emulator screenshots of the folder browser, view dialog and Me hub |
| VALIDATION.md | Current verified results and limitations |
| MX_PLAYER_COMPARISON.md | Current store and historical manual comparison, additions and remaining gaps |
| FEATURE_STATUS.md | Implemented features and unfinished modules |

The public repository contains no private signing keys. GitHub Pages is the landing/download site for the native Android app.

- `AUDIT.md`: professional source/workflow audit, implemented fixes and prioritized remaining features.
