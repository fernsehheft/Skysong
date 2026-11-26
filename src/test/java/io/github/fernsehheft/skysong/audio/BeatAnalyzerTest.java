package io.github.fernsehheft.skysong.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatAnalyzerTest {
    private static final int RATE = BeatAnalyzer.SAMPLE_RATE;

    /** A made-up song: a kick drum on every beat, hi-hats in between and a quiet chord throughout. */
    private static float[] song(double bpm, double seconds, double loudness, long seed) {
        Random random = new Random(seed);
        int length = (int) (seconds * RATE);
        float[] samples = new float[length];
        double beat = 60 / bpm;
        for (int i = 0; i < length; i++) {
            double t = (double) i / RATE;
            double sinceBeat = t % beat;
            double sinceHat = (t + beat / 2) % beat;
            double kick = Math.sin(2 * Math.PI * 55 * sinceBeat) * Math.exp(-sinceBeat * 18);
            double hat = (random.nextDouble() * 2 - 1) * Math.exp(-sinceHat * 60) * 0.25;
            double chord = 0.08 * (Math.sin(2 * Math.PI * 330 * t) + Math.sin(2 * Math.PI * 415 * t));
            samples[i] = (float) (loudness * (0.7 * kick + hat + chord));
        }
        return samples;
    }

    /** Plays the samples into the analyzer in chunks, as they'd arrive from the sound card. */
    private static List<AudioState> play(BeatAnalyzer analyzer, float[] samples, long startNanos) {
        List<AudioState> states = new ArrayList<>();
        int chunk = 441;
        for (int offset = 0; offset < samples.length; offset += chunk) {
            int count = Math.min(chunk, samples.length - offset);
            float[] part = new float[count];
            System.arraycopy(samples, offset, part, 0, count);
            long end = startNanos + (long) (offset + count) * 1_000_000_000L / RATE;
            analyzer.accept(part, count, end);
            states.add(analyzer.state());
        }
        return states;
    }

    private static void assertTempo(double bpm) {
        BeatAnalyzer analyzer = new BeatAnalyzer();
        play(analyzer, song(bpm, 16, 0.8, 1), 0);
        AudioState state = analyzer.state();
        assertTrue(Math.abs(state.bpm() - bpm) < 3, "expected about " + bpm + " BPM, got " + state.bpm());
    }

    @Test
    void findsTheTempo() {
        assertTempo(90);
        assertTempo(120);
        assertTempo(140);
    }

    @Test
    void findsHardTechnoTempos() {
        assertTempo(150);
        assertTempo(160);
    }

    @Test
    void findsTheBeatsOnTheKicks() {
        BeatAnalyzer analyzer = new BeatAnalyzer();
        double seconds = 16;
        play(analyzer, song(120, seconds, 0.8, 2), 0);
        AudioState state = analyzer.state();
        // 32 kicks; the first couple go by while the analyzer is still getting a feel for the song.
        assertTrue(state.beatCount() >= 28 && state.beatCount() <= 33, "beats: " + state.beatCount());
        // The last beat is the last kick, at 15.5 s, give or take a frame.
        double lastBeatSeconds = state.lastBeatNanos() / 1e9;
        assertTrue(Math.abs(lastBeatSeconds - 15.5) < 0.06, "last beat at " + lastBeatSeconds);
    }

    @Test
    void silenceIsSilent() {
        BeatAnalyzer analyzer = new BeatAnalyzer();
        play(analyzer, new float[RATE], 0);
        AudioState state = analyzer.state();
        assertTrue(state.running());
        assertTrue(state.silent());
        assertEquals(0, state.beatCount());

        play(analyzer, song(120, 2, 0.8, 3), 1_000_000_000L);
        assertFalse(analyzer.state().silent());
    }

    @Test
    void noticesADrop() {
        BeatAnalyzer analyzer = new BeatAnalyzer();
        play(analyzer, song(128, 8, 0.08, 4), 0);
        assertTrue(analyzer.state().msSinceDrop(8_000_000_000L) > 100_000, "no drop while it stays quiet");
        play(analyzer, song(128, 3, 0.9, 5), 8_000_000_000L);
        long sinceDrop = analyzer.state().msSinceDrop(11_000_000_000L);
        // The drop is noticed within half a second of the music getting loud at 8 s.
        assertTrue(sinceDrop >= 2_400 && sinceDrop <= 3_000, "drop " + sinceDrop + " ms ago");
    }
}
