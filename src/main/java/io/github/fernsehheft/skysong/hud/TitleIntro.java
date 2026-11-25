package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.media.NowPlaying;
import io.github.fernsehheft.skysong.render.Easing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** The song's title, big in the middle of the screen for a few seconds when a new song starts. */
final class TitleIntro {
    private static final long FADE_IN_MS = 500;
    private static final long HOLD_MS = 3000;
    private static final long FADE_OUT_MS = 800;
    private static final float TITLE_SCALE = 2.5f;
    private static final float MAX_WIDTH_SHARE = 0.8f;
    private static final int ARTIST_COLOR = 0xCCCCCC;
    private static final float TOP_SHARE = 0.58f;

    private final SkysongConfig config;
    private NowPlaying introduced;
    private long startedAt;

    TitleIntro(SkysongConfig config) {
        this.config = config;
    }

    void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, LyricsPlayback.Moment moment) {
        NowPlaying track = moment == null ? null : moment.track();
        if (track != null && !track.isSameTrack(introduced)) {
            introduced = track;
            startedAt = System.nanoTime();
        }
        if (!config.titleIntro || track == null || !track.isSameTrack(introduced)) {
            return;
        }

        long elapsed = (System.nanoTime() - startedAt) / 1_000_000L;
        if (elapsed > FADE_IN_MS + HOLD_MS + FADE_OUT_MS) {
            return;
        }
        float in = Easing.easeOut(Easing.progress(elapsed, FADE_IN_MS));
        float out = Easing.progress(elapsed - FADE_IN_MS - HOLD_MS, FADE_OUT_MS);
        float alpha = in * (1 - out);
        if (alpha <= 0.02f) {
            return;
        }

        int titleWidth = config.font.width(font, track.title());
        float titleScale = Math.min(TITLE_SCALE, screenWidth * MAX_WIDTH_SHARE / Math.max(1, titleWidth));
        float centerX = screenWidth / 2f;
        // Below the crosshair, so it doesn't cover lyrics floating straight ahead.
        // It drifts up a little while fading in.
        float top = screenHeight * TOP_SHARE + (1 - in) * 8;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, top, 0);
        graphics.pose().scale(titleScale, titleScale, 1);
        HudDraw.text(graphics, font, config.font.text(track.title()), -titleWidth / 2f, 0,
                HudDraw.withAlpha(config.textRgb(), alpha), true);
        graphics.pose().popPose();

        if (!track.artist().isEmpty()) {
            String artist = HudDraw.fit(font, track.artist(), Math.round(screenWidth * MAX_WIDTH_SHARE));
            HudDraw.text(graphics, font, artist, centerX - font.width(artist) / 2f,
                    top + font.lineHeight * titleScale + 6, HudDraw.withAlpha(ARTIST_COLOR, alpha), true);
        }
    }
}
