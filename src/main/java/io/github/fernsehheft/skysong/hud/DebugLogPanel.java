package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.debug.DebugLog;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.util.List;

/** The newest entries of Skysong's log, on screen, to see what the mod is doing. */
final class DebugLogPanel implements MovableElement {
    private static final int LINES = 8;
    private static final int PADDING = 4;
    private static final int TEXT_WIDTH = 260;
    private static final float TEXT_SCALE = 0.75f;
    private static final int BACKGROUND = 0x000000;
    private static final float BACKGROUND_ALPHA = 0.65f;
    private static final int TITLE_COLOR = 0xFFD966;
    private static final int INFO_COLOR = 0xE0E0E0;
    private static final int WARN_COLOR = 0xFFC94D;
    private static final int ERROR_COLOR = 0xFF6B6B;
    private static final List<DebugLog.Entry> PREVIEW = List.of(
            new DebugLog.Entry(LocalTime.of(12, 0, 1), DebugLog.Level.INFO, "Now playing: Artist - Song (Spotify)"),
            new DebugLog.Entry(LocalTime.of(12, 0, 2), DebugLog.Level.INFO, "Lyrics for Song from LRCLIB (synced)"),
            new DebugLog.Entry(LocalTime.of(12, 0, 3), DebugLog.Level.WARN, "Something that went wrong shows up like this"));

    private final SkysongConfig config;

    DebugLogPanel(SkysongConfig config) {
        this.config = config;
    }

    @Override
    public Component name() {
        return Component.translatable("skysong.hud.debug_log");
    }

    @Override
    public boolean enabled() {
        return config.debugLog;
    }

    private static List<DebugLog.Entry> entries(boolean preview) {
        List<DebugLog.Entry> entries = DebugLog.get().latest(LINES);
        return entries.isEmpty() && preview ? PREVIEW : entries;
    }

    private static int lineHeight(Font font) {
        return Math.round((font.lineHeight + 1) * TEXT_SCALE);
    }

    private static int width() {
        return Math.round(TEXT_WIDTH * TEXT_SCALE) + PADDING * 2;
    }

    private static int height(Font font, int lines) {
        // A title row, then the entries.
        return PADDING * 2 + lineHeight(font) * (lines + 1);
    }

    @Override
    public HudBox box(Font font, int screenWidth, int screenHeight, LyricsPlayback.Moment moment, boolean preview) {
        int lines = Math.max(1, entries(preview).size());
        return HudBox.centeredAt(config.debugLogX, config.debugLogY, width(), height(font, lines),
                (float) config.debugLogScale, screenWidth, screenHeight);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       LyricsPlayback.Moment moment, boolean preview) {
        List<DebugLog.Entry> entries = entries(preview);
        HudBox box = box(font, screenWidth, screenHeight, moment, preview);
        graphics.pose().pushPose();
        graphics.pose().translate(box.x(), box.y(), 0);
        graphics.pose().scale(box.scale(), box.scale(), 1);
        graphics.fill(0, 0, width(), height(font, Math.max(1, entries.size())),
                HudDraw.withAlpha(BACKGROUND, BACKGROUND_ALPHA));

        graphics.pose().pushPose();
        graphics.pose().translate(PADDING, PADDING, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        int y = 0;
        int step = font.lineHeight + 1;
        graphics.drawString(font, Component.translatable("skysong.hud.debug_log"), 0, y, HudDraw.withAlpha(TITLE_COLOR, 1), false);
        y += step;
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("skysong.hud.debug_log.empty"), 0, y,
                    HudDraw.withAlpha(INFO_COLOR, 0.7f), false);
        }
        for (DebugLog.Entry entry : entries) {
            int color = switch (entry.level()) {
                case INFO -> INFO_COLOR;
                case WARN -> WARN_COLOR;
                case ERROR -> ERROR_COLOR;
            };
            graphics.drawString(font, HudDraw.fit(font, entry.toString(), TEXT_WIDTH), 0, y, HudDraw.withAlpha(color, 1), false);
            y += step;
        }
        graphics.pose().popPose();
        graphics.pose().popPose();
    }

    @Override
    public void moveTo(double fractionX, double fractionY) {
        config.debugLogX = fractionX;
        config.debugLogY = fractionY;
    }

    @Override
    public void resize(double scaleChange) {
        config.debugLogScale = KaraokeBar.clampScale(config.debugLogScale + scaleChange);
    }

    @Override
    public void reset() {
        config.debugLogX = SkysongConfig.DEFAULT_DEBUG_LOG_X;
        config.debugLogY = SkysongConfig.DEFAULT_DEBUG_LOG_Y;
        config.debugLogScale = 1.0;
    }
}
