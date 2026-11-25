package io.github.fernsehheft.skysong.render;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineLayoutTest {
    /** Every letter is 6 pixels wide, a space is 4. */
    private static List<LineLayout.PlacedWord> layout(int maxRowWidth, String... words) {
        List<TimedWord> timed = Arrays.stream(words).map(word -> new TimedWord(word, 0)).toList();
        return LineLayout.layout(timed, text -> text.length() * 6, 4, maxRowWidth, 10);
    }

    @Test
    void shortLineStaysOnOneCentredRow() {
        List<LineLayout.PlacedWord> placed = layout(200, "ab", "cd");
        // "ab cd" is 12 + 4 + 12 = 28 pixels wide.
        assertEquals(-14, placed.get(0).x());
        assertEquals(2, placed.get(1).x());
        assertEquals(-5, placed.get(0).y());
        assertEquals(-5, placed.get(1).y());
    }

    @Test
    void longLineWraps() {
        List<LineLayout.PlacedWord> placed = layout(40, "aaaa", "bbbb", "cc");
        assertEquals(-10, placed.get(0).y());
        assertEquals(0, placed.get(1).y());
        assertEquals(0, placed.get(2).y());
        // Second row "bbbb cc" is 24 + 4 + 12 = 40 pixels wide.
        assertEquals(-20, placed.get(1).x());
    }

    @Test
    void aWordWiderThanARowGetsItsOwnRow() {
        List<LineLayout.PlacedWord> placed = layout(10, "aaaaaa", "b");
        assertEquals(-18, placed.get(0).x());
        assertEquals(0, placed.get(1).y());
    }

    @Test
    void wrappedRowsAreBalanced() {
        // Filling the first row greedily would give three words and then one.
        List<LineLayout.PlacedWord> placed = layout(100, "aaaaa", "bbbbb", "ccccc", "ddddd");
        assertEquals(placed.get(0).y(), placed.get(1).y());
        assertEquals(placed.get(2).y(), placed.get(3).y());
        assertEquals(-32, placed.get(0).x());
    }
}
