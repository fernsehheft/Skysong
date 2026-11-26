package io.github.fernsehheft.skysong.debug;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The last things Skysong wrote to the log, kept in memory so they can be shown in the
 * game and copied from there. Filled from any thread.
 */
public final class DebugLog {
    public enum Level {
        INFO, WARN, ERROR
    }

    public record Entry(LocalTime time, Level level, String message) {
        private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

        @Override
        public String toString() {
            return time.format(CLOCK) + " " + (level == Level.INFO ? "" : level + " ") + message;
        }
    }

    private static final int CAPACITY = 300;
    private static final DebugLog INSTANCE = new DebugLog();

    private final Deque<Entry> entries = new ArrayDeque<>();

    public static DebugLog get() {
        return INSTANCE;
    }

    public synchronized void add(Level level, String message) {
        if (entries.size() == CAPACITY) {
            entries.removeFirst();
        }
        entries.addLast(new Entry(LocalTime.now(), level, message));
    }

    /** The newest {@code count} entries, oldest first. */
    public synchronized List<Entry> latest(int count) {
        List<Entry> all = new ArrayList<>(entries);
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    /** Everything kept, one entry per line, for copying. */
    public synchronized String asText() {
        StringBuilder text = new StringBuilder();
        for (Entry entry : entries) {
            text.append(entry).append('\n');
        }
        return text.toString();
    }

    synchronized void clear() {
        entries.clear();
    }
}
