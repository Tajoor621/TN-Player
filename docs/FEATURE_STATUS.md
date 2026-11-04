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
