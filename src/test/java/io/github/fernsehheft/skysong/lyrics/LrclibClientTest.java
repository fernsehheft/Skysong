package io.github.fernsehheft.skysong.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrclibClientTest {

    private static JsonArray results(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
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
        JsonObject best = LrclibClient.pickBest(results, 200_000).orElseThrow();
        assertEquals(3, best.get("id").getAsInt());
    }

    @Test
    void takesPlainLyricsIfThatsAllThereIs() {
        JsonArray results = results("[{\"id\": 1, \"duration\": 200, \"plainLyrics\": \"plain\"}]");
        assertEquals(1, LrclibClient.pickBest(results, 199_000).orElseThrow().get("id").getAsInt());
    }

    @Test
    void ignoresLengthWhenTheSongLengthIsUnknown() {
        JsonArray results = results("[{\"id\": 7, \"duration\": 999, \"syncedLyrics\": \"[00:01.00]a\"}]");
        assertEquals(7, LrclibClient.pickBest(results, 0).orElseThrow().get("id").getAsInt());
        assertTrue(LrclibClient.pickBest(results, 100_000).isEmpty());
    }
}
