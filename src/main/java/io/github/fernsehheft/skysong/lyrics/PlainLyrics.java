package io.github.fernsehheft.skysong.lyrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives lyrics without timestamps a rough timing, so they can still be shown along
 * with the song. Longer lines get more time than short ones.
 */
public final class PlainLyrics {
    /** Used when the player doesn't tell us how long the song is. */
    private static final long ASSUMED_DURATION_MS = 210_000;
    /** Most songs don't start singing right away and end with an outro. */
    private static final double INTRO_SHARE = 0.08;
    private static final double OUTRO_SHARE = 0.06;
    /** Every line gets a bit of time no matter how short it is. */
    private static final int MIN_WEIGHT = 12;

    private PlainLyrics() {
    }

    public static Lyrics spread(String text, long durationMs) {
        List<String> sung = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (sung.isEmpty()) {
            return new Lyrics(List.of(), false);
        }

        long duration = durationMs > 0 ? durationMs : ASSUMED_DURATION_MS;
        long start = Math.round(duration * INTRO_SHARE);
        long available = Math.round(duration * (1 - INTRO_SHARE - OUTRO_SHARE));
        long totalWeight = sung.stream().mapToLong(PlainLyrics::weight).sum();

        List<LyricLine> lines = new ArrayList<>(sung.size());
        long weightSoFar = 0;
        for (String line : sung) {
            lines.add(new LyricLine(start + available * weightSoFar / totalWeight, line));
            weightSoFar += weight(line);
        }
        return new Lyrics(lines, false);
    }

    private static long weight(String line) {
        return Math.max(MIN_WEIGHT, line.length());
    }
}
