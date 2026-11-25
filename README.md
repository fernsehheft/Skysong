# Skysong

A client-side Fabric mod that shows the synced lyrics of whatever you're listening to
right inside your Minecraft world.

> Early development. Skysong finds out which song is playing, loads its lyrics and shows
> them floating in front of you, word by word.

## In the world

- **Eight animations**: karaoke, fade, rise, wave, typewriter, glitch, drop and zoom
- **Three placements**: straight ahead, scattered around you, or up in the sky where the clouds are (look up and they follow)
- **Reacting to the song**: the chorus is drawn bigger, the song's title stands out and jumps when it's sung, background vocals in brackets step back, and held notes grow
- **Seven fonts**: Minecraft's own plus six free fonts (see [Credits](#credits))
- **Effects**: glow, 3D letters, sparks flying off every sung word, and a small beat zoom when a new line starts
- **Colours**: one colour, a flowing gradient between two colours, or a rainbow; the word being sung lights up in its own colour
- Pause the music and the lyrics fade away; play on and they come back at the right line

## On screen

- **Karaoke bar**: the current line as a subtitle, words lighting up as they're sung, with the next line below
- **Now playing card**: song, artist, a progress bar and whether lyrics were found
- **Title intro**: the song's name, big, when a new song starts
- **Cinematic bars**: black bars at the top and bottom while music plays (off by default)

Click **Move HUD** in the settings to drag the karaoke bar and the card around and resize
them with the mouse wheel. Hide the game HUD with **F1** and the karaoke bar, title intro and
cinematic bars stay, which is handy for recording videos.

## Settings

Press **K** in game, or use the settings button in [Mod Menu](https://modrinth.com/mod/modmenu)
if you have it. The settings sit on the left so you can watch every change on the right
while the music plays. Nothing playing? Turn on the demo song in the Music tab.

Presets give you a ready-made look in one click: Classic, Neon, Sunset, Dreamy, Bold, Aurora and Prism.

Everything is saved in `.minecraft/config/skysong/settings.json`, which you can also edit by hand:

| Setting | What it does | Default |
|---|---|---|
| `worldLyrics` | Show the lyrics in the world (also on a key you can bind in Controls) | `true` |
| `placement` | `AHEAD` (in front of you), `SCATTERED` (a new spot around you for every line) or `SKY` | `AHEAD` |
| `animation` | `KARAOKE`, `FADE`, `RISE`, `WAVE`, `TYPEWRITER`, `GLITCH`, `DROP` or `ZOOM` | `KARAOKE` |
| `font` | `MINECRAFT`, `SMOOTH`, `BOLD`, `SCRIPT`, `ELEGANT`, `RETRO` or `MONO` | `MINECRAFT` |
| `reactToSong` | Bigger chorus, title words standing out, smaller background vocals, growing held notes | `true` |
| `glow`, `depth3d`, `sparkles`, `beatZoom` | Effects for the lyrics in the world | `false`, `false`, `true`, `true` |
| `distance` | How far away the lyrics are, in blocks (1.5 to 12) | `4.0` |
| `size` | Text size, 1.0 is normal (0.25 to 4) | `1.0` |
| `textColor`, `highlightColor` | Colours like `"#FFD966"` | `#FFFFFF`, `#FFD966` |
| `colorMode` | `SINGLE` (the text colour), `GRADIENT` or `RAINBOW` | `SINGLE` |
| `gradientStart`, `gradientEnd` | The two colours of the flowing gradient | `#FF7EB3`, `#7AD7FF` |
| `offsetMs` | Show the lyrics this many milliseconds earlier (negative: later) | `0` |
| `karaokeBar`, `karaokeStyle` | Show the karaoke bar; style `PILL` (dark background) or `MINIMAL` (just text) | `true`, `PILL` |
| `nowPlayingCard` | Show the now playing card | `true` |
| `titleIntro` | Show the song title when a new song starts | `true` |
| `cinematicBars`, `cinematicBarSize` | Black bars while music plays, and their height as a share of the screen | `false`, `0.09` |
| `hudWhenHidden` | Keep the karaoke bar, title intro and bars when the HUD is hidden with F1 | `true` |
| `musicSource` | `AUTO` (Spotify when logged in, else the system player), `SPOTIFY` or `SYSTEM` | `AUTO` |
| `estimateWordTiming` | Guess when each word is sung if the lyrics only time whole lines | `false` |
| `debugOverlay` | Show timing details in the top left | `false` |
| `demoSong` | Play a built-in demo song instead of your music, to try the looks | `false` |

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

### Spotify (optional)

Logging in to Spotify also works when the music plays on another device, like your phone,
and lets you play, pause, skip and change the volume from the game (Spotify only allows
that with Premium). Spotify wants every program that uses its Web API to have its own app,
so you make a free one for yourself:

1. Open the [Spotify developer dashboard](https://developer.spotify.com/dashboard) and create an app. Any name works.
2. Tick **Web API** and add `http://127.0.0.1:18652/callback` as **Redirect URI**.
3. Copy the app's **Client ID** into Skysong's settings (Music tab) and press **Log in**.

Only a login token is saved, in `.minecraft/config/skysong/spotify.json`. Never your password.

The music control keys (play/pause, next, previous, volume) have no key by default; set them
in Minecraft's Controls under Skysong.

## When the timing is off

Turn on **Timing details** in the Music tab to see where the player says the song is, which
time the lyrics use and which lyrics were found. The game log also notes which LRCLIB entry
was picked.

Most lyrics only say when each line starts, not each word. So by default the words of a line
come together; **Guess word timing** lights them up one by one at a guessed time instead,
which can run ahead when a singer holds a note. Lyrics with real word timing always go word
by word.

Lyrics databases usually have the timing of the album version. A music video with a longer
intro, or a live version, can be ahead or behind. In the settings (Music tab) you can show
the lyrics of the song that's playing earlier or later; that's remembered for that song.
There are also keys for it (*Lyrics earlier* / *Lyrics later*), unbound by default.

Remixes, sped up and slowed versions get the lyrics of the original song, stretched to the
version's length. For sped up and slowed versions that fits; for other remixes it's a guess,
so the card says "timing estimated".

## Where the lyrics come from

1. **Your own files** in `.minecraft/config/skysong/lyrics/`. Name them `Artist - Title.lrc`
   (with timestamps) or `Title.txt` (plain text). A README in that folder explains the format.
2. **[LRCLIB](https://lrclib.net)**, a free and open lyrics database. Downloaded lyrics are
   kept in `.minecraft/config/skysong/cache/`, so songs you've heard before work offline.

## Credits

The fonts come from [Google Fonts](https://fonts.google.com) under the SIL Open Font License,
which is included next to them in `assets/skysong/font/licenses`: Poppins, Bebas Neue,
Pacifico, DM Serif Display, Press Start 2P and Space Mono.

## License

Copyright (C) 2026 fernsehheft

Skysong is licensed under the [GNU General Public License v3.0](LICENSE).
You're welcome to fork it and change it, but if you share a modified version, it has to
stay open source under the same license and say what you changed.
