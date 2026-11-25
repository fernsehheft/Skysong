package io.github.fernsehheft.skysong.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads lyrics in the LRC format:
 *
 * <pre>
 * [ar:Sia]
 * [offset:+250]
 * [00:12.30]A line of the song
 * [00:15.80]&lt;00:15.80&gt;Word &lt;00:16.20&gt;by &lt;00:16.50&gt;word
 * [00:20.00][01:05.00]A chorus line that comes twice
 * </pre>
 *
 * Lines without a timestamp are ignored, as are tags we don't use.
 */
public final class LrcParser {
    private static final Pattern LINE_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET_TAG = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);

    private LrcParser() {
    }

    public static Lyrics parse(String lrc) {
        // A positive offset means the lyrics should show up earlier.
        long offset = 0;
        Matcher offsetMatcher = OFFSET_TAG.matcher(lrc);
        if (offsetMatcher.find()) {
            offset = Long.parseLong(offsetMatcher.group(1));
        }

        List<LyricLine> lines = new ArrayList<>();
        for (String rawLine : lrc.split("\\R")) {
            String line = rawLine.strip();
            Matcher time = LINE_TIME.matcher(line);
            List<Long> starts = new ArrayList<>();
            int textStart = 0;
            while (textStart < line.length()) {
                time.region(textStart, line.length());
                if (!time.lookingAt()) {
                    break;
                }
                starts.add(toMillis(time) - offset);
                textStart = time.end();
            }
            if (starts.isEmpty()) {
                continue;
            }

            String body = line.substring(textStart);
            String text = WORD_TIME.matcher(body).replaceAll("").replaceAll("\\s+", " ").strip();
            List<LyricWord> words = parseWords(body, -offset);
            long firstStart = starts.get(0);
            for (long start : starts) {
                // A line listed under several timestamps repeats with the same word spacing.
                lines.add(new LyricLine(start, text, shift(words, start - firstStart)));
            }
        }
        return new Lyrics(lines, true);
    }

    private static List<LyricWord> parseWords(String body, long shiftMs) {
        Matcher matcher = WORD_TIME.matcher(body);
        List<LyricWord> words = new ArrayList<>();
        long start = -1;
        int textStart = 0;
        while (matcher.find()) {
            addWord(words, start, body.substring(textStart, matcher.start()), shiftMs);
            start = toMillis(matcher);
            textStart = matcher.end();
        }
        addWord(words, start, body.substring(textStart), shiftMs);
        return words;
    }

    private static void addWord(List<LyricWord> words, long start, String text, long shiftMs) {
        // Text before the first word tag has no time of its own, and a closing tag
        // at the very end only marks when the last word ends.
        if (start >= 0 && !text.isBlank()) {
            words.add(new LyricWord(start + shiftMs, text.strip()));
        }
    }

    private static List<LyricWord> shift(List<LyricWord> words, long byMs) {
        if (byMs == 0) {
            return words;
        }
        return words.stream().map(word -> new LyricWord(word.startMs() + byMs, word.text())).toList();
    }

    private static long toMillis(Matcher time) {
        long minutes = Long.parseLong(time.group(1));
        long seconds = Long.parseLong(time.group(2));
        long millis = 0;
        String fraction = time.group(3);
        if (fraction != null) {
            // ".5" is half a second, ".45" is 450 ms and ".450" too.
            millis = Long.parseLong((fraction + "00").substring(0, 3));
        }
        return (minutes * 60 + seconds) * 1000 + millis;
    }
}
