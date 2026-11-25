# Skysong

A client-side Fabric mod that shows the synced lyrics of whatever you're listening to
right inside your Minecraft world.

> Early development. Skysong finds out which song is playing, loads its lyrics and shows
> them floating in front of you, word by word.

## On screen

- **Karaoke bar**: the current line as a subtitle, words lighting up as they're sung, with the next line below
- **Now playing card**: song, artist, a progress bar and whether lyrics were found
- **Title intro**: the song's name, big, when a new song starts
- **Cinematic bars**: black bars at the top and bottom while music plays (off by default)

Press **K** to move the karaoke bar and the card with the mouse and resize them with the
mouse wheel. Hide the game HUD with **F1** and the karaoke bar, title intro and cinematic
bars stay, which is handy for recording videos.

## Settings

Until there's a settings screen, the settings live in `.minecraft/config/skysong/settings.json`:

| Setting | What it does | Default |
|---|---|---|
| `worldLyrics` | Show the lyrics in the world (also on a key you can bind in Controls) | `true` |
| `placement` | `AHEAD` (in front of you) or `SCATTERED` (a new spot around you for every line) | `AHEAD` |
| `animation` | `KARAOKE`, `FADE` or `RISE` | `KARAOKE` |
| `distance` | How far away the lyrics are, in blocks (1.5 to 12) | `4.0` |
| `size` | Text size, 1.0 is normal (0.25 to 4) | `1.0` |
| `textColor`, `highlightColor` | Colours like `"#FFD966"` | `#FFFFFF`, `#FFD966` |
| `offsetMs` | Show the lyrics this many milliseconds earlier (negative: later) | `0` |
| `karaokeBar`, `karaokeStyle` | Show the karaoke bar; style `PILL` (dark background) or `MINIMAL` (just text) | `true`, `PILL` |
| `nowPlayingCard` | Show the now playing card | `true` |
| `titleIntro` | Show the song title when a new song starts | `true` |
| `cinematicBars`, `cinematicBarSize` | Black bars while music plays, and their height as a share of the screen | `false`, `0.09` |
| `hudWhenHidden` | Keep the karaoke bar, title intro and bars when the HUD is hidden with F1 | `true` |
| `demoSong` | Play a built-in demo song instead of your music, to try the looks (needs a restart) | `false` |

## Where the music comes from

Skysong reads the music player of your operating system, no login needed:

- **Windows**: the media session Windows shows in its volume flyout (Spotify, browsers, most music apps)
- **macOS**: Spotify or Apple Music
- **Linux**: any MPRIS player through [`playerctl`](https://github.com/altdesktop/playerctl), which needs to be installed

## Building

You need JDK 17 or newer.

```bash
./gradlew build
```

The mod jar ends up in `build/libs/`. To start a development client:

```bash
./gradlew runClient
```

## Supported versions

| Minecraft | Loader | Status |
|-----------|--------|--------|
| 1.20.1    | Fabric | in development |

Other versions follow once 1.20.1 is complete.

## Where the lyrics come from

1. **Your own files** in `.minecraft/config/skysong/lyrics/`. Name them `Artist - Title.lrc`
   (with timestamps) or `Title.txt` (plain text). A README in that folder explains the format.
2. **[LRCLIB](https://lrclib.net)**, a free and open lyrics database. Downloaded lyrics are
   kept in `.minecraft/config/skysong/cache/`, so songs you've heard before work offline.

## License

Copyright (C) 2026 fernsehheft

Skysong is licensed under the [GNU General Public License v3.0](LICENSE).
You're welcome to fork it and change it, but if you share a modified version, it has to
stay open source under the same license and say what you changed.
