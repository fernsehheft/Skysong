package io.github.fernsehheft.skysong.lyrics;

import io.github.fernsehheft.skysong.media.DemoSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongStructureTest {

    @Test
    void repeatedLinesAreTheChorus() {
        Lyrics lyrics = LrcParser.parse("""
                [00:01.00]A verse that's sung once
                [00:05.00]I'm in love with the shape of you
                [00:09.00]Oh
                [00:12.00]I'm in love with the Shape of You!
                [00:15.00]Oh
                """);
        assertArrayEquals(new boolean[]{false, true, false, true, false}, SongStructure.repeatedLines(lyrics));
    }

    @Test
    void findsTheTitleInTheLine() {
        List<String> words = List.of("I'm", "in", "love", "with", "the", "shape", "of", "you,");
        assertArrayEquals(new boolean[]{false, false, false, false, false, true, true, true},
                SongStructure.titleWords(words, "Shape of You"));
        assertArrayEquals(new boolean[]{false, false, true}, SongStructure.titleWords(List.of("Welcome", "to", "Theater!"), "Theater"));
        // Too short to mean anything.
        assertArrayEquals(new boolean[]{false, false}, SongStructure.titleWords(List.of("I", "go"), "Go"));
    }

    @Test
    void wordsInBracketsAreBackingVocals() {
        assertArrayEquals(new boolean[]{false, true, true, false},
                SongStructure.backingWords(List.of("Baby", "(oh", "oh)", "yeah")));
        assertArrayEquals(new boolean[]{true}, SongStructure.backingWords(List.of("(hey)")));
    }

    @Test
    void wordLengthsGoUntilTheNextWord() {
        List<TimedWord> words = List.of(new TimedWord("Hold", 1000), new TimedWord("on", 3500));
        assertArrayEquals(new long[]{2500, 500}, SongStructure.wordLengths(words, 4000));
    }

    @Test
    void theDemoSongShowsOffEverything() {
        Lyrics demo = LrcParser.parse(DemoSource.LYRICS);
        boolean[] chorus = SongStructure.repeatedLines(demo);
        int chorusLines = 0;
        for (boolean repeated : chorus) {
            chorusLines += repeated ? 1 : 0;
        }
        assertEquals(2, chorusLines);
        assertTrue(SongStructure.titleWords(List.of("Skysong,", "shining"), DemoSource.TITLE)[0]);
        assertTrue(demo.wordTimed());
        assertTrue(demo.lines().stream().anyMatch(line -> line.text().contains("(oh-oh)")));
    }
}
