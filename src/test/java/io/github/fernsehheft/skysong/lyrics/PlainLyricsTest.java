package io.github.fernsehheft.skysong.lyrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainLyricsTest {

    @Test
    void spreadsLinesOverTheSong() {
        Lyrics lyrics = PlainLyrics.spread("One\n\nTwo\nThree\n", 100_000);
        List<LyricLine> lines = lyrics.lines();
        assertEquals(3, lines.size());
        assertFalse(lyrics.synced());
        assertEquals(8_000, lines.get(0).startMs());
        assertTrue(lines.get(1).startMs() > lines.get(0).startMs());
        assertTrue(lines.get(2).startMs() < 94_000);
    }

    @Test
    void longerLinesGetMoreTime() {
        Lyrics lyrics = PlainLyrics.spread(
                "This line is quite a bit longer than the others\nShort\nShort", 100_000);
        long first = lyrics.lines().get(1).startMs() - lyrics.lines().get(0).startMs();
        long second = lyrics.lines().get(2).startMs() - lyrics.lines().get(1).startMs();
        assertTrue(first > second);
    }

    @Test
    void emptyText() {
        assertTrue(PlainLyrics.spread("\n  \n", 100_000).isEmpty());
    }
}
