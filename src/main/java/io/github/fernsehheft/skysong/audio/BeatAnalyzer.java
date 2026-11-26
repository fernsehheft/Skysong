package io.github.fernsehheft.skysong.audio;

/**
 * Listens to the sound that's playing and finds the beat, the tempo, how loud the music is
 * right now compared to the rest of the song, and drops (sudden jumps in loudness).
 *
 * <p>It works on blocks of {@value #FRAME} samples at {@value #SAMPLE_RATE} Hz (about 23 ms
 * each). Beats are found in the bass: a kick drum shows up as a sudden rise in low-frequency
 * energy. The tempo comes from how regularly those rises repeat.
 *
 * <p>Not thread-safe: feed it from one thread and read the published {@link AudioState}.
 */
public final class BeatAnalyzer {
    public static final int SAMPLE_RATE = 11_025;
    static final int FRAME = 256;
    static final double FRAMES_PER_SECOND = (double) SAMPLE_RATE / FRAME;

    /** Below this loudness (about -50 dB) for a while, nothing is playing. */
    private static final double SILENCE_RMS = 0.003;
    private static final double SILENCE_SECONDS = 0.3;
    /** Cut-off of the bass filter; kick drums and bass lines live below it. */
    private static final double BASS_CUTOFF_HZ = 150;
    /** How far back beats are looked at to work out the tempo. */
    private static final double TEMPO_WINDOW_SECONDS = 8;
    private static final double MIN_BPM = 70;
    private static final double MAX_BPM = 180;
    /** How far back the quietest and loudest moments are remembered, for the relative level. */
    private static final double LEVEL_WINDOW_SECONDS = 12;
    /** A rise of the level by this much within half a second is a drop. */
    private static final double DROP_RISE = 0.35;
    /** The level is smoothed over about this long, so single kicks don't count as getting louder. */
    private static final double LEVEL_SMOOTHING_SECONDS = 0.4;
    /** Take double the tempo when its beat fits at least this well compared to the slower one. */
    private static final double DOUBLE_TEMPO_SHARE = 0.6;

    private final double bassCoefficient;
    private double bassState;

    private final double[] frame = new double[FRAME];
    private int frameFill;
    private long frameCount;

    private final double[] onsets;
    /** The bass rises of the tempo window, newest first and slightly blurred; see {@link #estimateTempo}. */
    private double[] blurred = new double[0];
    private final double[] loudness;
    private double previousBass;
    private double smoothedRms;
    private final double smoothing = 1 - Math.exp(-1 / (LEVEL_SMOOTHING_SECONDS * FRAMES_PER_SECOND));
    private long lastBeatFrame = Long.MIN_VALUE / 2;
    private long beatCount;
    private double bpm;
    private long quietFrames;
    private long audibleFrames;
    private long lastDropFrame = Long.MIN_VALUE / 2;
    /** The relative level over the last half second, to notice drops. */
    private final double[] recentLevels = new double[(int) Math.ceil(0.5 * FRAMES_PER_SECOND)];

    private volatile AudioState state = AudioState.NONE;

    public BeatAnalyzer() {
        double dt = 1.0 / SAMPLE_RATE;
        double rc = 1.0 / (2 * Math.PI * BASS_CUTOFF_HZ);
        bassCoefficient = dt / (rc + dt);
        onsets = new double[(int) Math.ceil(TEMPO_WINDOW_SECONDS * FRAMES_PER_SECOND)];
        loudness = new double[(int) Math.ceil(LEVEL_WINDOW_SECONDS * FRAMES_PER_SECOND)];
    }

    public AudioState state() {
        return state;
    }

    /**
     * Feeds samples between -1 and 1.
     *
     * @param endNanos {@link System#nanoTime()} at which the last of these samples was played
     */
    public void accept(float[] samples, int count, long endNanos) {
        long samplesBefore = frameCount * FRAME + frameFill;
        for (int i = 0; i < count; i++) {
            frame[frameFill++] = samples[i];
            if (frameFill == FRAME) {
                long frameEndSample = (frameCount + 1) * FRAME;
                long samplesAfter = samplesBefore + count - frameEndSample;
                long frameEndNanos = endNanos - samplesAfter * 1_000_000_000L / SAMPLE_RATE;
                analyseFrame(frameEndNanos);
                frameFill = 0;
                frameCount++;
            }
        }
    }

    private void analyseFrame(long nanos) {
        double sumSquares = 0;
        double bassSquares = 0;
        for (int i = 0; i < FRAME; i++) {
            double sample = frame[i];
            sumSquares += sample * sample;
            bassState += bassCoefficient * (sample - bassState);
            bassSquares += bassState * bassState;
        }
        double rms = Math.sqrt(sumSquares / FRAME);
        double bass = Math.sqrt(bassSquares / FRAME);

        int slot = (int) (frameCount % onsets.length);
        // How much the bass rose since the last frame: a kick drum is a sudden rise.
        double onset = Math.max(0, bass - previousBass);
        previousBass = bass;
        onsets[slot] = onset;
        smoothedRms += smoothing * (rms - smoothedRms);
        loudness[(int) (frameCount % loudness.length)] = smoothedRms;

        quietFrames = rms < SILENCE_RMS ? quietFrames + 1 : 0;
        audibleFrames = rms < SILENCE_RMS ? 0 : audibleFrames + 1;
        boolean silent = quietFrames > SILENCE_SECONDS * FRAMES_PER_SECOND;

        if (frameCount % 16 == 0) {
            bpm = estimateTempo();
        }
        boolean beat = !silent && isBeat(onset);
        double level = relativeLevel(smoothedRms);
        if (!silent && isDrop(level)) {
            lastDropFrame = frameCount;
        }
        AudioState previous = state;
        state = new AudioState(true, silent, (float) level, (float) bpm,
                beat ? nanos : previous.lastBeatNanos(), beat ? ++beatCount : beatCount,
                lastDropFrame == frameCount ? nanos : previous.lastDropNanos());
    }

