package io.github.fernsehheft.skysong.lyrics;

import java.util.List;

/**
 * Lyrics as we got them, before parsing.
 *
 * @param synced       LRC text with timestamps, or null
 * @param plain        lyrics without timing, or null
 * @param instrumental the song is known to have no vocals
 * @param timeScale    stretch the timestamps by this, for lyrics of the original song used
 *                     for a faster or slower version of it; 1 (or 0 in old cache files) for none
 */
public record LyricsText(String synced, String plain, boolean instrumental, double timeScale) {
    public LyricsText(String synced, String plain, boolean instrumental) {
        this(synced, plain, instrumental, 1);
    }

    public static LyricsText synced(String lrc) {
        return new LyricsText(lrc, null, false);
    }

    public static LyricsText plain(String text) {
        return new LyricsText(null, text, false);
    }

    public LyricsText stretchedBy(double scale) {
        return new LyricsText(synced, plain, instrumental, scale);
    }

    private boolean stretched() {
        return timeScale > 0 && Math.abs(timeScale - 1) > 0.001;
    }

    public boolean hasSynced() {
        return synced != null && !synced.isBlank();
    }

    public boolean hasPlain() {
        return plain != null && !plain.isBlank();
    }

    /** Turns the text into lyrics, preferring real timing over guessed timing. */
    public Lyrics toLyrics(long durationMs) {
        if (hasSynced()) {
            Lyrics lyrics = LrcParser.parse(synced);
            if (!lyrics.isEmpty()) {
                return stretched() ? lyrics.stretchedBy(timeScale) : lyrics;
            }
        }
        if (hasPlain()) {
            return PlainLyrics.spread(plain, durationMs);
        }
        return new Lyrics(List.of(), true);
    }
}
