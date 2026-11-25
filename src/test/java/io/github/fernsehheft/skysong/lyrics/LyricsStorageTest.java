package io.github.fernsehheft.skysong.lyrics;

import io.github.fernsehheft.skysong.media.NowPlaying;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricsStorageTest {
    private static final TrackQuery SONG = new TrackQuery("Snowman (feat. Nobody)", "Sia", "", 165_000);

    @Test
    void cacheRemembersLyricsAndMissingSongs(@TempDir Path directory) throws IOException {
        LyricsCache cache = new LyricsCache(directory);
        assertTrue(cache.get(SONG).isEmpty());

        cache.putFound(SONG, LyricsText.synced("[00:01.00]Hi"));
        LyricsCache.Entry entry = cache.get(SONG).orElseThrow();
        assertEquals("[00:01.00]Hi", assertInstanceOf(LyricsCache.Entry.Found.class, entry).text().synced());

        TrackQuery other = new TrackQuery("Other", "Sia", "", 0);
        cache.putMissing(other);
        assertInstanceOf(LyricsCache.Entry.Missing.class, cache.get(other).orElseThrow());
    }

    @Test
    void brokenCacheFilesAreDropped(@TempDir Path directory) throws IOException {
        LyricsCache cache = new LyricsCache(directory);
        cache.putFound(SONG, LyricsText.plain("Hi"));
        try (var files = Files.list(directory)) {
            Files.writeString(files.findFirst().orElseThrow(), "{not json");
        }
        assertTrue(cache.get(SONG).isEmpty());
    }

    @Test
    void localFilesMatchByName(@TempDir Path directory) throws IOException {
        LocalLyricsFolder folder = new LocalLyricsFolder(directory);
        folder.prepare();
        assertTrue(folder.find(SONG).isEmpty());

        Files.writeString(directory.resolve("sia - snowman.lrc"), "[00:01.00]Mine");
        assertEquals("[00:01.00]Mine", folder.find(SONG).orElseThrow().synced());
    }

    @Test
    void localFilesMatchByTitleOrTags(@TempDir Path directory) throws IOException {
        LocalLyricsFolder folder = new LocalLyricsFolder(directory);
        Files.writeString(directory.resolve("Snowman.txt"), "Plain words");
        assertEquals("Plain words", folder.find(SONG).orElseThrow().plain());

        Files.delete(directory.resolve("Snowman.txt"));
        Files.writeString(directory.resolve("whatever.lrc"), "[ar:Sia]\n[ti:Snowman]\n[00:02.00]Tagged");
        assertTrue(folder.find(SONG).orElseThrow().synced().contains("Tagged"));
    }

    @Test
    void cacheKeepsTheTimeScale(@TempDir Path directory) throws IOException {
        LyricsCache cache = new LyricsCache(directory);
        cache.putFound(SONG, LyricsText.synced("[00:01.00]Hi").stretchedBy(0.75));
        LyricsCache.Entry entry = cache.get(SONG).orElseThrow();
        assertEquals(0.75, assertInstanceOf(LyricsCache.Entry.Found.class, entry).text().timeScale());
    }

    @Test
    void songOffsetsAreRememberedPerSong(@TempDir Path directory) {
        Path file = directory.resolve("song-offsets.json");
        SongOffsets offsets = SongOffsets.load(file);
        NowPlaying song = new NowPlaying("Shape of You", "Ed Sheeran", "", 0, 0, true, "Test", 0);
        NowPlaying other = new NowPlaying("Snowman", "Sia", "", 0, 0, true, "Test", 0);

        offsets.adjust(song, -250);
        offsets.adjust(song, -250);
        assertEquals(-500, offsets.get(song));
        assertEquals(0, offsets.get(other));
        assertEquals(-500, SongOffsets.load(file).get(song));

        offsets.reset(song);
        assertEquals(0, SongOffsets.load(file).get(song));
        assertEquals(SongOffsets.LIMIT_MS, offsets.adjust(other, 10 * SongOffsets.LIMIT_MS));
    }
}
