package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.media.NowPlaying;
import io.github.fernsehheft.skysong.media.NowPlayingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A plain text readout of the current track in the top left corner. Mostly there to
 * check that the media sources work; the real lyrics display comes later.
 */
public final class NowPlayingOverlay {
    private static final int MARGIN = 6;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int DETAIL_COLOR = 0xFFAAAAAA;

    private final NowPlayingTracker tracker;
    private boolean visible;

    public NowPlayingOverlay(NowPlayingTracker tracker) {
        this.tracker = tracker;
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
        NowPlaying track = tracker.current().orElse(null);
        if (track == null) {
            graphics.drawString(font, "Nothing is playing", MARGIN, MARGIN, DETAIL_COLOR);
            return;
        }

        String time = formatTime(track.positionAt(System.nanoTime()));
        if (track.durationMs() > 0) {
            time += " / " + formatTime(track.durationMs());
        }
        if (!track.playing()) {
            time += "  (paused)";
        }

        graphics.drawString(font, "♪ " + track.title(), MARGIN, MARGIN, TITLE_COLOR);
        String details = track.artist().isEmpty() ? time : track.artist() + "  ·  " + time;
        graphics.drawString(font, details, MARGIN, MARGIN + font.lineHeight + 2, DETAIL_COLOR);
    }

    static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
