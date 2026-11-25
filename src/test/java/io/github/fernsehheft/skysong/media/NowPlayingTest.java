package io.github.fernsehheft.skysong.media;

import io.github.fernsehheft.skysong.lyrics.LrcParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NowPlayingTest {
    private static final long SECOND = 1_000_000_000L;

    private static NowPlaying track(long positionMs, long durationMs, boolean playing) {
        return new NowPlaying("Title", "Artist", "Album", durationMs, positionMs, playing, "Test", 0);
    }

    @Test
    void positionKeepsCountingWhilePlaying() {
        assertEquals(12_500, track(10_000, 200_000, true).positionAt(2 * SECOND + SECOND / 2));
    }

    @Test
    void positionStaysPutWhilePaused() {
        assertEquals(10_000, track(10_000, 200_000, false).positionAt(5 * SECOND));
    }

    @Test
    void positionNeverRunsPastTheEnd() {
        assertEquals(200_000, track(199_000, 200_000, true).positionAt(10 * SECOND));
    }

    @Test
    void sameTrackIgnoresPosition() {
        assertTrue(track(0, 1, true).isSameTrack(track(500, 1, false)));
        assertFalse(track(0, 1, true).isSameTrack(null));
    }

    @Test
    void demoSongHasLyricsThatParse() {
        NowPlaying demo = new DemoSource().poll().orElseThrow();
        assertTrue(DemoSource.isDemo(demo));
        assertTrue(demo.positionMs() < demo.durationMs());
        assertFalse(LrcParser.parse(DemoSource.LYRICS).isEmpty());
    }
}
