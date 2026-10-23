# Baliset

<img src="docs/images/icon.png" alt="Baliset app icon: a brass lyre on black" width="128" align="right" />

**Baliset** is a private, local music player for Android with the iOS 26 liquid-glass look of the [Glass](https://github.com/everettpenne/Glass)
suite. It plays the music already on your phone — no account, no ads, no upload — and hands the launcher a real media session, so
Uno Launcher's dynamic island, media widget, lock screen and control panel can show and control it.

It is the listening leg of the suite: Seekur reads Reddit, Spice plays Jellyfin, Thumper plays podcasts and feeds, Sietch opens files,
and Baliset plays the songs that are simply *there*.

<p align="center">
  <img src="docs/images/welcome.webp" width="180" alt="Welcome: your music, privately">
  <img src="docs/images/home.webp" width="180" alt="Home: jump back in, shuffle all, recently added and played">
  <img src="docs/images/library.webp" width="180" alt="Library: albums in a grid">
  <img src="docs/images/now-playing.webp" width="180" alt="Now Playing over blurred album art">
</p>
<p align="center">
  <img src="docs/images/album.webp" width="180" alt="An album with Play and Shuffle">
  <img src="docs/images/queue.webp" width="180" alt="Up Next, with drag handles">
  <img src="docs/images/search.webp" width="180" alt="Search across songs, albums and artists">
  <img src="docs/images/settings.webp" width="180" alt="Settings, grouped like the rest of the suite">
</p>

## What it does

- **Your library, two ways.** Read Android's media index with one audio permission, or hand Baliset a folder in the system picker and
  it reads only that folder — no storage permission at all. Tags (title, artist, album, disc, year, length) and embedded cover art are
  read on the phone; untagged files get a sensible name from their file name and a cover-shaped placeholder.
- **A player that keeps playing.** A Media3 `MediaSessionService` owns the sound: background playback, a media notification you can
  control, lock-screen and headset buttons, audio focus, "pause when headphones disconnect", gapless consecutive tracks, and a
  "previous restarts the song once it is past three seconds" rule.
- **A queue you can see and shape.** Shuffle and repeat (off / all / one), drag any song to a new place in Up Next, remove one with ×,
  or clear the queue. Shuffle is the app's own order, so the page always shows what will really play.
- **Playlists and favourites.** Make a list, add songs from anywhere with a long press, rename or delete it, and keep a heart on the
  tracks you love. Both live only on the phone.
- **Jump back in.** The last song is offered where it stopped, and Recently played keeps the last ten.
- **A friendly Now Playing.** Blurred cover, a scrubber you can drag, five controls in the usual places, Up Next, a sleep timer
  (15 / 30 / 45 / 60 minutes, or the end of the track) and a menu to reach the album, the artist or a playlist.
- **Search as you type** across songs, albums and artists, with accents and case ignored and the best matches first.
- **The launcher's island, for free.** Baliset publishes the title, artist, album, cover bitmap and duration on its media session. Uno
  Launcher reads exactly those, so the island's playing card, the media widget, the control panel and the paused-track memory all work
  with no launcher code and no special integration.

## Install

**[Download the latest APK](https://github.com/everettpenne/Baliset/releases/latest)**: on the release page expand **Assets** and tap
the `.apk`.

Android 12 or later (minSdk 31, targetSdk 36). It is a normal sideloaded app; nothing is published to a store. Updates are signed with
one key, so a new release installs over the old one. Baliset can check GitHub for you (**Settings → Check for updates**) and hands the
download to Android's installer, which always asks first.

## How to use

| To do this | Do this |
| --- | --- |
| Give it music | **Scan my phone** (one audio permission) or **Choose a folder** (no permission, only that folder) |
| Play something | Tap a song, an album, **Play**, **Shuffle**, or **Shuffle all** on Home |
| Change what plays next | Hold a song → **Play next** / **Add to queue**; open **Up Next** to drag or remove |
| Keep a song | Hold it → **Add to favourites**, or tap the heart in the player |
| Make a playlist | **Playlists → +**; then hold any song → **Add to playlist** |
| Jump to the album or artist | Hold a song, or use the ••• menu in the player |
| Sleep | In the player tap **Sleep**, and choose a length or the end of the track |
| Rescan or change source | **Settings → Rescan now**, or the Folder row to choose another folder |

## Glass

The interface is the shared [Glass](https://github.com/everettpenne/Glass) kit: `GlassScreen`, the floating tab bar, glass sheets,
menus and sliders, the HIG type scale in Inter, and the suite's player controls (`PlayerScrubber`, `PlayerTransport`, `PlayButton`).
Baliset is built with the version in `glass.version` via a composite build:

```kotlin
// settings.gradle.kts
includeBuild("../Glass")
```

This release also adds three pieces to Glass that the suite was missing, used here and ready for Seekur, Spice and Thumper:

- `glass/GlassRows.kt`: `GlassSectionHeader`, `GlassGroup`, `GlassListRow`, `GlassRowDivider`, `GlassSearchField`, `GlassEmptyState`.
- `media/MiniPlayer.kt`: `GlassMiniPlayer`, the now-playing capsule with a progress hairline.

## Build

JDK 17 or 21, Android SDK 36, and the Glass checkout next to this one (`../Glass`). The wrapper is included.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Release builds take their signing from the environment and are unsigned
without it: `BALISET_RELEASE_STORE_FILE`, `BALISET_RELEASE_STORE_PASSWORD`, `BALISET_RELEASE_KEY_ALIAS`,
`BALISET_RELEASE_KEY_PASSWORD`. The signing key is deliberately outside the repository.

## Status and limits

- **Baliset is a working name** for the app; the package id is `com.everettpenne.baliset`.
- Version 0.1.0. It has been built and exercised on an emulator (1080 × 2400, Android 17 / API 37) with sample music, in light and
  dark themes; **it has not been run on a physical phone or a GrapheneOS device.**
- The phone-index route is implemented, but the test emulator's media index exposed the pushed files without tags or `is_music`, so the
  screenshots use the folder route (the same scanner, reading each file's tags). On a phone whose index is populated, **Scan my phone**
  reads it; if the index is empty, the welcome page points you at **Choose a folder**.
- No home-screen widget yet. The island, notification and lock-screen controls are the system surfaces Baliset uses today.
- Playlists, favourites and the library list are not exported; making a backup is a future feature. Clearing the app's storage forgets
  them; the music files are untouched.

## Credits and licence

MIT, see [LICENSE](LICENSE). Baliset is an independent project, not affiliated with Apple, Google or GrapheneOS. The glass recipe is
[Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) (Apache-2.0); playback is AndroidX Media3; type is Inter (SIL OFL 1.1). See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [PRIVACY.md](PRIVACY.md).
