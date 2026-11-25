package io.github.fernsehheft.skysong.lyrics;

import java.util.List;

/**
 * Lyrics as we got them, before parsing.
 *
 * @param synced       LRC text with timestamps, or null
 * @param plain        lyrics without timing, or null
 * @param instrumental the song is known to have no vocals
 */
public record LyricsText(String synced, String plain, boolean instrumental) {
    public static LyricsText synced(String lrc) {
        return new LyricsText(lrc, null, false);
    }

    public static LyricsText plain(String text) {
        return new LyricsText(null, text, false);
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
                return lyrics;
            }
        }
        if (hasPlain()) {
            return PlainLyrics.spread(plain, durationMs);
        }
        return new Lyrics(List.of(), true);
    }
}
