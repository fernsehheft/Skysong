package io.github.fernsehheft.skysong.render;

public final class Easing {
    private Easing() {
    }

    /** How far along {@code elapsedMs} is in an animation of {@code lengthMs}, from 0 to 1. */
    public static float progress(long elapsedMs, long lengthMs) {
        if (elapsedMs <= 0) {
            return 0;
        }
        return elapsedMs >= lengthMs ? 1 : (float) elapsedMs / lengthMs;
    }

    /** Starts fast and slows down at the end. */
    public static float easeOut(float t) {
        float inverse = 1 - t;
        return 1 - inverse * inverse * inverse;
    }

    /** Goes up to 1 and back to 0, for a short "pop". */
    public static float bump(float t) {
        return (float) Math.sin(Math.PI * t);
    }

    public static int lerpColor(int from, int to, float t) {
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
