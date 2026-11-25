package io.github.fernsehheft.skysong.render;

import io.github.fernsehheft.skysong.lyrics.TimedWord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Lays the words of a line out in rows, centred around (0, 0), in font pixels. Long
 * lines are wrapped so they don't stretch across the whole screen.
 */
public final class LineLayout {
    public record PlacedWord(TimedWord word, float x, float y, int width) {
    }

    private LineLayout() {
    }

    public static List<PlacedWord> layout(List<TimedWord> words, ToIntFunction<String> width, int spaceWidth,
                                          int maxRowWidth, int rowHeight) {
        return layoutWithWidths(words, word -> width.applyAsInt(word.text()), spaceWidth, maxRowWidth, rowHeight);
    }

    /** Like {@link #layout}, with the width decided per word, e.g. to give an emphasised word more room. */
    public static List<PlacedWord> layoutWithWidths(List<TimedWord> words, ToIntFunction<TimedWord> width,
                                                    int spaceWidth, int maxRowWidth, int rowHeight) {
        // Aim for rows of about the same width, so a long line becomes two halves
        // instead of a full row with one lonely word below it.
        int totalWidth = 0;
        for (TimedWord word : words) {
            totalWidth += (totalWidth == 0 ? 0 : spaceWidth) + width.applyAsInt(word);
        }
        int rowCount = Math.max(1, (totalWidth + maxRowWidth - 1) / maxRowWidth);
        float targetWidth = (float) totalWidth / rowCount;

        List<List<TimedWord>> rows = new ArrayList<>();
        List<Integer> rowWidths = new ArrayList<>();
        List<TimedWord> row = new ArrayList<>();
        int rowWidth = 0;
        for (TimedWord word : words) {
            int wordWidth = width.applyAsInt(word);
            int widthWithWord = row.isEmpty() ? wordWidth : rowWidth + spaceWidth + wordWidth;
            // A word moves to the next row once more than half of it would stick out.
            boolean pastTarget = widthWithWord - wordWidth / 2f > targetWidth;
            if (!row.isEmpty() && (pastTarget || widthWithWord > maxRowWidth)) {
                rows.add(row);
                rowWidths.add(rowWidth);
                row = new ArrayList<>();
                widthWithWord = wordWidth;
            }
            row.add(word);
            rowWidth = widthWithWord;
        }
        if (!row.isEmpty()) {
            rows.add(row);
            rowWidths.add(rowWidth);
        }

        List<PlacedWord> placed = new ArrayList<>(words.size());
        float top = -rows.size() * rowHeight / 2f;
        for (int r = 0; r < rows.size(); r++) {
            float x = -rowWidths.get(r) / 2f;
            float y = top + r * rowHeight;
            for (TimedWord word : rows.get(r)) {
                int wordWidth = width.applyAsInt(word);
                placed.add(new PlacedWord(word, x, y, wordWidth));
                x += wordWidth + spaceWidth;
            }
        }
        return placed;
    }
}
