package io.github.fernsehheft.skysong.spotify;

import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Everything about the Spotify connection in one place: the account, the login and the player. */
public final class Spotify {
    public enum LoginStatus {
        LOGGED_OUT,
        /** The login page is open in the browser and we wait for Spotify to send the player back. */
        WAITING,
        LOGGED_IN,
        FAILED
    }

    private final SpotifyAccount account;
    private final SpotifyApi api;
    private final SpotifyLogin login;
    private final SpotifySource source;

    private volatile LoginStatus status;
    private volatile String problem = "";

    public Spotify(Path accountFile) {
        this.account = SpotifyAccount.load(accountFile);
        this.api = new SpotifyApi(account);
        this.login = new SpotifyLogin(api);
        this.source = new SpotifySource(api);
        this.status = account.loggedIn() ? LoginStatus.LOGGED_IN : LoginStatus.LOGGED_OUT;
    }

    public SpotifyAccount account() {
        return account;
    }

    public SpotifyApi api() {
        return api;
    }

    public SpotifySource source() {
        return source;
    }

    public LoginStatus status() {
        // The login can also end elsewhere, e.g. when Spotify stops accepting the refresh token.
        if (status == LoginStatus.LOGGED_IN && !account.loggedIn()) {
            status = LoginStatus.LOGGED_OUT;
        }
        return status;
    }

    /** Why the last login failed, for showing to the player. */
    public String problem() {
        return problem;
    }

    /**
     * Starts logging in.
     *
     * @param openInBrowser opens the given address in the player's browser
     * @param whenDone      called on a background thread once the login is over, either way
     */
    public void logIn(Consumer<String> openInBrowser, Runnable whenDone) {
        try {
            SpotifyLogin.Started started = login.start();
            status = LoginStatus.WAITING;
            problem = "";
            openInBrowser.accept(started.url());
            started.done().whenComplete((ignored, error) -> {
                if (error == null) {
                    status = LoginStatus.LOGGED_IN;
                    source.refreshSoon();
                } else if (!(error instanceof CancellationException)) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    Skysong.LOGGER.warn("Spotify login failed: {}", cause.getMessage());
                    problem = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    status = LoginStatus.FAILED;
                }
                whenDone.run();
            });
        } catch (IOException e) {
            // Most likely another program already uses our callback port.
            Skysong.LOGGER.warn("Couldn't start the Spotify login", e);
            problem = "Port " + SpotifyLogin.PORT + " is busy: " + e.getMessage();
            status = LoginStatus.FAILED;
            whenDone.run();
        }
    }

    public void cancelLogin() {
        login.cancel();
        if (status == LoginStatus.WAITING) {
            status = account.loggedIn() ? LoginStatus.LOGGED_IN : LoginStatus.LOGGED_OUT;
        }
    }

    public void logOut() {
        login.cancel();
        account.logOut();
        api.forgetTokens();
        status = LoginStatus.LOGGED_OUT;
        Skysong.LOGGER.info("Logged out of Spotify");
    }
}
