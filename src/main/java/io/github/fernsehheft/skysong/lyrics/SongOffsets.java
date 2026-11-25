package io.github.fernsehheft.skysong.lyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import io.github.fernsehheft.skysong.Skysong;
import io.github.fernsehheft.skysong.media.NowPlaying;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Timing corrections for single songs, for when the lyrics found don't quite fit the
 * version that's playing (a music video with a longer intro, for example). Positive
 * values show the lyrics earlier. Saved as {@code song-offsets.json}.
 */
public final class SongOffsets {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<TreeMap<String, Long>>() { }.getType();
    public static final long LIMIT_MS = 30_000;

    private final Path file;
    private final Map<String, Long> offsets;

    private SongOffsets(Path file, Map<String, Long> offsets) {
        this.file = file;
        this.offsets = offsets;
    }

    public static SongOffsets load(Path file) {
        Map<String, Long> offsets = null;
        if (Files.isRegularFile(file)) {
            try {
                offsets = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP_TYPE);
            } catch (IOException | JsonParseException e) {
                Skysong.LOGGER.warn("Couldn't read {}, starting without song timing corrections", file, e);
            }
        }
        return new SongOffsets(file, offsets == null ? new TreeMap<>() : new TreeMap<>(offsets));
    }

    public synchronized long get(NowPlaying track) {
        return track == null ? 0 : offsets.getOrDefault(key(track), 0L);
    }

    /** Changes the correction for {@code track} by {@code changeMs} and returns the new value. */
    public synchronized long adjust(NowPlaying track, long changeMs) {
        long value = Math.max(-LIMIT_MS, Math.min(LIMIT_MS, get(track) + changeMs));
        if (value == 0) {
            offsets.remove(key(track));
        } else {
            offsets.put(key(track), value);
        }
        save();
        return value;
    }

    public synchronized void reset(NowPlaying track) {
        if (offsets.remove(key(track)) != null) {
            save();
        }
    }

    private static String key(NowPlaying track) {
        return track.artist().toLowerCase(Locale.ROOT) + " - " + track.title().toLowerCase(Locale.ROOT);
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(offsets, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Skysong.LOGGER.warn("Couldn't save the song timing corrections to {}", file, e);
        }
    }
}
