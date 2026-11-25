package io.github.fernsehheft.skysong.spotify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.fernsehheft.skysong.Skysong;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Talks to Spotify's Web API for the logged in player: trading the login code for
 * tokens, renewing the access token when it runs out, and backing off when Spotify
 * says we're asking too often.
 */
public final class SpotifyApi {
    public static final String REDIRECT_URI = "http://127.0.0.1:" + SpotifyLogin.PORT + SpotifyLogin.CALLBACK_PATH;
    static final String SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state";

    private static final String ACCOUNTS = "https://accounts.spotify.com";
    private static final String API = "https://api.spotify.com/v1";
    /** Renew the access token a little before it actually runs out. */
    private static final long EXPIRY_MARGIN_MS = 60_000;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final SpotifyAccount account;
    private final String accountsBase;
    private final String apiBase;

    private String accessToken;
    private long accessTokenExpiresAt;
    private long blockedUntil;

    public SpotifyApi(SpotifyAccount account) {
        this(account, ACCOUNTS, API);
    }

    /** With other addresses than Spotify's, for tests. */
    SpotifyApi(SpotifyAccount account, String accountsBase, String apiBase) {
        this.account = account;
        this.accountsBase = accountsBase;
        this.apiBase = apiBase;
    }

    /** The Spotify page where the player allows Skysong to see what they're listening to. */
    String authorizeUrl(Pkce pkce, String state) {
        return accountsBase + "/authorize?" + form(Map.of(
                "response_type", "code",
                "client_id", account.clientId(),
                "scope", SCOPES,
                "redirect_uri", REDIRECT_URI,
                "code_challenge_method", "S256",
                "code_challenge", pkce.challenge(),
                "state", state));
    }

    /** Finishes the login with the code Spotify sent to our callback. */
    synchronized void finishLogin(String code, Pkce pkce) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", REDIRECT_URI);
        body.put("client_id", account.clientId());
        body.put("code_verifier", pkce.verifier());
        takeTokens(requestTokens(body));
    }

    public synchronized void forgetTokens() {
        accessToken = null;
        accessTokenExpiresAt = 0;
    }

    /**
     * GETs {@code path} below the API's address.
     *
     * @return the answer, or empty when Spotify had nothing to say (HTTP 204)
     */
    public Optional<JsonObject> get(String path) throws IOException {
        HttpResponse<String> response = send("GET", path);
        if (response.statusCode() == 204 || response.body().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(JsonParser.parseString(response.body()).getAsJsonObject());
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Spotify sent something we couldn't read", e);
        }
    }

    /** Sends a command like PUT /me/player/pause that answers without content. */
    public void command(String method, String path) throws IOException {
        send(method, path);
    }

    private HttpResponse<String> send(String method, String path) throws IOException {
        checkNotBlocked();
        HttpResponse<String> response = sendOnce(method, path, accessToken());
        if (response.statusCode() == 401) {
            // The access token was rejected early; get a fresh one and try once more.
            forgetTokens();
            response = sendOnce(method, path, accessToken());
        }
        int status = response.statusCode();
        if (status == 429) {
            long seconds = response.headers().firstValueAsLong("Retry-After").orElse(5);
            synchronized (this) {
                blockedUntil = System.currentTimeMillis() + seconds * 1000;
            }
            Skysong.LOGGER.warn("Spotify asked us to wait {} s before asking again", seconds);
            throw new SpotifyException(SpotifyException.Reason.RATE_LIMITED, "Spotify asked us to slow down");
        }
        if (status == 403) {
            throw forbidden(response.body());
        }
        if (status == 404 && path.startsWith("/me/player")) {
            throw new SpotifyException(SpotifyException.Reason.NO_DEVICE, "No Spotify app is open");
        }
        if (status >= 300) {
            throw new IOException("Spotify answered " + status + errorMessage(response.body()).map(m -> ": " + m).orElse(""));
        }
        return response;
    }

    private HttpResponse<String> sendOnce(String method, String path, String token) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .method(method, method.equals("GET")
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(""))
                .build();
        return exchange(request);
    }

    private synchronized void checkNotBlocked() throws SpotifyException {
        if (System.currentTimeMillis() < blockedUntil) {
            throw new SpotifyException(SpotifyException.Reason.RATE_LIMITED, "Spotify asked us to slow down");
        }
    }

    /** A valid access token, renewed with the refresh token if needed. */
    private synchronized String accessToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpiresAt - EXPIRY_MARGIN_MS) {
            return accessToken;
        }
        if (!account.loggedIn()) {
            throw new SpotifyException(SpotifyException.Reason.NOT_LOGGED_IN, "Not logged in to Spotify");
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", account.refreshToken());
        body.put("client_id", account.clientId());
        try {
            takeTokens(requestTokens(body));
        } catch (SpotifyException e) {
            if (e.reason() == SpotifyException.Reason.LOGIN_REJECTED) {
                // The refresh token is no good any more (revoked, or the app changed).
                account.logOut();
            }
            throw e;
        }
        return accessToken;
    }

    private JsonObject requestTokens(Map<String, String> body) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(accountsBase + "/api/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(body)))
                .build();
        HttpResponse<String> response = exchange(request);
        if (response.statusCode() == 400 || response.statusCode() == 401) {
            throw new SpotifyException(SpotifyException.Reason.LOGIN_REJECTED,
                    "Spotify didn't accept the login" + errorMessage(response.body()).map(m -> " (" + m + ")").orElse(""));
        }
        if (response.statusCode() != 200) {
            throw new IOException("Spotify's login server answered " + response.statusCode());
        }
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Spotify's login server sent something we couldn't read", e);
        }
    }

    private void takeTokens(JsonObject tokens) throws IOException {
        if (!tokens.has("access_token")) {
            throw new IOException("Spotify didn't send an access token");
        }
        accessToken = tokens.get("access_token").getAsString();
        long expiresInSeconds = tokens.has("expires_in") ? tokens.get("expires_in").getAsLong() : 3600;
        accessTokenExpiresAt = System.currentTimeMillis() + expiresInSeconds * 1000;
        // Spotify may hand out a new refresh token; the old one then stops working.
        if (tokens.has("refresh_token")) {
            account.setRefreshToken(tokens.get("refresh_token").getAsString());
        }
    }

    private HttpResponse<String> exchange(HttpRequest request) throws IOException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while talking to Spotify", e);
        }
    }

    private static SpotifyException forbidden(String body) {
        String message = errorMessage(body).orElse("");
        // Apps in Spotify's development mode only work for accounts added to them.
        if (message.toLowerCase(Locale.ROOT).contains("not registered") || message.toLowerCase(Locale.ROOT).contains("user may not")) {
            return new SpotifyException(SpotifyException.Reason.NOT_REGISTERED, message);
        }
        return new SpotifyException(SpotifyException.Reason.PREMIUM_REQUIRED,
                message.isEmpty() ? "Spotify doesn't allow this for your account" : message);
    }

    /** The "message" from an error answer like {"error": {"status": 403, "message": "..."}}. */
    static Optional<String> errorMessage(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonElement error = json.get("error");
            if (error != null && error.isJsonObject() && error.getAsJsonObject().has("message")) {
                return Optional.of(error.getAsJsonObject().get("message").getAsString());
            }
            if (json.has("error_description")) {
                return Optional.of(json.get("error_description").getAsString());
            }
            if (error != null && error.isJsonPrimitive()) {
                return Optional.of(error.getAsString());
            }
        } catch (RuntimeException e) {
            // Not JSON; nothing to add then.
        }
        return Optional.empty();
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
