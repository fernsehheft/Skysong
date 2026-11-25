package io.github.fernsehheft.skysong.lyrics;

/** One word (or syllable) of a line, for lyrics that are timed word by word. */
public record LyricWord(long startMs, String text) {
}
