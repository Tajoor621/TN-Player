# Local library workflow

The Videos and Music tabs read MediaStore on an I/O dispatcher. Name, date added, date modified, size, duration, and watch progress sort in either direction. Unknown metadata sorts after known values. A folder remembers its sort key and direction independently. Files uses Android's Storage Access Framework, which does not generally supply date added or media duration. Duration and progress there can use existing playback metadata. The sort retains deterministic ties for paging.

Search as you type filters the selected media folder or, with **Search whole library**, all indexed folders in the current Videos or Music tab. The Files tab searches the open document folder. MediaStore and document-provider access still depend on Android grants. Glide loads thumbnails asynchronously and caches decoded resources; inaccessible or unreadable media keeps its placeholder.

**Continue watching** reads saved positions from Room and excludes unstarted and nearly finished entries. Playlists can be renamed, deleted, and reordered, and individual tracks can be moved up or down. When a video's sibling is readable through a local path or a granted external-storage tree, TN-Player checks for the same basename with SRT, VTT, ASS/SSA, or TTML. Exact basename matching does not include language suffixes. MediaStore alone does not grant access to subtitle files. Downloads now go to public `Downloads/TN-Player`; previous downloads keep their original locations. Playlist warming reads metadata only.

**Private Folder** keeps original files in place. It gates this app's view with a PIN, with Keystore-backed encryption for the folder record and PIN verifier. It excludes protected files from public catalog results, recents, playlists, favorites, and thumbnail requests. It stops private playback on leaving the app and blocks screenshots in private screens. This is app-level hiding: other apps with independent access to the files may still see them. It is not an encrypted media vault. This first version supports internal and removable storage exposed by Android's local external-storage document provider; other providers can be unavailable while protection is active if their files cannot be reliably classified.

## Device validation

`LibraryDeviceTest` uses MediaStore fixtures and the actual PIN screen to verify that protected entries are excluded even after unlock. `scripts/benchmark-library.sh` uses the already installed app and test APKs on a dedicated API 29+ device. It creates 5,000 one-second PCM WAV files in a unique public Music test folder, measures the first catalog page and UI heartbeat gaps, exports a CSV, and removes the test files. The recorded scan excludes fixture creation and cleanup. The heartbeat threshold is 1 second; the CSV also reports the actual maximum gap.

**Current scan result:** pending a device run. No measured scan time or UI heartbeat result yet.

## Later phase

Built-in SMB, WebDAV, and FTP-family browsing/playback are deferred. The intended design is off by default, streams media without writing file contents into app storage, and uses Android Keystore-backed credential storage. No network protocol libraries or credential configuration UI are included in this change.
