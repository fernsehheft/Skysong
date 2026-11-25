package io.github.fernsehheft.skysong.spotify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.fernsehheft.skysong.media.MediaSource;
import io.github.fernsehheft.skysong.media.NowPlaying;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Asks Spotify's Web API what the logged in player is listening to, on any of their
 * devices. Spotify limits how often we may ask, so between requests the last answer is
 * reused; {@link NowPlaying#positionAt} keeps the time running in between.
 */
public final class SpotifySource implements MediaSource {
    private static final long MIN_REQUEST_GAP_MS = 1000;

    private final SpotifyApi api;
    private long lastRequestAt;
    private Optional<NowPlaying> last = Optional.empty();
    private volatile PlayerState playerState = PlayerState.NONE;

    /** The parts of Spotify's player state that the controls need. */
    public record PlayerState(boolean playing, int volumePercent, boolean volumeAllowed) {
        static final PlayerState NONE = new PlayerState(false, -1, false);
    }

    public SpotifySource(SpotifyApi api) {
        this.api = api;
    }

    @Override
    public String name() {
        return "Spotify";
    }

    public PlayerState playerState() {
        return playerState;
    }

    /** Makes the next poll ask Spotify again, after the player changed something. */
    public synchronized void refreshSoon() {
        lastRequestAt = 0;
    }

    @Override
    public synchronized Optional<NowPlaying> poll() throws IOException {
        long now = System.currentTimeMillis();
        if (now - lastRequestAt < MIN_REQUEST_GAP_MS) {
            return last;
        }
        lastRequestAt = now;
        long before = System.nanoTime();
        Optional<JsonObject> answer = api.get("/me/player?additional_types=track,episode");
        // The position was read somewhere during the request; the middle is the best guess.
        long sampledAt = before + (System.nanoTime() - before) / 2;
        playerState = answer.map(SpotifySource::parseState).orElse(PlayerState.NONE);
        last = answer.flatMap(json -> parse(json, sampledAt));
        return last;
    }

    static Optional<NowPlaying> parse(JsonObject player, long sampledAtNanos) {
        JsonElement itemElement = player.get("item");
        if (itemElement == null || !itemElement.isJsonObject()) {
            // Ads and some podcasts come without an item.
            return Optional.empty();
        }
        JsonObject item = itemElement.getAsJsonObject();
        String title = string(item, "name");
        if (title.isBlank()) {
            return Optional.empty();
        }

        String artist;
        String album = "";
        if (item.has("artists") && item.get("artists").isJsonArray()) {
            List<String> names = new ArrayList<>();
            for (JsonElement each : item.getAsJsonArray("artists")) {
                names.add(string(each.getAsJsonObject(), "name"));
            }
            artist = String.join(", ", names);
        } else if (item.has("show") && item.get("show").isJsonObject()) {
            // A podcast episode: the show takes the artist's place.
            artist = string(item.getAsJsonObject("show"), "name");
        } else {
            artist = "";
        }
        if (item.has("album") && item.get("album").isJsonObject()) {
            album = string(item.getAsJsonObject("album"), "name");
        }

        long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
        long progress = player.has("progress_ms") && !player.get("progress_ms").isJsonNull()
                ? player.get("progress_ms").getAsLong() : 0;
        boolean playing = player.has("is_playing") && player.get("is_playing").getAsBoolean();
        return Optional.of(new NowPlaying(title, artist, album, duration, progress, playing, "Spotify", sampledAtNanos));
    }

    static PlayerState parseState(JsonObject player) {
        boolean playing = player.has("is_playing") && player.get("is_playing").getAsBoolean();
        int volume = -1;
        boolean volumeAllowed = false;
        if (player.has("device") && player.get("device").isJsonObject()) {
            JsonObject device = player.getAsJsonObject("device");
            if (device.has("volume_percent") && !device.get("volume_percent").isJsonNull()) {
                volume = device.get("volume_percent").getAsInt();
            }
            volumeAllowed = !device.has("supports_volume") || device.get("supports_volume").getAsBoolean();
        }
        return new PlayerState(playing, volume, volumeAllowed);
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
