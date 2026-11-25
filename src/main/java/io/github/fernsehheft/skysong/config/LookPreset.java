package io.github.fernsehheft.skysong.config;

import io.github.fernsehheft.skysong.render.Animation;

import java.util.Arrays;

/** Ready-made looks: an animation, colours and a karaoke style that go well together. */
public enum LookPreset {
    /** Whatever the player set up themselves; choosing it changes nothing. */
    CUSTOM(null, null, null, null, 0),
    CLASSIC(Animation.KARAOKE, "#FFFFFF", "#FFD966", SkysongConfig.KaraokeStyle.PILL, 1.0),
    NEON(Animation.FADE, "#8AF7FF", "#FF4FD8", SkysongConfig.KaraokeStyle.MINIMAL, 1.1),
    SUNSET(Animation.RISE, "#FFE3C2", "#FF7A45", SkysongConfig.KaraokeStyle.PILL, 1.0),
    DREAMY(Animation.FADE, "#E6E0FF", "#B79CFF", SkysongConfig.KaraokeStyle.MINIMAL, 0.9),
    BOLD(Animation.KARAOKE, "#FFFFFF", "#FF3B3B", SkysongConfig.KaraokeStyle.PILL, 1.4);

    private final Animation animation;
    private final String textColor;
    private final String highlightColor;
    private final SkysongConfig.KaraokeStyle karaokeStyle;
    private final double size;

    LookPreset(Animation animation, String textColor, String highlightColor,
               SkysongConfig.KaraokeStyle karaokeStyle, double size) {
        this.animation = animation;
        this.textColor = textColor;
        this.highlightColor = highlightColor;
        this.karaokeStyle = karaokeStyle;
        this.size = size;
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
                && Math.abs(config.size - size) < 0.001;
    }
}
