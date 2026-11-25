package io.github.fernsehheft.skysong.lyrics;

import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.media.NowPlaying;
import io.github.fernsehheft.skysong.media.NowPlayingTracker;

import java.util.Optional;

/**
 * Puts together what's playing, its lyrics and the current position, so everything
 * that draws lyrics works from the same moment.
 */
public final class LyricsPlayback {
    private final NowPlayingTracker tracker;
    private final LyricsService lyrics;
    private final SkysongConfig config;
    private final SongOffsets songOffsets;

    /**
     * @param lyrics the lyrics of {@code track}, or null while they're missing or still being looked for
     * @param timeMs the position in the song with the timing corrections applied
     */
    public record Moment(NowPlaying track, LyricsService.Status status, Lyrics lyrics, String source, long timeMs) {
        /** Index of the line being sung, or -1 if there are no lyrics or none has started yet. */
        public int lineIndex() {
            return lyrics == null ? -1 : lyrics.lineIndexAt(timeMs);
        }

        public Optional<LyricLine> line() {
            int index = lineIndex();
            return index < 0 ? Optional.empty() : Optional.of(lyrics.lines().get(index));
        }

        public Optional<LyricLine> nextLine() {
            if (lyrics == null) {
                return Optional.empty();
            }
            for (int i = lineIndex() + 1; i < lyrics.lines().size(); i++) {
                if (!lyrics.lines().get(i).isBreak()) {
                    return Optional.of(lyrics.lines().get(i));
                }
            }
            return Optional.empty();
        }

        /** When the current line is over: where the next one starts, or a few seconds after its start for the last line. */
        public long lineEndMs() {
            int index = lineIndex();
            if (index < 0) {
                return timeMs;
            }
            if (index + 1 < lyrics.lines().size()) {
                return lyrics.lines().get(index + 1).startMs();
            }
            return lyrics.lines().get(index).startMs() + LAST_LINE_MS;
        }
    }

    /** How long the last line of a song stays, as it has no next line to end it. */
    static final long LAST_LINE_MS = 6000;

    public LyricsPlayback(NowPlayingTracker tracker, LyricsService lyrics, SkysongConfig config, SongOffsets songOffsets) {
        this.tracker = tracker;
        this.lyrics = lyrics;
        this.config = config;
        this.songOffsets = songOffsets;
    }

    public SongOffsets songOffsets() {
        return songOffsets;
    }

    /** What's happening right now, or empty when no music is playing. */
    public Optional<Moment> now() {
        NowPlaying track = tracker.current().orElse(null);
        if (track == null) {
            return Optional.empty();
        }
        LyricsService.State state = lyrics.state();
        boolean forThisTrack = track.isSameTrack(state.track());
        // Right after a song change the lyrics lookup may not have caught up yet.
        LyricsService.Status status = forThisTrack ? state.status() : LyricsService.Status.SEARCHING;
        Lyrics found = forThisTrack && status == LyricsService.Status.FOUND ? state.lyrics() : null;
        long time = track.positionAt(System.nanoTime()) + config.offsetMs + songOffsets.get(track);
        return Optional.of(new Moment(track, status, found, forThisTrack ? state.source() : null, time));
    }
}
