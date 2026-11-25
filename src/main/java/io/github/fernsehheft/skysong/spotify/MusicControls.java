package io.github.fernsehheft.skysong.spotify;

import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Play, pause, skip and change the volume from within the game. This goes through
 * Spotify's Web API, so it only works when listening through the Spotify login, and
 * Spotify only allows it for Premium accounts.
 */
public final class MusicControls {
    public enum Action {
        PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN
    }

    /** What happened, for telling the player; the detail is a volume or an error message. */
    public enum Result {
        PLAYING, PAUSED, NEXT, PREVIOUS, VOLUME,
        NEEDS_SPOTIFY, NO_VOLUME_CONTROL, NO_DEVICE, PREMIUM_REQUIRED, FAILED
    }

    private static final int VOLUME_STEP = 10;

    private final Spotify spotify;
    private final BooleanSupplier spotifyInUse;
    private final BiConsumer<Result, String> feedback;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Skysong music controls");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * @param spotifyInUse whether the music currently comes from Spotify
     * @param feedback     told how each action went, on a background thread
     */
    public MusicControls(Spotify spotify, BooleanSupplier spotifyInUse, BiConsumer<Result, String> feedback) {
        this.spotify = spotify;
        this.spotifyInUse = spotifyInUse;
        this.feedback = feedback;
    }

    public void run(Action action) {
        if (!spotifyInUse.getAsBoolean()) {
            feedback.accept(Result.NEEDS_SPOTIFY, "");
            return;
        }
        executor.execute(() -> {
            try {
                perform(action);
            } catch (SpotifyException e) {
                feedback.accept(switch (e.reason()) {
                    case NO_DEVICE -> Result.NO_DEVICE;
                    case PREMIUM_REQUIRED -> Result.PREMIUM_REQUIRED;
                    default -> Result.FAILED;
                }, e.getMessage());
            } catch (IOException e) {
                Skysong.LOGGER.warn("Music control {} failed: {}", action, e.getMessage());
                feedback.accept(Result.FAILED, e.getMessage());
            }
        });
    }

    private void perform(Action action) throws IOException {
        SpotifyApi api = spotify.api();
        SpotifySource.PlayerState state = spotify.source().playerState();
        switch (action) {
            case PLAY_PAUSE -> {
                boolean pause = state.playing();
                api.command("PUT", pause ? "/me/player/pause" : "/me/player/play");
                feedback.accept(pause ? Result.PAUSED : Result.PLAYING, "");
            }
            case NEXT -> {
                api.command("POST", "/me/player/next");
                feedback.accept(Result.NEXT, "");
            }
            case PREVIOUS -> {
                api.command("POST", "/me/player/previous");
                feedback.accept(Result.PREVIOUS, "");
            }
            case VOLUME_UP, VOLUME_DOWN -> {
                if (!state.volumeAllowed() || state.volumePercent() < 0) {
                    feedback.accept(Result.NO_VOLUME_CONTROL, "");
                    return;
                }
                int change = action == Action.VOLUME_UP ? VOLUME_STEP : -VOLUME_STEP;
                int volume = Math.max(0, Math.min(100, state.volumePercent() + change));
                api.command("PUT", "/me/player/volume?volume_percent=" + volume);
                feedback.accept(Result.VOLUME, Integer.toString(volume));
            }
        }
        spotify.source().refreshSoon();
    }

    public void stop() {
        executor.shutdownNow();
    }
}
