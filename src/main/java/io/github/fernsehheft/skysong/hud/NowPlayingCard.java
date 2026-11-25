package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.lyrics.LyricsService;
import io.github.fernsehheft.skysong.media.NowPlaying;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A small card with the song, a progress bar and whether lyrics were found. */
final class NowPlayingCard implements MovableElement {
    private static final int PADDING = 6;
    private static final int MIN_WIDTH = 110;
    private static final int MAX_TEXT_WIDTH = 170;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int DETAIL_COLOR = 0xAAAAAA;
    private static final int TRACK_COLOR = 0x555555;
    private static final int BACKGROUND = 0x000000;
    private static final float BACKGROUND_ALPHA = 0.6f;

    private final SkysongConfig config;

    NowPlayingCard(SkysongConfig config) {
        this.config = config;
    }

    /** The texts on the card, already cut to fit. */
    private record Content(String title, String artist, String time, String status, float progress, int textWidth) {
    }

    @Override
    public String name() {
        return "Now playing";
    }

    @Override
    public boolean enabled() {
        return config.nowPlayingCard;
    }

    private static Content content(Font font, LyricsPlayback.Moment moment, boolean preview) {
        String title;
        String artist;
        String time;
        String status;
        float progress;
        if (moment != null) {
            NowPlaying track = moment.track();
            long position = track.positionAt(System.nanoTime());
            title = track.title();
            artist = track.artist();
            time = HudDraw.formatTime(position) + (track.durationMs() > 0 ? " / " + HudDraw.formatTime(track.durationMs()) : "");
            if (!track.playing()) {
                time += "  paused";
            }
            status = statusText(moment);
            progress = track.durationMs() > 0 ? Math.min(1f, (float) position / track.durationMs()) : 0;
        } else if (preview) {
            title = "Song title";
            artist = "Artist";
            time = "1:23 / 3:45";
            status = "Lyrics: LRCLIB";
            progress = 0.37f;
        } else {
            return null;
        }

        int textWidth = Math.max(MIN_WIDTH, Math.min(MAX_TEXT_WIDTH,
                Math.max(font.width(title), Math.max(font.width(artist), font.width(time) + font.width(status) + 10))));
        return new Content(HudDraw.fit(font, title, textWidth), HudDraw.fit(font, artist, textWidth),
                time, HudDraw.fit(font, status, textWidth - font.width(time) - 10), progress, textWidth);
    }

    private static String statusText(LyricsPlayback.Moment moment) {
        return switch (moment.status()) {
            case FOUND -> "Lyrics: " + moment.source();
            case IDLE, SEARCHING -> "Looking for lyrics...";
            case NOT_FOUND -> "No lyrics found";
            case INSTRUMENTAL -> "Instrumental";
            case FAILED -> "No internet?";
        };
    }

    private static int width(Content content) {
        return content.textWidth() + PADDING * 2;
    }

    private static int height(Font font) {
        // Title, artist, progress bar, time.
        return PADDING * 2 + font.lineHeight * 3 + 2 + 3 + 6;
    }

    @Override
    public HudBox box(Font font, int screenWidth, int screenHeight, LyricsPlayback.Moment moment, boolean preview) {
        Content content = content(font, moment, preview);
        if (content == null) {
            return null;
        }
        return HudBox.centeredAt(config.cardX, config.cardY, width(content), height(font),
                (float) config.cardScale, screenWidth, screenHeight);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       LyricsPlayback.Moment moment, boolean preview) {
        Content content = content(font, moment, preview);
        if (content == null) {
            return;
        }
        int width = width(content);
        int height = height(font);
        HudBox box = HudBox.centeredAt(config.cardX, config.cardY, width, height,
                (float) config.cardScale, screenWidth, screenHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(box.x(), box.y(), 0);
        graphics.pose().scale(box.scale(), box.scale(), 1);
        HudDraw.roundedRect(graphics, 0, 0, width, height, 5, HudDraw.withAlpha(BACKGROUND, BACKGROUND_ALPHA));

        int y = PADDING;
        graphics.drawString(font, content.title(), PADDING, y, HudDraw.withAlpha(TITLE_COLOR, 1), false);
        y += font.lineHeight + 2;
        graphics.drawString(font, content.artist(), PADDING, y, HudDraw.withAlpha(DETAIL_COLOR, 1), false);
        y += font.lineHeight + 3;

        int barWidth = width - PADDING * 2;
        graphics.fill(PADDING, y, PADDING + barWidth, y + 2, HudDraw.withAlpha(TRACK_COLOR, 1));
        graphics.fill(PADDING, y, PADDING + Math.round(barWidth * content.progress()), y + 2,
                HudDraw.withAlpha(config.highlightRgb(), 1));
        y += 6;

        graphics.drawString(font, content.time(), PADDING, y, HudDraw.withAlpha(DETAIL_COLOR, 1), false);
        graphics.drawString(font, content.status(), width - PADDING - font.width(content.status()), y,
                HudDraw.withAlpha(DETAIL_COLOR, 1), false);
        graphics.pose().popPose();
    }

    @Override
    public void moveTo(double fractionX, double fractionY) {
        config.cardX = fractionX;
        config.cardY = fractionY;
    }

    @Override
    public void resize(double scaleChange) {
        config.cardScale = KaraokeBar.clampScale(config.cardScale + scaleChange);
    }

    @Override
    public void reset() {
        config.cardX = SkysongConfig.DEFAULT_CARD_X;
        config.cardY = SkysongConfig.DEFAULT_CARD_Y;
        config.cardScale = 1.0;
    }
}
