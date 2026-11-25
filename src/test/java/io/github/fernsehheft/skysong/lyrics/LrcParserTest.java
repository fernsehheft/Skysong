package io.github.fernsehheft.skysong.lyrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrcParserTest {

    @Test
    void readsLinesAndIgnoresTags() {
        Lyrics lyrics = LrcParser.parse("""
                [ar:Someone]
                [ti:Some Song]
                [00:01.5]First line
                [00:04.25]  Second   line\s
                not a lyric line
                [00:08.120]
                """);
        assertEquals(3, lyrics.lines().size());
        assertEquals(new LyricLine(1500, "First line"), lyrics.lines().get(0));
        assertEquals(new LyricLine(4250, "Second line"), lyrics.lines().get(1));
        assertTrue(lyrics.lines().get(2).isBreak());
        assertTrue(lyrics.synced());
        assertFalse(lyrics.wordTimed());
    }

    @Test
    void repeatedTimestampsAreSortedIn() {
        Lyrics lyrics = LrcParser.parse("""
                [00:30.00][00:10.00]Chorus
                [00:20.00]Verse
                """);
        assertEquals(List.of(10_000L, 20_000L, 30_000L),
                lyrics.lines().stream().map(LyricLine::startMs).toList());
        assertEquals("Chorus", lyrics.lines().get(2).text());
    }

    @Test
    void offsetMovesEverythingEarlier() {
        Lyrics lyrics = LrcParser.parse("[offset:+300]\n[00:01.00]<00:01.00>Hey <00:01.50>you");
        LyricLine line = lyrics.lines().get(0);
        assertEquals(700, line.startMs());
        assertEquals(List.of(new LyricWord(700, "Hey"), new LyricWord(1200, "you")), line.words());
    }

    @Test
    void wordTiming() {
        Lyrics lyrics = LrcParser.parse("[00:12.00]<00:12.00>Word <00:12.40>by <00:12.90>word<00:13.50>");
        LyricLine line = lyrics.lines().get(0);
        assertEquals("Word by word", line.text());
        assertEquals(3, line.words().size());
        assertEquals(new LyricWord(12_900, "word"), line.words().get(2));
        assertTrue(lyrics.wordTimed());
    }

    @Test
    void findsTheLineForATime() {
        Lyrics lyrics = LrcParser.parse("[00:01.00]a\n[00:02.00]b\n[00:03.00]c");
        assertEquals(-1, lyrics.lineIndexAt(999));
        assertEquals(0, lyrics.lineIndexAt(1000));
        assertEquals(1, lyrics.lineIndexAt(2999));
        assertEquals(2, lyrics.lineIndexAt(60_000));
        assertTrue(new Lyrics(List.of(), true).lineAt(0).isEmpty());
    }
}
