# Folder queues and playback notifications

A MediaStore file tap queries the entire matching volume/path, including
accessible indexed audio and video, and sorts with the active library order.
Search, the display-page boundary and minimum duration do not shorten the
queue. Folder exclusions still apply. The Files tab uses the selected SAF
directory and its name order; opaque providers or entries that cannot be
resolved to a folder from other screens play individually. Playlist Play all
preserves playlist order. A list over 5,000 playable entries is rejected with a
message. The player receives a one-shot process-local token, not a large
Android intent. If the process dies before consuming it, reopen the file.
The position badge is the item's index in the queue; shuffle and repeat remain
Media3 settings. This does not change the decoding pipeline.

On Android 13+ the player offers the notification permission once, from the
visible player. Denial shows one settings link. Ordinary notifications may be
hidden; Android exempts media-session notifications, so denial does not
guarantee that controls disappear. Actual visibility depends on channel,
system settings, OEM behavior and session state.

The service installs Media3 1.4.1 DefaultMediaNotificationProvider using
`tn-player-playback`. The Android manifest declares `mediaPlayback` and
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`. In pinned Media3,
`MediaNotificationManager.startForeground` starts the service and calls
`ServiceCompat.startForeground` with
`ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`. See the
[version-pinned source](https://github.com/androidx/media/blob/1.4.1/libraries/session/src/main/java/androidx/media3/session/MediaNotificationManager.java).
Task removal stops the service only when its queue is empty. Media3 still
owns foreground promotion and demotion; Android can reclaim the process.
Opt-in paused-decoder release puts the player into IDLE, where Media3 1.4.1
may hide its notification while leaving the queue intact.

Source-only checks do not verify Samsung or other device notification controls,
Android 14+ promotion at runtime, or continuous playback after task removal.
