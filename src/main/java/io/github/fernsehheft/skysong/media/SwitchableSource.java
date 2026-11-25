package io.github.fernsehheft.skysong.media;

import io.github.fernsehheft.skysong.config.MusicSource;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.spotify.Spotify;
import io.github.fernsehheft.skysong.spotify.SpotifyException;

import java.io.IOException;
import java.util.Optional;

/**
 * Picks where the music comes from at every poll: the demo song while it's switched on,
 * otherwise Spotify or the system's music player as the settings say. Changes in the
 * settings therefore work right away, without restarting the game.
 */
public final class SwitchableSource implements MediaSource {
    public enum Active {
        DEMO, SPOTIFY, SYSTEM, NONE
    }

    private final MediaSource system;
    private final Spotify spotify;
    private final SkysongConfig config;
    private final DemoSource demo = new DemoSource();

    /**
     * @param system the music player of this OS, or null if we can't read it here
     */
    public SwitchableSource(MediaSource system, Spotify spotify, SkysongConfig config) {
        this.system = system;
        this.spotify = spotify;
        this.config = config;
    }

    /** Which source the next poll will ask. */
    public Active active() {
        if (config.demoSong) {
            return Active.DEMO;
        }
        boolean spotifyWanted = config.musicSource != MusicSource.SYSTEM;
        if (spotifyWanted && spotify.account().loggedIn()) {
            return Active.SPOTIFY;
        }
        if (config.musicSource == MusicSource.SPOTIFY || system == null) {
            // Spotify was chosen but nobody is logged in yet.
            return Active.NONE;
        }
        return Active.SYSTEM;
    }

    public DemoSource demo() {
        return demo;
    }

    @Override
    public String name() {
        return switch (active()) {
            case DEMO -> demo.name();
            case SPOTIFY -> spotify.source().name();
            case SYSTEM -> system.name();
            case NONE -> "nothing";
        };
    }

    @Override
    public Optional<NowPlaying> poll() throws IOException {
        Active active = active();
        if (active == Active.DEMO) {
            demo.restartIfStopped();
            return demo.poll();
        }
        demo.stop();
        return switch (active) {
            case SPOTIFY -> pollSpotify();
            case SYSTEM -> system.poll();
            default -> Optional.empty();
        };
    }

    private Optional<NowPlaying> pollSpotify() throws IOException {
        try {
            return spotify.source().poll();
        } catch (SpotifyException e) {
            boolean loginGone = e.reason() == SpotifyException.Reason.NOT_LOGGED_IN
                    || e.reason() == SpotifyException.Reason.LOGIN_REJECTED;
            if (loginGone && config.musicSource == MusicSource.AUTO && system != null) {
                return system.poll();
            }
            throw e;
        }
    }

    @Override
    public void close() {
        if (system != null) {
            system.close();
        }
    }
}
