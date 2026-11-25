package io.github.fernsehheft.skysong.media;

import java.util.Optional;

/**
 * Plays a short song that comes with the mod, on repeat, so every look can be tried
 * without any music running. There's no audio, only the timing and the lyrics.
 */
public final class DemoSource implements MediaSource {
    public static final String TITLE = "Skysong";
    public static final String ARTIST = "Skysong Demo";
    public static final String PLAYER = "Demo";
    private static final long DURATION_MS = 32_000;
    // The lyrics are written to show off what Skysong can do: "Skysong" is the title and
    // comes back in the chorus, "(oh-oh)" are background vocals and "loud" is a held note.

    public static final String LYRICS = """
            [ti:Skysong]
            [ar:Skysong Demo]
            [00:02.00]Look up, the night is open wide
            [00:05.50]A thousand blocks of starlight
            [00:09.00]Skysong, shining through the dark
            [00:12.50]Until the sky is yours and mine
            [00:16.00]<00:16.00>Sing <00:16.50>it <00:16.90>out <00:17.30>loud<00:19.40>
            [00:19.50]Let the words light up the clouds (oh-oh)
            [00:23.00]Every word a little spark
            [00:26.50]Skysong, shining through the dark
            [00:30.00]
            """;

    private boolean playing = true;
    private boolean paused;
    private long startedAt = System.nanoTime();
    private long pausedAt;

    public static boolean isDemo(NowPlaying track) {
        return track != null && PLAYER.equals(track.player()) && TITLE.equals(track.title());
    }

    @Override
    public String name() {
        return "the demo song";
    }

    /** Starts the demo from the beginning, unless it's already playing. */
    synchronized void restartIfStopped() {
        if (!playing) {
            playing = true;
            paused = false;
            startedAt = System.nanoTime();
        }
    }

    /** Pauses the demo, or lets it play on from where it was paused. */
    public synchronized void togglePause() {
        long now = System.nanoTime();
        if (paused) {
            // Move the start by the length of the pause, so the song continues where it stopped.
            startedAt += now - pausedAt;
        } else {
            pausedAt = now;
        }
        paused = !paused;
    }

    public synchronized boolean paused() {
        return paused;
    }

    synchronized void stop() {
        playing = false;
    }

    @Override
    public synchronized Optional<NowPlaying> poll() {
        if (!playing) {
            return Optional.empty();
        }
        long now = System.nanoTime();
        long position = (((paused ? pausedAt : now) - startedAt) / 1_000_000L) % DURATION_MS;
        return Optional.of(new NowPlaying(TITLE, ARTIST, "", DURATION_MS, position, !paused, PLAYER, now));
    }
}
