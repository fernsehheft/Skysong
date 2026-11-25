package io.github.fernsehheft.skysong.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.fernsehheft.skysong.media.NowPlaying;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotifyPartsTest {

    @Test
    void pkceChallengeIsTheHashOfTheVerifier() {
        // Worked out independently with Python's hashlib and base64.
        assertEquals("eB1Mc5mYiZVc081BDcfucS-b6ZwZK6yzPTfxKmwiAgQ",
                Pkce.forVerifier("skysong-test-verifier-0123456789-abcdefghijklmnop").challenge());
        Pkce random = Pkce.create();
        assertTrue(random.verifier().length() >= 43, "RFC 7636 wants at least 43 characters");
        assertNotEquals(random.verifier(), Pkce.create().verifier());
    }

    @Test
    void readsTheCurrentTrack() {
        JsonObject player = JsonParser.parseString("""
                {
                  "is_playing": true,
                  "progress_ms": 42000,
                  "device": {"name": "Phone", "volume_percent": 70, "supports_volume": true},
                  "item": {
                    "name": "Snowman",
                    "duration_ms": 165000,
                    "album": {"name": "Everyday Is Christmas"},
                    "artists": [{"name": "Sia"}, {"name": "Someone Else"}]
                  }
                }
                """).getAsJsonObject();
        NowPlaying track = SpotifySource.parse(player, 5).orElseThrow();
        assertEquals("Snowman", track.title());
        assertEquals("Sia, Someone Else", track.artist());
        assertEquals("Everyday Is Christmas", track.album());
        assertEquals(165_000, track.durationMs());
        assertEquals(42_000, track.positionMs());
        assertTrue(track.playing());

        SpotifySource.PlayerState state = SpotifySource.parseState(player);
        assertEquals(70, state.volumePercent());
        assertTrue(state.volumeAllowed());
    }

    @Test
    void adsHaveNoTrack() {
        JsonObject player = JsonParser.parseString("{\"is_playing\": true, \"currently_playing_type\": \"ad\", \"item\": null}")
                .getAsJsonObject();
        assertTrue(SpotifySource.parse(player, 0).isEmpty());
        assertEquals(-1, SpotifySource.parseState(player).volumePercent());
    }

    @Test
    void podcastEpisodesUseTheShowAsArtist() {
        JsonObject player = JsonParser.parseString("""
                {"is_playing": false, "progress_ms": 1000,
                 "item": {"name": "Episode 1", "duration_ms": 60000, "show": {"name": "A Podcast"}}}
                """).getAsJsonObject();
        NowPlaying episode = SpotifySource.parse(player, 0).orElseThrow();
        assertEquals("A Podcast", episode.artist());
        assertFalse(episode.playing());
    }

    @Test
    void parsesTheCallbackQuery() {
        assertEquals(Map.of("code", "a b", "state", "x=y"), SpotifyLogin.parseQuery("code=a+b&state=x%3Dy"));
        assertTrue(SpotifyLogin.parseQuery(null).isEmpty());
    }

    @Test
    void recognisesClientIds() {
        assertTrue(SpotifyAccount.looksLikeClientId(" 0123456789abcdef0123456789ABCDEF "));
        assertFalse(SpotifyAccount.looksLikeClientId("0123456789abcdef"));
        assertFalse(SpotifyAccount.looksLikeClientId(null));
    }

    @Test
    void changingTheAppLogsOut(@TempDir Path directory) {
        SpotifyAccount account = SpotifyAccount.load(directory.resolve("spotify.json"));
        account.setClientId("0123456789abcdef0123456789abcdef");
        account.setRefreshToken("token");
        assertTrue(account.loggedIn());
        account.setClientId("fedcba9876543210fedcba9876543210");
        assertFalse(account.loggedIn());
    }

    @Test
    void errorMessages() {
        assertEquals("Nope", SpotifyApi.errorMessage("{\"error\":{\"status\":403,\"message\":\"Nope\"}}").orElseThrow());
        assertEquals("Bad code", SpotifyApi.errorMessage("{\"error\":\"invalid_grant\",\"error_description\":\"Bad code\"}").orElseThrow());
        assertTrue(SpotifyApi.errorMessage("<html>").isEmpty());
    }
}
