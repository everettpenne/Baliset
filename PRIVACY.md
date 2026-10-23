# Privacy

Baliset is a local music player. It has no account, no server, no advertising and no analytics, and it does not upload anything.

## What it reads

- **With "Scan my phone":** Android's media index, which lists the audio files on the phone. Baliset reads each track's title, artist,
  album, album id, track number, year, length and file name, and asks Android for a thumbnail (the embedded cover) for each album.
- **With "Choose a folder":** only the folder you grant through the system picker, and its children. Baliset reads the same fields from
  each audio file's tags, plus the embedded cover.
- **Playback:** the file you choose, opened by the player. Nothing else is read from it.

Baliset does not read contacts, messages, photos, location, the clipboard, other apps' notifications or anything outside the audio
files and the folder you choose.

## What it stores (on the phone only)

| What | Where | Why |
| --- | --- | --- |
| The library list | `files/library.json` in the app's private storage | so opening the app is instant |
| Cover art (one small JPEG per album) | `files/art/` | so lists scroll without reading files |
| Playlists and favourites | `files/collections.json` | your lists |
| Source, folder, recent plays, last position, sleep length | shared preferences | remembered choices |

None of it is sent anywhere, and it is not included in Android backups (`allowBackup` is off). Uninstalling Baliset or clearing its
storage removes all of it. The music files themselves are never modified.

## Network

The app declares `INTERNET` only for the shared Glass updater. A request goes to `api.github.com` (repositories
`everettpenne/Baliset`) **only when you tap "Check for updates"**, and, in release builds, once quietly at launch to see whether a
newer release exists. Nothing about you or your library is sent; the request is the same public release listing anyone can open in a
browser. Revoking the Network permission leaves everything except updates working.

## Permissions

- `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 12) — optional, only for **Scan my phone**. Baliset works without
  it via **Choose a folder**; asking is skipped entirely if you only use a folder.
- `POST_NOTIFICATIONS` — for the playback notification (Android 13+). Asked once, when playback first starts. Playback works without
  it; the notification is how another app or the lock screen controls the session.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` — keep music playing with the screen off.
- `INTERNET` — the updater only, as above.
- `REQUEST_INSTALL_PACKAGES` (from the Glass updater library) — to hand a downloaded update to Android's installer, which asks you to
  confirm.

There is no `MANAGE_EXTERNAL_STORAGE`, no accessibility service and no overlay permission.

## Children and tracking

No crash reporting, no identifiers, no third-party SDKs that talk to a network. The only third-party code that touches the network is
the updater, and only for GitHub.
