package io.github.fernsehheft.skysong.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The lyrics of one song, as lines sorted by their start time. */
public final class Lyrics {
    private final List<LyricLine> lines;
    private final boolean synced;

    /**
     * @param synced false when the timing was guessed from plain text rather than
     *               coming with the lyrics
     */
    public Lyrics(List<LyricLine> lines, boolean synced) {
        List<LyricLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparingLong(LyricLine::startMs));
        this.lines = List.copyOf(sorted);
        this.synced = synced;
    }

    public List<LyricLine> lines() {
        return lines;
    }

    public boolean synced() {
        return synced;
    }

    public boolean wordTimed() {
        return lines.stream().anyMatch(line -> !line.words().isEmpty());
    }

    public boolean isEmpty() {
        return lines.stream().allMatch(LyricLine::isBreak);
    }

    /** Index of the line that is being sung at {@code timeMs}, or -1 before the first one. */
    public int lineIndexAt(long timeMs) {
        int low = 0;
        int high = lines.size() - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).startMs() <= timeMs) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }

    public Optional<LyricLine> lineAt(long timeMs) {
        int index = lineIndexAt(timeMs);
        return index < 0 ? Optional.empty() : Optional.of(lines.get(index));
    }
}
