package io.github.fernsehheft.skysong.lyrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimedWordTest {

    @Test
    void usesRealWordTimingWhenThereIsSome() {
        LyricLine line = new LyricLine(1000, "Hey you", List.of(new LyricWord(1000, "Hey"), new LyricWord(1600, "you")));
        assertEquals(List.of(new TimedWord("Hey", 1000), new TimedWord("you", 1600)), TimedWord.of(line, 5000, false));
    }

    @Test
    void spreadsWordsOverTheLine() {
        List<TimedWord> words = TimedWord.of(new LyricLine(10_000, "We are never ever"), 14_000, true);
        assertEquals(4, words.size());
        assertEquals(10_000, words.get(0).startMs());
        for (int i = 1; i < words.size(); i++) {
            assertTrue(words.get(i).startMs() > words.get(i - 1).startMs());
        }
        // The last word starts before the line is over, leaving room to sing it.
        assertTrue(words.get(3).startMs() < 14_000 * 0.8 + 10_000 * 0.2);
    }

    @Test
    void longPausesDontStretchTheWords() {
        List<TimedWord> words = TimedWord.of(new LyricLine(0, "Short line"), 60_000, true);
        assertTrue(words.get(1).startMs() < 2_000);
    }

    @Test
    void emptyLine() {
        assertTrue(TimedWord.of(new LyricLine(0, "   "), 1000, true).isEmpty());
    }

    @Test
    void withoutGuessingTheWordsComeTogether() {
        List<TimedWord> words = TimedWord.of(new LyricLine(10_000, "Hold this note"), 20_000, false);
        assertEquals(List.of(10_000L, 10_060L, 10_120L), words.stream().map(TimedWord::startMs).toList());
    }
}
