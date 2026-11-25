package io.github.fernsehheft.skysong.spotify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What we keep between game sessions to stay logged in: the player's own Spotify app
 * (its client ID) and a refresh token. Never a password. Saved as {@code spotify.json}.
 */
public final class SpotifyAccount {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private transient Path file;
    private String clientId = "";
    private String refreshToken = "";

    public static SpotifyAccount load(Path file) {
        SpotifyAccount account = null;
        if (Files.isRegularFile(file)) {
            try {
                account = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SpotifyAccount.class);
            } catch (IOException | JsonParseException e) {
                Skysong.LOGGER.warn("Couldn't read {}, you'll have to log in to Spotify again", file, e);
            }
        }
        if (account == null) {
            account = new SpotifyAccount();
        }
        account.file = file;
        if (account.clientId == null) {
            account.clientId = "";
        }
        if (account.refreshToken == null) {
            account.refreshToken = "";
        }
        return account;
    }

    public synchronized String clientId() {
        return clientId;
    }

    /** A new app means the old login is no longer valid. */
    public synchronized void setClientId(String clientId) {
        String cleaned = clientId == null ? "" : clientId.strip();
        if (!cleaned.equals(this.clientId)) {
            this.clientId = cleaned;
            this.refreshToken = "";
            save();
        }
    }

    synchronized String refreshToken() {
        return refreshToken;
    }

    synchronized void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        save();
    }

    public synchronized boolean loggedIn() {
        return !clientId.isEmpty() && !refreshToken.isEmpty();
    }

    public synchronized void logOut() {
        setRefreshToken("");
    }

    /** Client IDs from the Spotify dashboard are 32 hexadecimal characters. */
    public static boolean looksLikeClientId(String text) {
        return text != null && text.strip().matches("[0-9a-fA-F]{32}");
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Skysong.LOGGER.warn("Couldn't save the Spotify login to {}", file, e);
        }
    }
}