    /**
     * A beat is a bass rise that clearly stands out from the last second and a half, and
     * not too soon after the previous beat (half a beat at the current tempo, or 1/6 s).
     */
    private boolean isBeat(double onset) {
        int span = (int) (1.5 * FRAMES_PER_SECOND);
        double mean = 0;
        double squares = 0;
        for (int i = 1; i <= span; i++) {
            double value = onsets[(int) Math.floorMod(frameCount - i, (long) onsets.length)];
            mean += value;
            squares += value * value;
        }
        mean /= span;
        double deviation = Math.sqrt(Math.max(0, squares / span - mean * mean));
        double minGapFrames = bpm > 0 ? 0.5 * 60 / bpm * FRAMES_PER_SECOND : FRAMES_PER_SECOND / 6;
        boolean standsOut = onset > mean + 1.5 * deviation && onset > 1e-4;
        if (standsOut && frameCount - lastBeatFrame >= minGapFrames) {
            lastBeatFrame = frameCount;
            return true;
        }
        return false;
    }

    /**
     * The tempo whose beat lines up best with the bass rises of the last few seconds. The rises
     * are blurred a little first: a beat rarely falls exactly on a frame, and without the blur
     * that small miss adds up over the seconds and hides the real tempo.
     */
    private double estimateTempo() {
        int frames = (int) Math.min(onsets.length, frameCount);
        int minLag = (int) Math.floor(60 / MAX_BPM * FRAMES_PER_SECOND);
        int maxLag = (int) Math.ceil(60 / MIN_BPM * FRAMES_PER_SECOND);
        if (frames < maxLag * 3) {
            return bpm;
        }
        blurred = new double[frames];
        for (int i = 0; i < frames; i++) {
            double before = i > 0 ? onsetAgo(i - 1) : 0;
            double after = i + 1 < frames ? onsetAgo(i + 1) : 0;
            blurred[i] = 0.25 * before + 0.5 * onsetAgo(i) + 0.25 * after;
        }
        double best = 0;
        double bestLag = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double sum = correlation(lag, frames);
            // Slightly prefer tempos around 120, where most music is, to settle doubling doubts.
            double tempo = 60 * FRAMES_PER_SECOND / lag;
            double weight = Math.exp(-0.5 * Math.pow(Math.log(tempo / 120) / 0.7, 2));
            sum *= weight;
            if (sum > best) {
                best = sum;
                bestLag = lag;
            }
        }
        if (bestLag == 0 || best <= 0) {
            return bpm;
        }
        // Every tempo also fits at half speed; prefer the faster one when it fits nearly as well.
        int half = (int) Math.round(bestLag / 2);
        if (half >= minLag) {
            double halfFit = Math.max(correlation(half, frames), Math.max(correlation(half - 1, frames), correlation(half + 1, frames)));
            if (halfFit >= DOUBLE_TEMPO_SHARE * correlation((int) bestLag, frames)) {
                bestLag = half;
            }
        }
        // Refine between whole frames with a parabola through the neighbouring lags.
        double refined = refineLag((int) bestLag, frames);
        return 60 * FRAMES_PER_SECOND / refined;
    }

    private double refineLag(int lag, int frames) {
        double left = correlation(lag - 1, frames);
        double middle = correlation(lag, frames);
        double right = correlation(lag + 1, frames);
        double denominator = left - 2 * middle + right;
        if (denominator >= 0) {
            return lag;
        }
        return lag + 0.5 * (left - right) / denominator;
    }

    private double correlation(int lag, int frames) {
        double sum = 0;
        for (int i = 0; i + lag < frames; i++) {
            sum += blurred[i] * blurred[i + lag];
        }
        return sum;
    }

    private double onsetAgo(int framesAgo) {
        return onsets[(int) Math.floorMod(frameCount - framesAgo, (long) onsets.length)];
    }

    /** How loud this moment is between the quietest (0) and loudest (1) of the last seconds. */
    private double relativeLevel(double rms) {
        int frames = (int) Math.min(loudness.length, frameCount + 1);
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int i = 0; i < frames; i++) {
            double value = loudness[i];
            if (value >= SILENCE_RMS) {
                min = Math.min(min, value);
            }
            max = Math.max(max, value);
        }
        if (max <= SILENCE_RMS || min == Double.MAX_VALUE || max - min < 1e-6) {
            return rms > SILENCE_RMS ? 1 : 0;
        }
        return Math.max(0, Math.min(1, (rms - min) / (max - min)));
    }

    /** A drop: the music got much louder within the last half second, and not just after another drop. */
    private boolean isDrop(double level) {
        int slot = (int) (frameCount % recentLevels.length);
        double levelHalfASecondAgo = recentLevels[slot];
        recentLevels[slot] = level;
        boolean longEnoughSince = frameCount - lastDropFrame > 4 * FRAMES_PER_SECOND;
        // Music starting after silence isn't a drop; a drop needs music before it.
        boolean musicBefore = audibleFrames > 2 * FRAMES_PER_SECOND;
        return musicBefore && longEnoughSince && level - levelHalfASecondAgo > DROP_RISE;
    }
}
