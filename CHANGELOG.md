# TN-Player 1.0.0 reliability revision

- Keep Files navigation intact after cancelling the playlist chooser; refresh collection screens on return.
- Retain folder navigation and transfer progress during rotation; keep a now-playing control bar available while browsing.
- Add bounded MediaStore pages and background search instead of materializing the whole library.
- Encrypt saved network URL payloads and playback checkpoints with Keystore-backed keys; use opaque database identifiers, migrate legacy URLs, and redact URL-bearing titles.
- Restore queue, selected item, position, repeat/shuffle state and explicit streaming MIME type after process loss. Restoration is paused until the user requests playback.
- Stop A–B polling while paused; keep the screen awake only during active playback. Show setting success only after the service accepts the command.
- Stage and verify individual file transfers using SHA-256 read-back before committing and removing an original. Add progress, cancellation and encrypted interrupted-transfer tracking.
- Extend regression coverage for navigation, Room migration, encryption/tampering, retention, checkpoints and failed/cancelled transfers. Add Android playback/PiP/Keystore and external process-recovery test sources.

See VALIDATION.md for executed checks and remaining limitations. This revision preserves versionName 1.0.0, versionCode 1 and the requested SDK levels. It is a debug testing build, not a signed production release.
