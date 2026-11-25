package io.github.fernsehheft.skysong.media;

import java.util.Objects;

/**
 * A snapshot of what a media player reported at one moment.
 *
 * @param durationMs     track length, or 0 if the player didn't tell us
 * @param positionMs     playback position at the time of the snapshot
 * @param player         name of the app that is playing, for display and logs
 * @param sampledAtNanos {@link System#nanoTime()} when the position was read
 */
public record NowPlaying(
        String title,
        String artist,
        String album,
        long durationMs,
        long positionMs,
        boolean playing,
        String player,
        long sampledAtNanos) {

    public NowPlaying {
        Objects.requireNonNull(title, "title");
        artist = artist == null ? "" : artist;
        album = album == null ? "" : album;
        player = player == null ? "" : player;
    }

    /**
     * Where playback should be at {@code nanoTime}. Players are only asked a couple of
     * times per second, so in between we just keep counting from the last snapshot.
     */
    public long positionAt(long nanoTime) {
        if (!playing) {
            return positionMs;
        }
        long elapsedMs = Math.max(0, (nanoTime - sampledAtNanos) / 1_000_000L);
        long position = positionMs + elapsedMs;
        return durationMs > 0 ? Math.min(position, durationMs) : position;
    }

    public boolean isSameTrack(NowPlaying other) {
        return other != null
                && title.equals(other.title)
                && artist.equals(other.artist)
                && album.equals(other.album);
    }
}
