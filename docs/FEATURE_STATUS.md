# Library access and explicit storage imports

The Videos and Music tabs read Android's MediaStore index. See the [main feature
matrix](../FEATURE_STATUS.md) for the rest of TN-Player.

| Access state | In-app behavior |
| --- | --- |
| Full | Show accessible MediaStore folders and file counts without opening a document picker. Existing list, grid, sorting and search remain available. |
| Partial (video, Android 14+) | Show the selected-video library and count the videos visible to the app, independently of folder and search filters. **Allow all media** requests video, audio and selected-media permissions. If access remains partial, show the app Settings route. The video Continue Watching row is hidden to avoid showing older history outside the selected set. |
| Needs request | Explain why access is needed and request it only when the user taps **Allow access**. Android owns the permission dialog and its choices. |
| Permanently denied | Explain how to change access and offer **Open app settings**. |

Android 12 and older use the existing storage read permission. Android 13+
requests video or audio access as appropriate. The app rechecks grants on resume.
The Settings hub's Media library access action returns to Videos and shows the
relevant access state; it does not open a provider picker.

For SD cards, USB drives and Android document providers, use the toolbar menu:
**Add from storage → Open a file** opens Android's document picker;
**Add from storage → Open a folder** opens its folder picker and retains the
existing Files browsing path. Merely opening the app, switching tabs or opening
the menu does not launch either picker. Unrelated explicit actions such as backup
import and loading external subtitles still use their own document pickers.

## Folder queues, diagnostics and hidden folders

See [PLAYBACK.md](PLAYBACK.md) for queue ordering, the 5,000-item cap and
notification behavior. MediaStore folder playback is independent of the
200-item display page.

**Library diagnostics** reports Android video and audio grants and, for each
collection, raw MediaStore rows → dot-name filter → user folder exclusions →
minimum duration → current folder scope → folder grouping → text search. The
grouped and searched columns count display rows (folders or files). An empty
Music list with indexed audio reports the first stage that reaches zero and
offers its reset. If raw audio is zero, no app filter removed it; verify the
audio permission and the Android index. The dialog requests a bounded scanner
pass for readable files in the last selected local storage tree, then refreshes.
Android may deny direct path access even when SAF can read a file; opaque or
cloud providers are not scanned.

Long-press an indexed folder or a supported local SAF folder to hide/unhide it.
The Me hub and Settings include **Hidden folders** with current indexed file
counts. **Show hidden folders** brings those folders and their descendants back
into results and dims folder rows with a Hidden badge. Dot-prefixed names have
a separate filter. Hiding is a browse preference, not a vault or a deletion.
The backup format stays version 1. It accepts bounded, validated
`excludedFolders` and `showExcludedFolders` values; old backups may omit both.
