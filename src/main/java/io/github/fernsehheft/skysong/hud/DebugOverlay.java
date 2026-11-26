package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.audio.AudioListener;
import io.github.fernsehheft.skysong.audio.AudioState;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.lyrics.LyricLine;
import io.github.fernsehheft.skysong.lyrics.Lyrics;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.media.NowPlaying;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Timing details in the top left corner, to find out why lyrics are early or late: is the
 * position the player reports off, or do the lyrics belong to a different version?
 */
final class DebugOverlay {
    private static final int MARGIN = 4;
    private static final int PADDING = 3;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int BACKGROUND = 0xA0000000;

    private final SkysongConfig config;
    private final LyricsPlayback playback;
    private final AudioListener audio;

    DebugOverlay(SkysongConfig config, LyricsPlayback playback, AudioListener audio) {
        this.config = config;
        this.playback = playback;
        this.audio = audio;
    }

    void render(GuiGraphics graphics, Font font, LyricsPlayback.Moment moment) {
        if (!config.debugOverlay) {
            return;
        }
        List<String> lines = lines(moment);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int lineHeight = font.lineHeight + 1;
        graphics.fill(MARGIN, MARGIN, MARGIN + width + PADDING * 2, MARGIN + lines.size() * lineHeight + PADDING * 2, BACKGROUND);
        int y = MARGIN + PADDING;
        for (String line : lines) {
            graphics.drawString(font, line, MARGIN + PADDING, y, TEXT_COLOR, false);
            y += lineHeight;
        }
    }

    private List<String> lines(LyricsPlayback.Moment moment) {
        List<String> lines = new ArrayList<>();
        lines.add("Skysong debug");
        AudioState sound = audio.state();
        if (!sound.running()) {
            lines.add("Audio: not listening" + (config.listenToMusic ? "" : " (switched off)"));
        } else if (sound.silent()) {
            lines.add("Audio: listening, silence");
        } else {
            lines.add(String.format(Locale.ROOT, "Audio: %.0f BPM, level %.2f, last beat %d ms ago",
                    sound.bpm(), sound.level(), Math.min(9999, sound.msSinceBeat(System.nanoTime()))));
        }
        if (moment == null) {
            lines.add("Nothing is playing");
            return lines;
        }
        NowPlaying track = moment.track();
        long position = track.positionAt(System.nanoTime());
        long songOffset = playback.songOffsets().get(track);
        lines.add("Track: " + track.artist() + " - " + track.title());
        lines.add("Player: " + track.player() + (track.playing() ? ", playing" : ", paused")
                + ", length " + time(track.durationMs()));
        lines.add("Player position: " + time(position) + "   lyrics time: " + time(moment.timeMs())
                + String.format(Locale.ROOT, "   (all songs %+d ms, this song %+d ms)", config.offsetMs, songOffset));

        Lyrics lyrics = moment.lyrics();
        if (lyrics == null) {
            lines.add("Lyrics: " + moment.status().name().toLowerCase(Locale.ROOT));
            return lines;
        }
        String timing = lyrics.wordTimed() ? "word timing" : lyrics.synced() ? "line timing" : "estimated timing";
        lines.add("Lyrics: " + moment.source() + ", " + timing + ", " + lyrics.lines().size() + " lines");
        int index = moment.lineIndex();
        if (index < 0) {
            LyricLine first = lyrics.lines().isEmpty() ? null : lyrics.lines().get(0);
            lines.add("Before the first line" + (first == null ? "" : ", which starts at " + time(first.startMs())));
        } else {
            LyricLine line = lyrics.lines().get(index);
            String next = index + 1 < lyrics.lines().size() ? time(lyrics.lines().get(index + 1).startMs()) : "-";
            lines.add("Line " + (index + 1) + " starts " + time(line.startMs()) + ", next " + next
                    + ": " + (line.isBreak() ? "(break)" : line.text()));
        }
        return lines;
    }

    private static String time(long millis) {
        long clamped = Math.max(0, millis);
        return String.format(Locale.ROOT, "%d:%02d.%02d", clamped / 60000, clamped / 1000 % 60, clamped % 1000 / 10);
    }
}
