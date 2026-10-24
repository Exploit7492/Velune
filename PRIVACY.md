# Privacy Policy — YT-PRO (Velune)

Last updated: 2026-10-24

## Summary

YT-PRO (the app whose source repository is `Velune`) is a music and video client.
It has no account system of its own, no analytics or telemetry SDK, no
crash-reporting service, and no advertising. Nothing is sent to a server operated
by this project. Your library, playlists, history, downloads, and settings are
stored on the device and leave it only when you use a feature that talks to a
service you chose (for example signing in to Spotify or YouTube).

## What the app stores on your device

- Playlists, liked songs, listening history, and queue state.
- Settings and preferences.
- Downloaded audio/video and cached artwork or metadata.
- Optional integration tokens (for example a Spotify, YouTube, or Discord token
  you granted). These stay on the device.

## What is sent, and only when you use those features

- Music, video, artwork, and metadata requests go to the service serving the
  content (for example YouTube/YouTube Music, or Spotify if you sign in).
- Optional lyrics lookups go to the configured lyrics provider.
- Optional scrobbling to Last.fm or ListenBrainz sends your listening activity
  to the service you enabled.
- Optional Discord Rich Presence shares the track you are playing with Discord,
  if you turn it on.
- The optional in-app updater checks the project's own release endpoint for a
  newer version. It sends no personal data.
- Optional Spotify import reads your Spotify playlists through Spotify's API
  after you authorise it.

No data is sent to a server operated by this project.

## Permissions

| Permission | Why it is declared | Required? |
| --- | --- | --- |
| `INTERNET` | All network access (music, video, artwork, metadata) | Yes |
| `ACCESS_NETWORK_STATE` | Offline detection and retry policy | Yes |
| `WAKE_LOCK` | Background playback and downloads | Yes |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC` | Playback and download services | Yes |
| `POST_NOTIFICATIONS` | Playback notification and download progress | No |
| `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` | Play media files already on the device | No |
| `WRITE_EXTERNAL_STORAGE` | Save downloads on older Android versions | No |
| `VIBRATE` | Haptic feedback on controls | No |

## Data deletion

Uninstalling the app removes everything it stored on the device. If you signed in
to a third-party service, revoke the app's access from that service's account
settings.

## Children

The app is not directed at children and does not knowingly collect personal
information from them.

## Changes

Material changes to this policy are recorded in this file's history in the
repository.

## Contact

Open an issue at https://github.com/Exploit7492/Velune.
