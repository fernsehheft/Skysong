package io.github.fernsehheft.skysong.media;

import java.util.Optional;

/**
 * Plays a short song that comes with the mod, on repeat, so every look can be tried
 * without any music running. There's no audio, only the timing and the lyrics.
 */
public final class DemoSource implements MediaSource {
    public static final String TITLE = "Skysong Demo";
    public static final String ARTIST = "Skysong";
    public static final String PLAYER = "Demo";
    private static final long DURATION_MS = 32_000;

    public static final String LYRICS = """
            [ti:Skysong Demo]
            [ar:Skysong]
            [00:02.00]Look up, the night is open wide
            [00:05.50]A thousand blocks of starlight
            [00:09.00]We build it higher, line by line
            [00:12.50]Until the sky is yours and mine
            [00:16.00]<00:16.00>Sing <00:16.60>it <00:17.00>out <00:17.60>loud
            [00:18.50]Let the words light up the clouds
            [00:22.00]Every word a little spark
            [00:25.50]Skysong, shining through the dark
            [00:29.50]
            """;

    private boolean playing = true;
    private long startedAt = System.nanoTime();

    public static boolean isDemo(NowPlaying track) {
        return track != null && PLAYER.equals(track.player()) && TITLE.equals(track.title());
    }

    @Override
    public String name() {
        return "the demo song";
    }

    /** Starts the demo from the beginning, unless it's already playing. */
    void restartIfStopped() {
        if (!playing) {
            playing = true;
            startedAt = System.nanoTime();
        }
    }

    void stop() {
        playing = false;
    }

    @Override
    public Optional<NowPlaying> poll() {
        if (!playing) {
            return Optional.empty();
        }
        long now = System.nanoTime();
        long position = ((now - startedAt) / 1_000_000L) % DURATION_MS;
        return Optional.of(new NowPlaying(TITLE, ARTIST, "", DURATION_MS, position, true, PLAYER, now));
    }
}
