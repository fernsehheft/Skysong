package io.github.fernsehheft.skysong.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaParsingTest {

    @Test
    void playerctlLine() {
        NowPlaying track = PlayerctlSource.parse(
                "Playing\tSnowman\tSia\tEveryday Is Christmas\t165000000\t42500000\tspotify\n", 7).orElseThrow();
        assertEquals("Snowman", track.title());
        assertEquals("Sia", track.artist());
        assertEquals(165_000, track.durationMs());
        assertEquals(42_500, track.positionMs());
        assertTrue(track.playing());
        assertEquals("spotify", track.player());
    }

    @Test
    void playerctlWithoutLengthOrStopped() {
        NowPlaying track = PlayerctlSource.parse("Paused\tRadio\t\t\t\t\tvlc", 0).orElseThrow();
        assertEquals(0, track.durationMs());
        assertFalse(track.playing());

        assertTrue(PlayerctlSource.parse("Stopped\tSong\tA\tB\t1\t1\tvlc", 0).isEmpty());
        assertTrue(PlayerctlSource.parse("", 0).isEmpty());
    }

    @Test
    void appleScriptLine() {
        NowPlaying track = AppleScriptSource.parse("paused\tSnowman\tSia\tAlbum\t165,5\t2.15E+1\n", "Music", 0)
                .orElseThrow();
        assertEquals(165_500, track.durationMs());
        assertEquals(21_500, track.positionMs());
        assertFalse(track.playing());
        assertEquals("Music", track.player());
    }

    @Test
    void windowsHelperLine() {
        String line = "{\"active\":true,\"title\":\"Снеговик\",\"artist\":\"Sia\",\"album\":null,"
                + "\"positionMs\":1200,\"durationMs\":165000,\"playing\":true,\"app\":\"Spotify.exe\"}";
        NowPlaying track = WindowsMediaSource.parseLine(line, 0).orElseThrow();
        assertEquals("Снеговик", track.title());
        assertEquals("", track.album());
        assertEquals(1200, track.positionMs());
        assertEquals("Spotify", track.player());

        assertTrue(WindowsMediaSource.parseLine("{\"active\":false}", 0).isEmpty());
        assertTrue(WindowsMediaSource.parseLine("{\"active\":false,\"error\":\"boom\"}", 0).isEmpty());
    }

    @Test
    void windowsAppNames() {
        assertEquals("Spotify", WindowsMediaSource.appName("SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify"));
        assertEquals("chrome", WindowsMediaSource.appName("chrome.exe"));
        assertEquals("", WindowsMediaSource.appName(""));
    }
}
