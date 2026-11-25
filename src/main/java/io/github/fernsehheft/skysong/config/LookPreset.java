package io.github.fernsehheft.skysong.config;

import io.github.fernsehheft.skysong.render.Animation;

import java.util.Arrays;

/** Ready-made looks: an animation, colours, a karaoke style and effects that go well together. */
public enum LookPreset {
    /** Whatever the player set up themselves; choosing it changes nothing. */
    CUSTOM(null, null, null, null, 0, false, false),
    CLASSIC(Animation.KARAOKE, "#FFFFFF", "#FFD966", SkysongConfig.KaraokeStyle.PILL, 1.0, false, false),
    NEON(Animation.GLITCH, "#8AF7FF", "#FF4FD8", SkysongConfig.KaraokeStyle.MINIMAL, 1.1, true, false),
    SUNSET(Animation.RISE, "#FFE3C2", "#FF7A45", SkysongConfig.KaraokeStyle.PILL, 1.0, false, true),
    DREAMY(Animation.WAVE, "#E6E0FF", "#B79CFF", SkysongConfig.KaraokeStyle.MINIMAL, 0.9, true, false),
    BOLD(Animation.DROP, "#FFFFFF", "#FF3B3B", SkysongConfig.KaraokeStyle.PILL, 1.4, false, true);

    private final Animation animation;
    private final String textColor;
    private final String highlightColor;
    private final SkysongConfig.KaraokeStyle karaokeStyle;
    private final double size;
    private final boolean glow;
    private final boolean depth3d;

    LookPreset(Animation animation, String textColor, String highlightColor,
               SkysongConfig.KaraokeStyle karaokeStyle, double size, boolean glow, boolean depth3d) {
        this.animation = animation;
        this.textColor = textColor;
        this.highlightColor = highlightColor;
        this.karaokeStyle = karaokeStyle;
        this.size = size;
        this.glow = glow;
        this.depth3d = depth3d;
    }

    public void applyTo(SkysongConfig config) {
        if (this == CUSTOM) {
            return;
        }
        config.animation = animation;
        config.textColor = textColor;
        config.highlightColor = highlightColor;
        config.karaokeStyle = karaokeStyle;
        config.size = size;
        config.glow = glow;
        config.depth3d = depth3d;
    }

    /** The preset the settings currently match, or {@link #CUSTOM}. */
    public static LookPreset matching(SkysongConfig config) {
        return Arrays.stream(values())
                .filter(preset -> preset != CUSTOM && preset.matches(config))
                .findFirst()
                .orElse(CUSTOM);
    }

    private boolean matches(SkysongConfig config) {
        return config.animation == animation
                && textColor.equalsIgnoreCase(config.textColor)
                && highlightColor.equalsIgnoreCase(config.highlightColor)
                && config.karaokeStyle == karaokeStyle
                && Math.abs(config.size - size) < 0.001
                && config.glow == glow
                && config.depth3d == depth3d;
    }
}
