package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import net.minecraft.client.gui.GuiGraphics;

/** Black bars that slide in at the top and bottom of the screen while music plays. */
final class CinematicBars {
    /** How quickly the bars follow the music starting and stopping, per second. */
    private static final float SPEED = 3f;

    private final SkysongConfig config;
    private float shown;

    CinematicBars(SkysongConfig config) {
        this.config = config;
    }

    void render(GuiGraphics graphics, int screenWidth, int screenHeight, LyricsPlayback.Moment moment, float seconds) {
        boolean wanted = config.cinematicBars && moment != null && moment.track().playing();
        float step = SPEED * seconds;
        shown = wanted ? Math.min(1, shown + step) : Math.max(0, shown - step);
        if (shown <= 0) {
            return;
        }
        // Smoothstep, so the bars ease in and out instead of moving at a constant speed.
        float eased = shown * shown * (3 - 2 * shown);
        int barHeight = Math.round((float) (screenHeight * config.cinematicBarSize) * eased);
        graphics.fill(0, 0, screenWidth, barHeight, 0xFF000000);
        graphics.fill(0, screenHeight - barHeight, screenWidth, screenHeight, 0xFF000000);
    }
}
