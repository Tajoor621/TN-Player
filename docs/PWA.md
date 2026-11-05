# TN-Player Web

The [project site](https://tajoor621.github.io/TN-Player/) exposes a Web App Manifest. The installed app opens the browser player, which plays device files through the browser's built-in media element. Local media is never uploaded or stored in the offline cache. The service worker caches the web shell after an initial online visit; the Android APK remains a separate download.

Run the asset and scoring check with:

```sh
python3 scripts/check-web-manifest.py https://tajoor621.github.io/TN-Player/
```

The check mirrors the applicable rules in PWABuilder's [published manifest-finder scorer](https://github.com/pwa-builder/pwabuilder-manifest-finder/blob/fd230613536a172e9fd5e6c9d9975c0f6fdc0670/Services/ManifestService.cs). This configuration targets 160 raw manifest points: 70 required and 90 recommended. It does not supply a store rating, store relationship or screenshots. The score is from the published rules, and is separate from the live [PWABuilder report](https://www.pwabuilder.com/reportcard?site=https%3A%2F%2Ftajoor621.github.io%2FTN-Player%2F) and browser install testing.

## Live deployment check

Checked on 5 November 2026 before this change, against the public Pages site
and main commit `45c3db1`.

| Live resource | HTTP GET | Findings |
| --- | --- | --- |
| `manifest.webmanifest` | 200 | `file_handlers` and `launch_handler` absent |
| `sw.js` | 200 | Worker script reachable |
| `pwa.js` | 200 | Registration was in the external script |

All three responses matched the pre-change main files byte for byte. The
live site was not behind that revision; this check is not evidence that Pages
has served the changes below. Reachability alone does not show that a scanner
detected registration or that the worker installed.

## Static detection and desktop window controls

Both HTML entry points declare one guarded service-worker registration
inline. It resolves `./sw.js` in the same scope. The shell cache name is
`v3` and includes the shared stylesheet plus the four reference screens used
on the project page. Local media, the APK, and checksum files stay outside
that cache. Nothing the visitor plays is uploaded.

The manifest prefers `window-controls-overlay` and falls back to
`standalone`. In a supporting desktop install, the existing header fits the
available title-bar rectangle while the overlay is visible. Browsers without
the API and windows without a visible overlay keep the normal header. This
has no effect on Android phones.

The local checker is unchanged; `display` remains `standalone`. Its raw
manifest score does not measure the overlay or service worker. Retest the
public URL in PWABuilder after Pages serves the update. Installed desktop
geometry and offline playback setup still need browser verification. Real
web-app screenshots could improve the manifest report once captured and
served. Store associations or content ratings should only be added if they
actually exist and apply.

## Local playback tools

The browser player can queue up to 100 files from the picker or a drop,
change speed, repeat one file or the queue, run a sleep timer, open
picture-in-picture where the browser allows it, and attach WebVTT or plain
SRT captions. A resume position for the same file name, size, and modified
time is kept in `sessionStorage` for that tab. Styled ASS/SSA captions,
direct network streams, and the Android gesture surface are not part of this
page. Drag-and-drop and the file input are in-page only. They are not
`file_handlers`.

`scripts/check-site.py` checks the local HTML, manifest limits, shell files,
and the published APK checksum. It does not fetch the live site. Run
`scripts/check-web-manifest.py` against the public URL after Pages serves
the update.

## Deliberately omitted

| Capability | Status | Reason |
| --- | --- | --- |
| `share_target` | Owner decision | Receiving shared files would need an approved storage and handoff design. |
| `protocol_handlers` | Omitted | No custom URL protocol handling. |
| `widgets` | Omitted | No PWA widgets. |
| `edge_side_panel` | Omitted | No Edge side-panel interface. |
| Tabbed display | Omitted | No installed-app tab management. |
| `note_taking` | Omitted | No note-taking interface. |
| `file_handlers` | Omitted | The in-page picker does not implement OS-level file opening. |
| `launch_handler` | Omitted | Uses the browser's default launch behavior; no custom launch routing. |

## Web-only deployment (owner steps)

1. In repository **Settings → Pages**, confirm **Deploy from a branch** is
   configured for `main` and `/ (root)`. With that configuration a normal
   push to `main` can trigger the built-in **pages build and deployment** run.
2. In **Actions**, confirm that built-in deployment succeeded for the web commit.
   If it did not run, check the Pages source configuration; if it failed, review
   and re-run that built-in deployment. Do not dispatch the APK publication
   workflow (`pages.yml`).
3. Check the served manifest, both HTML pages, `pwa.js` and `sw.js`, and run
   the checker above against the public URL. Then retest PWABuilder. No APK
   rebuild or changes to the existing APK download and checksum are needed.
