package io.github.fernsehheft.skysong.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrclibClientTest {

    private static final TrackQuery SONG = new TrackQuery("Shape of You", "Ed Sheeran", "", 0);

    private static JsonArray results(String json) {
        // Every test result is our song unless it says otherwise.
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            JsonObject result = element.getAsJsonObject();
            if (!result.has("trackName")) {
                result.addProperty("trackName", "Shape of You");
                result.addProperty("artistName", "Ed Sheeran");
            }
        }
        return array;
    }

    @Test
    void prefersSyncedLyricsOfTheRightLength() {
        JsonArray results = results("""
                [
                  {"id": 1, "duration": 200, "plainLyrics": "plain", "syncedLyrics": null},
                  {"id": 2, "duration": 260, "plainLyrics": "x", "syncedLyrics": "[00:01.00]too long"},
                  {"id": 3, "duration": 201.5, "plainLyrics": "x", "syncedLyrics": "[00:01.00]right"},
                  {"id": 4, "duration": 200, "plainLyrics": null, "syncedLyrics": null}
                ]
                """);
        JsonObject best = LrclibClient.pickBest(results, SONG, 200_000, true).orElseThrow();
        assertEquals(3, best.get("id").getAsInt());
    }

    @Test
    void takesPlainLyricsIfThatsAllThereIs() {
        JsonArray results = results("[{\"id\": 1, \"duration\": 200, \"plainLyrics\": \"plain\"}]");
        assertEquals(1, LrclibClient.pickBest(results, SONG, 199_000, true).orElseThrow().get("id").getAsInt());
    }

    @Test
    void ignoresLengthWhenTheSongLengthIsUnknown() {
        JsonArray results = results("[{\"id\": 7, \"duration\": 999, \"syncedLyrics\": \"[00:01.00]a\"}]");
        assertEquals(7, LrclibClient.pickBest(results, SONG, 0, true).orElseThrow().get("id").getAsInt());
        assertTrue(LrclibClient.pickBest(results, SONG, 100_000, true).isEmpty());
    }

    @Test
    void ignoresOtherSongsInTheSearchResults() {
        JsonArray results = results("""
                [
                  {"id": 1, "trackName": "Galway Girl", "artistName": "Ed Sheeran", "syncedLyrics": "[00:01.00]a"},
                  {"id": 2, "trackName": "Shape of You", "artistName": "Some Cover Band", "syncedLyrics": "[00:01.00]b"},
                  {"id": 3, "trackName": "Shape of You [Shape of You, 2017]", "artistName": "Ed Sheeran", "syncedLyrics": "[00:01.00]c"}
                ]
                """);
        assertEquals(3, LrclibClient.pickBest(results, SONG, 0, true).orElseThrow().get("id").getAsInt());
    }

    @Test
    void nothingFitsMeansNothing() {
        // Like a DJ announcement searched as "Spotify DJ": whatever comes back isn't it.
        TrackQuery announcement = new TrackQuery("DJ", "Spotify", "", 0);
        JsonArray results = results("""
                [{"id": 1, "trackName": "Superstar DJ", "artistName": "Someone", "syncedLyrics": "[00:01.00]a"}]
                """);
        assertTrue(LrclibClient.pickBest(results, announcement, 0, true).isEmpty());
    }

    @Test
    void remixesOnlyNeedTheTitle() {
        TrackQuery remix = new TrackQuery("Shape of You Hardtekk", "GoldZoro", "", 0);
        JsonArray results = results("[{\"id\": 4, \"trackName\": \"Shape of You\", \"artistName\": \"Ed Sheeran\", \"syncedLyrics\": \"[00:01.00]a\"}]");
        assertTrue(LrclibClient.pickBest(results, remix, 0, true).isEmpty());
        assertEquals(4, LrclibClient.pickBest(results, remix, 0, false).orElseThrow().get("id").getAsInt());
    }
}
