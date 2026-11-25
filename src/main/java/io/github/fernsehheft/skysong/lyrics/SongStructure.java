package io.github.fernsehheft.skysong.lyrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What can be told about a song from its lyrics alone: which lines come back (the chorus),
 * where the song's title is sung, which words are background vocals and which notes are held.
 */
public final class SongStructure {
    /** Shorter lines ("Oh", "Yeah") come back in every song without being a chorus. */
    private static final int MIN_CHORUS_LETTERS = 8;
    /** A word sung for longer than this counts as a held note. */
    public static final long HELD_NOTE_MS = 1000;

    private SongStructure() {
    }

    /** For every line, whether the same words are sung somewhere else in the song too. */
    public static boolean[] repeatedLines(Lyrics lyrics) {
        List<LyricLine> lines = lyrics.lines();
        Map<String, Integer> counts = new HashMap<>();
        String[] keys = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String key = TrackQuery.simplify(lines.get(i).text());
            keys[i] = key;
            if (key.length() >= MIN_CHORUS_LETTERS) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        boolean[] repeated = new boolean[lines.size()];
        for (int i = 0; i < keys.length; i++) {
            repeated[i] = counts.getOrDefault(keys[i], 0) > 1;
        }
        return repeated;
    }

    /**
     * Which of the words spell out the song's title, e.g. "Shape", "of", "You" in
     * "I'm in love with the shape of you". Titles shorter than three letters are left out,
     * as they'd light up half the song.
     */
    public static boolean[] titleWords(List<String> words, String title) {
        boolean[] marked = new boolean[words.size()];
        List<String> wanted = new ArrayList<>();
        for (String part : title.split("\\s+")) {
            String simple = TrackQuery.simplify(part);
            if (!simple.isEmpty()) {
                wanted.add(simple);
            }
        }
        if (wanted.isEmpty() || String.join("", wanted).length() < 3) {
            return marked;
        }
        List<String> simple = words.stream().map(TrackQuery::simplify).toList();
        for (int start = 0; start + wanted.size() <= simple.size(); start++) {
            if (simple.subList(start, start + wanted.size()).equals(wanted)) {
                Arrays.fill(marked, start, start + wanted.size(), true);
            }
        }
        return marked;
    }

    /** Which words are in brackets, like "(oh-oh)": usually background vocals. */
    public static boolean[] backingWords(List<String> words) {
        boolean[] backing = new boolean[words.size()];
        boolean open = false;
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            if (word.startsWith("(")) {
                open = true;
            }
            backing[i] = open;
            if (word.endsWith(")")) {
                open = false;
            }
        }
        return backing;
    }

    /**
     * How long each word is held: until the next word, or the end of the line for the last.
     * Only meaningful with real word timing; guessed timing says nothing about held notes.
     */
    public static long[] wordLengths(List<TimedWord> words, long lineEndMs) {
        long[] lengths = new long[words.size()];
        for (int i = 0; i < words.size(); i++) {
            long end = i + 1 < words.size() ? words.get(i + 1).startMs() : lineEndMs;
            lengths[i] = Math.max(0, end - words.get(i).startMs());
        }
        return lengths;
    }
}
