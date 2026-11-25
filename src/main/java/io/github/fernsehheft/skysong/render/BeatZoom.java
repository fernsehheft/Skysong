package io.github.fernsehheft.skysong.render;

/**
 * A short zoom-in of the view when a new line starts, like the cut to the next shot in
 * a music video. It's small on purpose: enough to feel the beat, not enough to get dizzy.
 */
public final class BeatZoom {
    /** How much narrower the field of view gets at the peak. */
    private static final double STRENGTH = 0.04;
    private static final long LENGTH_MS = 350;

    private static volatile long kickedAt = Long.MIN_VALUE;

    private BeatZoom() {
    }

    static void kick() {
        kickedAt = System.currentTimeMillis();
    }

    /** What to multiply the field of view with right now: 1 when nothing happens. */
    public static double fovMultiplier() {
        long elapsed = System.currentTimeMillis() - kickedAt;
        if (elapsed < 0 || elapsed >= LENGTH_MS) {
            return 1;
        }
        float t = (float) elapsed / LENGTH_MS;
        // Snap in fast, ease back out.
        float strength = t < 0.15f ? t / 0.15f : 1 - Easing.easeOut((t - 0.15f) / 0.85f);
        return 1 - STRENGTH * strength;
    }
}
