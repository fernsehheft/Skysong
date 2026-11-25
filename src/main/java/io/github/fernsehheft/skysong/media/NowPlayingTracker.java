package io.github.fernsehheft.skysong.media;

import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Keeps asking a {@link MediaSource} what's playing, on its own thread, so the game
 * never waits for a slow player. The render thread only reads {@link #current()}.
 */
public final class NowPlayingTracker {
    private static final long POLL_INTERVAL_MS = 500;

    private final MediaSource source;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Skysong media");
        thread.setDaemon(true);
        return thread;
    });

    private final List<Consumer<NowPlaying>> trackListeners = new CopyOnWriteArrayList<>();
    private volatile NowPlaying current;
    private String lastError;

    public NowPlayingTracker(MediaSource source) {
        this.source = Objects.requireNonNull(source);
    }

    /**
     * Called on the media thread whenever a different song starts, or with null when
     * the music stops altogether. Pausing and seeking don't count as a change.
     */
    public void onTrackChange(Consumer<NowPlaying> listener) {
        trackListeners.add(listener);
    }

    public void start() {
        Skysong.LOGGER.info("Listening for music through {}", source.name());
        executor.scheduleWithFixedDelay(this::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        executor.shutdownNow();
        source.close();
    }

    public Optional<NowPlaying> current() {
        return Optional.ofNullable(current);
    }

    private void poll() {
        NowPlaying next;
        try {
            next = source.poll().orElse(null);
            lastError = null;
        } catch (IOException e) {
            // Log each problem once instead of twice a second.
            if (!Objects.equals(e.getMessage(), lastError)) {
                lastError = e.getMessage();
                Skysong.LOGGER.warn("Couldn't ask {} what's playing: {}", source.name(), e.getMessage());
            }
            next = null;
        } catch (RuntimeException e) {
            Skysong.LOGGER.error("Media source {} failed", source.name(), e);
            next = null;
        }

        NowPlaying previous = current;
        current = next;
        boolean changed = next == null ? previous != null : !next.isSameTrack(previous);
        if (!changed) {
            return;
        }
        if (next != null) {
            Skysong.LOGGER.info("Now playing: {} - {} ({})", next.artist(), next.title(), next.player());
        }
        for (Consumer<NowPlaying> listener : trackListeners) {
            try {
                listener.accept(next);
            } catch (RuntimeException e) {
                // Don't let one listener stop the polling for good.
                Skysong.LOGGER.error("Track change listener failed", e);
            }
        }
    }
}
