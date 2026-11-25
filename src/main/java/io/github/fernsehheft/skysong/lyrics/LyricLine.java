package io.github.fernsehheft.skysong.lyrics;

import java.util.List;

/**
 * A line of the song, starting at {@code startMs}. An empty {@code text} marks a break
 * where nothing is sung. {@code words} is empty unless the lyrics have word timing.
 */
public record LyricLine(long startMs, String text, List<LyricWord> words) {
    public LyricLine {
        words = List.copyOf(words);
    }

    public LyricLine(long startMs, String text) {
        this(startMs, text, List.of());
    }

    public boolean isBreak() {
        return text.isBlank();
    }
}
