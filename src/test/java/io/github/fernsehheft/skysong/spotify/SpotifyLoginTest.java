package io.github.fernsehheft.skysong.spotify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotifyLoginTest {

    @Test
    void finishesTheLoginWhenSpotifySendsTheBrowserBack(@TempDir Path directory) throws Exception {
        try (FakeSpotify fake = new FakeSpotify()) {
            SpotifyAccount account = SpotifyAccount.load(directory.resolve("spotify.json"));
            account.setClientId("0123456789abcdef0123456789abcdef");
            SpotifyApi api = new SpotifyApi(account, fake.accounts(), fake.api());
            SpotifyLogin login = new SpotifyLogin(api);
            fake.tokenAnswers.add(FakeSpotify.Answer.json(200,
                    "{\"access_token\":\"a\",\"expires_in\":3600,\"refresh_token\":\"r\"}"));

            SpotifyLogin.Started started = login.start();
            String state = SpotifyLogin.parseQuery(URI.create(started.url()).getRawQuery()).get("state");
            HttpClient browser = HttpClient.newHttpClient();

            // An answer that doesn't belong to our login is turned away and changes nothing.
            HttpResponse<String> stranger = browser.send(HttpRequest.newBuilder(
                    URI.create(SpotifyApi.REDIRECT_URI + "?code=x&state=wrong")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, stranger.statusCode());
            assertFalse(started.done().isDone());

            HttpResponse<String> page = browser.send(HttpRequest.newBuilder(
                    URI.create(SpotifyApi.REDIRECT_URI + "?code=the-code&state=" + state)).build(), HttpResponse.BodyHandlers.ofString());
            started.done().get(5, TimeUnit.SECONDS);
            assertTrue(page.body().contains("connected to Spotify"));
            assertTrue(account.loggedIn());
            assertTrue(fake.requests.get(0).body().contains("code=the-code"));
        }
    }
}
