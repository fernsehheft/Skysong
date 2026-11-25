# Skysong

A client-side Fabric mod that shows the synced lyrics of whatever you're listening to
right inside your Minecraft world.

> Early development. Right now Skysong finds out which song is playing and loads its
> lyrics. Press **K** in game to see the song and the current line in the corner of the
> screen. Showing the lyrics in the world comes next.

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

Skysong is licensed under the [GNU General Public License v3.0](LICENSE).
You're welcome to fork it and change it, but if you share a modified version, it has to
stay open source under the same license and say what you changed.
