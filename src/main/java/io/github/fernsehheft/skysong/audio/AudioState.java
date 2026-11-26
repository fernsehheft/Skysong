package io.github.fernsehheft.skysong.audio;

/**
 * What the music sounds like right now.
 *
 * @param running       sound is being listened to at all
 * @param silent        nothing has been audible for a moment
 * @param level         how loud it is now, from the quietest (0) to the loudest (1) moment of the last seconds
 * @param bpm           the tempo, or 0 while it isn't known yet
 * @param lastBeatNanos {@link System#nanoTime()} of the last beat
 * @param beatCount     how many beats there have been, to notice new ones
 * @param lastDropNanos {@link System#nanoTime()} of the last drop, a sudden jump in loudness
 */
public record AudioState(boolean running, boolean silent, float level, float bpm,
                         long lastBeatNanos, long beatCount, long lastDropNanos) {
    public static final AudioState NONE = new AudioState(false, true, 0, 0, Long.MIN_VALUE / 2, 0, Long.MIN_VALUE / 2);

    /** Milliseconds since the last beat, or a large number if there was none. */
    public long msSinceBeat(long nowNanos) {
        return running ? (nowNanos - lastBeatNanos) / 1_000_000L : Long.MAX_VALUE / 2;
    }

    public long msSinceDrop(long nowNanos) {
        return running ? (nowNanos - lastDropNanos) / 1_000_000L : Long.MAX_VALUE / 2;
    }
}
