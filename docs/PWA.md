# TN-Player Web

The [project site](https://tajoor621.github.io/TN-Player/) exposes a Web App Manifest. The installed app opens the browser player, which plays device files through the browser's built-in media element. Local media is never uploaded or stored in the offline cache. The service worker caches the web shell after an initial online visit; the Android APK remains a separate download.

Run the asset and scoring check with:

```sh
python3 scripts/check-web-manifest.py https://tajoor621.github.io/TN-Player/
```

The check mirrors the applicable rules in PWABuilder's [published manifest-finder scorer](https://github.com/pwa-builder/pwabuilder-manifest-finder/blob/fd230613536a172e9fd5e6c9d9975c0f6fdc0670/Services/ManifestService.cs). This configuration targets 160 raw manifest points: 70 required and 90 recommended. It does not supply a store rating, store relationship or screenshots. The score is from the published rules, and is separate from the live [PWABuilder report](https://www.pwabuilder.com/reportcard?site=https%3A%2F%2Ftajoor621.github.io%2FTN-Player%2F) and browser install testing.
