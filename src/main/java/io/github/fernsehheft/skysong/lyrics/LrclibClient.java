package io.github.fernsehheft.skysong.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Looks up lyrics on <a href="https://lrclib.net">LRCLIB</a>, a free and open lyrics
 * database. We first ask for an exact match and fall back to a search.
 */
public final class LrclibClient {
    private static final String API = "https://lrclib.net/api";
    /** LRCLIB's durations can be off by a bit, so a match within this is good enough. */
    private static final double MAX_DURATION_DIFFERENCE_S = 4.0;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String userAgent;

    /** LRCLIB asks clients to say who they are, see their API docs. */
    public LrclibClient(String userAgent) {
        this.userAgent = userAgent;
    }

    public Optional<LyricsText> find(TrackQuery query) throws IOException {
        Map<String, String> exact = new LinkedHashMap<>();
        exact.put("track_name", query.title());
        exact.put("artist_name", query.artist());
        if (!query.album().isBlank()) {
            exact.put("album_name", query.album());
        }
        if (query.durationMs() > 0) {
            exact.put("duration", Long.toString(Math.round(query.durationMs() / 1000.0)));
        }
        Optional<JsonElement> match = get("/get", exact);
        if (match.isPresent() && match.get().isJsonObject()) {
            return Optional.of(toText(match.get().getAsJsonObject()));
        }

        Optional<LyricsText> found = search(Map.of(
                "track_name", query.cleanTitle(),
                "artist_name", query.mainArtist()), query.durationMs());
        if (found.isPresent()) {
            return found;
        }
        // Last try: free text search, which also helps when the title contains the artist.
        return search(Map.of("q", query.mainArtist() + " " + query.cleanTitle()), query.durationMs());
    }

    private Optional<LyricsText> search(Map<String, String> parameters, long durationMs) throws IOException {
        Optional<JsonElement> results = get("/search", parameters);
        if (results.isEmpty() || !results.get().isJsonArray()) {
            return Optional.empty();
        }
        return pickBest(results.get().getAsJsonArray(), durationMs).map(LrclibClient::toText);
    }

    /** Prefers results with timestamps, then the one whose length is closest to the song's. */
    static Optional<JsonObject> pickBest(JsonArray results, long durationMs) {
        JsonObject best = null;
        double bestScore = Double.MAX_VALUE;
        for (JsonElement element : results) {
            JsonObject result = element.getAsJsonObject();
            LyricsText text = toText(result);
            if (!text.hasSynced() && !text.hasPlain() && !text.instrumental()) {
                continue;
            }
            double difference = 0;
            if (durationMs > 0 && result.has("duration") && !result.get("duration").isJsonNull()) {
                difference = Math.abs(result.get("duration").getAsDouble() - durationMs / 1000.0);
                if (difference > MAX_DURATION_DIFFERENCE_S) {
                    continue;
                }
            }
            double score = difference + (text.hasSynced() ? 0 : 100);
            if (score < bestScore) {
                bestScore = score;
                best = result;
            }
        }
        return Optional.ofNullable(best);
    }

    static LyricsText toText(JsonObject result) {
        return new LyricsText(
                string(result, "syncedLyrics"),
                string(result, "plainLyrics"),
                result.has("instrumental") && result.get("instrumental").getAsBoolean());
    }

    private Optional<JsonElement> get(String path, Map<String, String> parameters) throws IOException {
        String queryString = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + path + "?" + queryString))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while asking LRCLIB", e);
        }
        // 404 is LRCLIB's way of saying "no such song", 400 means the request was incomplete.
        if (response.statusCode() == 404 || response.statusCode() == 400) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException("LRCLIB answered with HTTP " + response.statusCode());
        }
        return Optional.of(JsonParser.parseString(response.body()));
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
