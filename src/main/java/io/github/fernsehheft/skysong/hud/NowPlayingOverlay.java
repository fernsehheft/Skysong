package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.lyrics.LyricLine;
import io.github.fernsehheft.skysong.lyrics.LyricsService;
import io.github.fernsehheft.skysong.media.NowPlaying;
import io.github.fernsehheft.skysong.media.NowPlayingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A plain text readout of the current track and lyric line in the top left corner.
 * Mostly there to check that finding music and lyrics works; the real lyrics display
 * in the world comes later.
 */
public final class NowPlayingOverlay {
    private static final int MARGIN = 6;
    private static final int LINE_GAP = 2;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int DETAIL_COLOR = 0xFFAAAAAA;
    private static final int LYRIC_COLOR = 0xFFFFE08A;

    private final NowPlayingTracker tracker;
    private final LyricsService lyrics;
    private boolean visible;

    public NowPlayingOverlay(NowPlayingTracker tracker, LyricsService lyrics) {
        this.tracker = tracker;
        this.lyrics = lyrics;
    }

    public void toggle() {
        visible = !visible;
    }

    public void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!visible || minecraft.options.hideGui || minecraft.options.renderDebug) {
            return;
        }

        Font font = minecraft.font;
        int y = MARGIN;
        NowPlaying track = tracker.current().orElse(null);
        if (track == null) {
            graphics.drawString(font, "Nothing is playing", MARGIN, y, DETAIL_COLOR);
            return;
        }

        long position = track.positionAt(System.nanoTime());
        String time = formatTime(position);
        if (track.durationMs() > 0) {
            time += " / " + formatTime(track.durationMs());
        }
        if (!track.playing()) {
            time += "  (paused)";
        }

        graphics.drawString(font, "♪ " + track.title(), MARGIN, y, TITLE_COLOR);
        y += font.lineHeight + LINE_GAP;
        String details = track.artist().isEmpty() ? time : track.artist() + "  ·  " + time;
        graphics.drawString(font, details, MARGIN, y, DETAIL_COLOR);
        y += font.lineHeight + LINE_GAP * 3;

        int lyricY = y;
        LyricsService.State state = lyrics.state();
        if (state.status() == LyricsService.Status.FOUND && track.isSameTrack(state.track())) {
            state.lyrics().lineAt(position)
                    .filter(line -> !line.isBreak())
                    .map(LyricLine::text)
                    .ifPresent(text -> graphics.drawString(font, text, MARGIN, lyricY, LYRIC_COLOR));
            String source = "Lyrics: " + state.source() + (state.lyrics().synced() ? "" : " (timing estimated)");
            graphics.drawString(font, source, MARGIN, y + font.lineHeight + LINE_GAP, DETAIL_COLOR);
        } else {
            graphics.drawString(font, statusText(state.status()), MARGIN, y, DETAIL_COLOR);
        }
    }

    private static String statusText(LyricsService.Status status) {
        return switch (status) {
            case IDLE, SEARCHING, FOUND -> "Looking for lyrics...";
            case NOT_FOUND -> "No lyrics found for this song";
            case INSTRUMENTAL -> "Instrumental";
            case FAILED -> "Couldn't load lyrics (no internet?)";
        };
    }

    static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
