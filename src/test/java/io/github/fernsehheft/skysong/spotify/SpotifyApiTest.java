package io.github.fernsehheft.skysong.spotify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotifyApiTest {
    private static final String CLIENT_ID = "0123456789abcdef0123456789abcdef";
    private static final String TOKENS = "{\"access_token\":\"access-1\",\"expires_in\":3600,\"refresh_token\":\"refresh-2\"}";

    @TempDir
    Path directory;
    private FakeSpotify fake;
    private SpotifyAccount account;
    private SpotifyApi api;

    @BeforeEach
    void setUp() throws IOException {
        fake = new FakeSpotify();
        account = SpotifyAccount.load(directory.resolve("spotify.json"));
        account.setClientId(CLIENT_ID);
        api = new SpotifyApi(account, fake.accounts(), fake.api());
    }

    @AfterEach
    void tearDown() {
        fake.close();
    }

    @Test
    void loginTradesTheCodeForTokensAndRemembersThem() throws IOException {
        fake.tokenAnswers.add(FakeSpotify.Answer.json(200, TOKENS));
        api.finishLogin("the-code", Pkce.forVerifier("verifier"));

        String body = fake.requests.get(0).body();
        assertTrue(body.contains("grant_type=authorization_code"), body);
        assertTrue(body.contains("code=the-code"), body);
        assertTrue(body.contains("code_verifier=verifier"), body);
        assertTrue(account.loggedIn());
        assertEquals("refresh-2", SpotifyAccount.load(directory.resolve("spotify.json")).refreshToken());
    }

    @Test
    void renewsTheAccessTokenWithTheRefreshToken() throws IOException {
        account.setRefreshToken("refresh-1");
        fake.tokenAnswers.add(FakeSpotify.Answer.json(200, TOKENS));
        fake.apiAnswers.add(FakeSpotify.Answer.json(200, "{\"is_playing\":true}"));

        assertTrue(api.get("/me/player").orElseThrow().get("is_playing").getAsBoolean());
        assertTrue(fake.requests.get(0).body().contains("refresh_token=refresh-1"));
        assertEquals("Bearer access-1", fake.requests.get(1).authorization());
        assertEquals("refresh-2", account.refreshToken());
    }

    @Test
    void nothingPlayingIsEmpty() throws IOException {
        account.setRefreshToken("refresh-1");
        fake.tokenAnswers.add(FakeSpotify.Answer.json(200, TOKENS));
        fake.apiAnswers.add(FakeSpotify.Answer.json(204, ""));
        assertTrue(api.get("/me/player").isEmpty());
    }

    @Test
    void triesOnceMoreWithAFreshTokenAfter401() throws IOException {
        account.setRefreshToken("refresh-1");
        fake.tokenAnswers.add(FakeSpotify.Answer.json(200, TOKENS));
        fake.tokenAnswers.add(FakeSpotify.Answer.json(200, TOKENS.replace("access-1", "access-2")));
        fake.apiAnswers.add(FakeSpotify.Answer.json(401, "{\"error\":{\"status\":401,\"message\":\"expired\"}}"));
        fake.apiAnswers.add(FakeSpotify.Answer.json(204, ""));

        assertTrue(api.get("/me/player").isEmpty());
        assertEquals("Bearer access-2", fake.requests.get(3).authorization());
    }

    @Test
    void rejectedRefreshTokenLogsOut() {
        account.setRefreshToken("revoked");
        fake.tokenAnswers.add(FakeSpotify.Answer.json(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Refresh token revoked\"}"));

        SpotifyException e = assertThrows(SpotifyException.class, () -> api.get("/me/player"));
        assertEquals(SpotifyException.Reason.LOGIN_REJECTED, e.reason());
        assertTrue(e.getMessage().contains("Refresh token revoked"));
        assertFalse(account.loggedIn());
    }

    @Test
    void waitsAfterBeingRateLimited() {
        account.setRefreshToken("refresh-1");
        fake.tokenAnswers.add(FakeSpotify.Answer.json(200, TOKENS));
        fake.apiAnswers.add(new FakeSpotify.Answer(429, "", "30"));

        SpotifyException first = assertThrows(SpotifyException.class, () -> api.get("/me/player"));
        assertEquals(SpotifyException.Reason.RATE_LIMITED, first.reason());
        int requestsSoFar = fake.requests.size();
        // While blocked, we don't even ask.
        assertThrows(SpotifyException.class, () -> api.get("/me/player"));
        assertEquals(requestsSoFar, fake.requests.size());
    }

    @Test
    void explainsForbiddenAnswers() {
        account.setRefreshToken("refresh-1");
        fake.tokenAnswers.add(FakeSpotify.Answer.json(200, TOKENS));
        fake.apiAnswers.add(FakeSpotify.Answer.json(403, "{\"error\":{\"status\":403,\"message\":\"Player command failed: Premium required\"}}"));
        fake.apiAnswers.add(FakeSpotify.Answer.json(403, "{\"error\":{\"status\":403,\"message\":\"User not registered in the Developer Dashboard\"}}"));

        assertEquals(SpotifyException.Reason.PREMIUM_REQUIRED,
                assertThrows(SpotifyException.class, () -> api.command("PUT", "/me/player/pause")).reason());
        assertEquals(SpotifyException.Reason.NOT_REGISTERED,
                assertThrows(SpotifyException.class, () -> api.get("/me/player")).reason());
    }

    @Test
    void notLoggedInDoesntTouchTheNetwork() {
        SpotifyException e = assertThrows(SpotifyException.class, () -> api.get("/me/player"));
        assertEquals(SpotifyException.Reason.NOT_LOGGED_IN, e.reason());
        assertTrue(fake.requests.isEmpty());
    }

    @Test
    void authorizeUrlHasEverythingSpotifyNeeds() {
        String url = api.authorizeUrl(Pkce.forVerifier("verifier"), "the-state");
        assertTrue(url.startsWith(fake.accounts() + "/authorize?"));
        assertTrue(url.contains("client_id=" + CLIENT_ID));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("state=the-state"));
        assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A" + SpotifyLogin.PORT + "%2Fcallback"));
    }
}
