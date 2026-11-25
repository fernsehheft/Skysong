package io.github.fernsehheft.skysong.lyrics;

import java.util.ArrayList;
import java.util.List;

/** A word of a line together with the moment it's sung. */
public record TimedWord(String text, long startMs) {
    /** Share of a line's time that is spent singing; the rest is the pause after it. */
    private static final double SUNG_SHARE = 0.8;
    /** Nobody sings a single word for longer than this, even in a slow song. */
    private static final long MAX_MS_PER_LETTER = 180;

    /**
     * The words of {@code line} with their timing. Most lyrics only say when a line
     * starts, so then the words are spread over the line, longer words taking longer.
     *
     * @param endMs when the line is over, usually where the next one starts
     */
    public static List<TimedWord> of(LyricLine line, long endMs) {
        if (!line.words().isEmpty()) {
            List<TimedWord> words = new ArrayList<>(line.words().size());
            for (LyricWord word : line.words()) {
                words.add(new TimedWord(word.text(), word.startMs()));
            }
            return words;
        }

        String[] texts = line.text().strip().split("\\s+");
        if (texts.length == 1 && texts[0].isEmpty()) {
            return List.of();
        }
        long letters = 0;
        for (String text : texts) {
            letters += weight(text);
        }
        long duration = Math.round(Math.max(0, endMs - line.startMs()) * SUNG_SHARE);
        duration = Math.min(duration, letters * MAX_MS_PER_LETTER);

        List<TimedWord> words = new ArrayList<>(texts.length);
        long lettersBefore = 0;
        for (String text : texts) {
            words.add(new TimedWord(text, line.startMs() + duration * lettersBefore / letters));
            lettersBefore += weight(text);
        }
        return words;
    }

    private static int weight(String word) {
        // Even "a" takes a moment to sing.
        return word.length() + 2;
    }
}
