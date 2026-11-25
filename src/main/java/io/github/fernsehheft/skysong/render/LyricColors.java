package io.github.fernsehheft.skysong.render;

import io.github.fernsehheft.skysong.config.SkysongConfig;

import java.awt.Color;

/** The colour of a word that isn't lit up, for the colour mode the player picked. */
public final class LyricColors {
    /** How long the gradient takes to flow once through a line and back. */
    private static final double GRADIENT_CYCLE_MS = 4000;
    private static final double RAINBOW_CYCLE_MS = 6000;
    /** How much the hue moves on from one word to the next. */
    private static final float RAINBOW_STEP_PER_WORD = 0.07f;
    private static final float RAINBOW_SATURATION = 0.55f;

    /** How long a word stays lit up before it fades back into the gradient or rainbow. */
    private static final long LIT_MS = 150;
    private static final long BACK_TO_BASE_MS = 500;

    private LyricColors() {
    }

    /**
     * With a gradient or rainbow, a sung word lights up in the highlight colour and then
     * goes back to its moving colour, so the whole line keeps its colours.
     */
    public static int afterSinging(int base, int highlight, long sinceSung) {
        if (sinceSung < 0) {
            return base;
        }
        return Easing.lerpColor(highlight, base, Easing.progress(sinceSung - LIT_MS, BACK_TO_BASE_MS));
    }

    /**
     * @param index    the word's place in the line
     * @param count    how many words the line has
     * @param clockMs  a clock that keeps running, so the colours move
     */
    public static int base(SkysongConfig config, int index, int count, long clockMs) {
        return switch (config.colorMode) {
            case SINGLE -> config.textRgb();
            case GRADIENT -> {
                double along = count <= 1 ? 0 : (double) index / (count - 1);
                // Rises and falls smoothly, so the colours flow back and forth instead of jumping.
                double phase = along + clockMs / GRADIENT_CYCLE_MS;
                float mix = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI));
                yield Easing.lerpColor(config.gradientStartRgb(), config.gradientEndRgb(), mix);
            }
            case RAINBOW -> {
                float hue = (float) ((index * RAINBOW_STEP_PER_WORD + clockMs / RAINBOW_CYCLE_MS) % 1.0);
                yield Color.HSBtoRGB(hue, RAINBOW_SATURATION, 1f) & 0xFFFFFF;
            }
        };
    }
}
