package io.github.fernsehheft.skysong.media;

import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private volatile NowPlaying current;
    private String lastError;

    public NowPlayingTracker(MediaSource source) {
        this.source = Objects.requireNonNull(source);
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

        if (next != null && !next.isSameTrack(current)) {
            Skysong.LOGGER.info("Now playing: {} - {} ({})", next.artist(), next.title(), next.player());
        }
        current = next;
    }
}
