package io.github.fernsehheft.skysong.spotify;

import java.io.IOException;

/** A problem with Spotify that the player can do something about, so it gets its own explanation. */
public final class SpotifyException extends IOException {
    public enum Reason {
        NOT_LOGGED_IN,
        /** The login was refused, e.g. because it was revoked in the Spotify account settings. */
        LOGIN_REJECTED,
        /** The Spotify app is in development mode and this account isn't added to it. */
        NOT_REGISTERED,
        /** Controlling playback through the Web API needs Spotify Premium. */
        PREMIUM_REQUIRED,
        /** Nothing is open to play on. */
        NO_DEVICE,
        RATE_LIMITED
    }

    private final Reason reason;

    public SpotifyException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
