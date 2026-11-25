package io.github.fernsehheft.skysong.lyrics;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Remembers lyrics we already downloaded, one small JSON file per song, so replaying
 * a song doesn't need the internet. Songs without lyrics are remembered for a day
 * only, in case someone adds them to LRCLIB in the meantime.
 */
public final class LyricsCache {
    private static final long FORGET_MISSING_AFTER_MS = TimeUnit.DAYS.toMillis(1);
    private static final Gson GSON = new Gson();

    private final Path directory;

    public LyricsCache(Path directory) {
        this.directory = directory;
    }

    /** What the cache knows about this song. */
    public sealed interface Entry {
        record Found(LyricsText text) implements Entry {
        }

        record Missing() implements Entry {
        }
    }

    /** The stored JSON. Field names are part of the file format, so don't rename them. */
    private record Stored(String synced, String plain, boolean instrumental, boolean missing, long savedAt) {
    }

    public Optional<Entry> get(TrackQuery query) throws IOException {
        Path file = fileFor(query);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Stored stored;
        try {
            stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
        } catch (JsonParseException e) {
            Files.deleteIfExists(file);
            return Optional.empty();
        }
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.missing()) {
            boolean expired = System.currentTimeMillis() - stored.savedAt() > FORGET_MISSING_AFTER_MS;
            return expired ? Optional.empty() : Optional.of(new Entry.Missing());
        }
        return Optional.of(new Entry.Found(new LyricsText(stored.synced(), stored.plain(), stored.instrumental())));
    }

    public void putFound(TrackQuery query, LyricsText text) throws IOException {
        write(query, new Stored(text.synced(), text.plain(), text.instrumental(), false, System.currentTimeMillis()));
    }

    public void putMissing(TrackQuery query) throws IOException {
        write(query, new Stored(null, null, false, true, System.currentTimeMillis()));
    }

    private void write(TrackQuery query, Stored stored) throws IOException {
        Files.createDirectories(directory);
        Path file = fileFor(query);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(stored), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path fileFor(TrackQuery query) {
        String key = query.artist().toLowerCase(Locale.ROOT) + "\n" + query.title().toLowerCase(Locale.ROOT);
        return directory.resolve(sha1(key) + ".json");
    }

    private static String sha1(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-1", e);
        }
    }
}
