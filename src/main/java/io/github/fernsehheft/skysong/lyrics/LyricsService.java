package io.github.fernsehheft.skysong.lyrics;

import io.github.fernsehheft.skysong.Skysong;
import io.github.fernsehheft.skysong.media.DemoSource;
import io.github.fernsehheft.skysong.media.NowPlaying;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Finds the lyrics whenever the song changes: the player's own lyrics folder first,
 * then the cache, then LRCLIB. Lookups run on a background thread; the game reads
 * the result through {@link #state()}.
 */
public final class LyricsService {
    public enum Status {
        /** Nothing is playing. */
        IDLE,
        SEARCHING,
        FOUND,
        NOT_FOUND,
        INSTRUMENTAL,
        /** Something went wrong, most likely no internet. */
        FAILED
    }

    /**
     * @param lyrics only set when the status is {@link Status#FOUND}
     * @param source where the lyrics came from, a short name for showing to the player
     */
    public record State(NowPlaying track, Status status, Lyrics lyrics, String source) {
        static final State IDLE = new State(null, Status.IDLE, null, null);
    }

    private final LocalLyricsFolder localFolder;
    private final LyricsCache cache;
    private final LrclibClient lrclib;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Skysong lyrics");
        thread.setDaemon(true);
        return thread;
    });

    private volatile State state = State.IDLE;

    public LyricsService(LocalLyricsFolder localFolder, LyricsCache cache, LrclibClient lrclib) {
        this.localFolder = localFolder;
        this.cache = cache;
        this.lrclib = lrclib;
    }

    public State state() {
        return state;
    }

    public synchronized void onTrackChanged(NowPlaying track) {
        if (track == null) {
            state = State.IDLE;
            return;
        }
        state = new State(track, Status.SEARCHING, null, null);
        executor.execute(() -> lookUp(track));
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void lookUp(NowPlaying track) {
        // When songs are skipped quickly, lookups for songs that are long gone pile up.
        if (!isCurrent(track)) {
            return;
        }
        if (DemoSource.isDemo(track)) {
            publish(track, LyricsText.synced(DemoSource.LYRICS), "Demo");
            return;
        }
        TrackQuery query = TrackQuery.of(track);
        try {
            Optional<LyricsText> own = localFolder.find(query);
            if (own.isPresent()) {
                publish(track, own.get(), "Your file");
                return;
            }

            Optional<LyricsCache.Entry> cached = cache.get(query);
            if (cached.isPresent()) {
                if (cached.get() instanceof LyricsCache.Entry.Found found) {
                    publish(track, found.text(), "LRCLIB");
                } else {
                    setIfCurrent(track, new State(track, Status.NOT_FOUND, null, null));
                }
                return;
            }

            Optional<LyricsText> downloaded = lrclib.find(query);
            if (downloaded.isPresent()) {
                cache.putFound(query, downloaded.get());
                publish(track, downloaded.get(), "LRCLIB");
            } else {
                cache.putMissing(query);
                setIfCurrent(track, new State(track, Status.NOT_FOUND, null, null));
            }
        } catch (IOException | RuntimeException e) {
            Skysong.LOGGER.warn("Couldn't get lyrics for {} - {}: {}", track.artist(), track.title(), e.toString());
            setIfCurrent(track, new State(track, Status.FAILED, null, null));
        }
    }

    private void publish(NowPlaying track, LyricsText text, String source) {
        Lyrics lyrics = text.toLyrics(track.durationMs());
        if (lyrics.isEmpty()) {
            Status status = text.instrumental() ? Status.INSTRUMENTAL : Status.NOT_FOUND;
            setIfCurrent(track, new State(track, status, null, null));
            return;
        }
        Skysong.LOGGER.info("Lyrics for {} from {} ({})", track.title(), source,
                lyrics.wordTimed() ? "word by word" : lyrics.synced() ? "synced" : "timing estimated");
        setIfCurrent(track, new State(track, Status.FOUND, lyrics, source));
    }

    private boolean isCurrent(NowPlaying track) {
        NowPlaying current = state.track();
        return current != null && current.isSameTrack(track);
    }

    private synchronized void setIfCurrent(NowPlaying track, State next) {
        if (isCurrent(track)) {
            state = next;
        }
    }
}
