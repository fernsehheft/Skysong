package io.github.fernsehheft.skysong.media;

import io.github.fernsehheft.skysong.config.MusicSource;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.spotify.Spotify;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchableSourceTest {
    private static final MediaSource SYSTEM = new MediaSource() {
        @Override
        public String name() {
            return "system";
        }

        @Override
        public Optional<NowPlaying> poll() {
            return Optional.empty();
        }
    };

    private static Spotify spotify(Path directory, boolean loggedIn) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("spotify.json");
        if (loggedIn) {
            Files.writeString(file, "{\"clientId\":\"0123456789abcdef0123456789abcdef\",\"refreshToken\":\"token\"}");
        }
        return new Spotify(file);
    }

    @Test
    void picksTheRightSource(@TempDir Path directory) throws IOException {
        SkysongConfig config = SkysongConfig.load(directory.resolve("settings.json"));
        SwitchableSource loggedOut = new SwitchableSource(SYSTEM, spotify(directory.resolve("a"), false), config);
        SwitchableSource loggedIn = new SwitchableSource(SYSTEM, spotify(directory.resolve("b"), true), config);

        config.musicSource = MusicSource.AUTO;
        assertEquals(SwitchableSource.Active.SYSTEM, loggedOut.active());
        assertEquals(SwitchableSource.Active.SPOTIFY, loggedIn.active());

        config.musicSource = MusicSource.SPOTIFY;
        assertEquals(SwitchableSource.Active.NONE, loggedOut.active());

        config.musicSource = MusicSource.SYSTEM;
        assertEquals(SwitchableSource.Active.SYSTEM, loggedIn.active());

        config.demoSong = true;
        assertEquals(SwitchableSource.Active.DEMO, loggedIn.active());
        assertTrue(DemoSource.isDemo(loggedIn.poll().orElseThrow()));
    }

    @Test
    void withoutASystemPlayerOnlySpotifyOrTheDemoWork(@TempDir Path directory) throws IOException {
        SkysongConfig config = SkysongConfig.load(directory.resolve("settings.json"));
        SwitchableSource source = new SwitchableSource(null, spotify(directory, false), config);
        assertEquals(SwitchableSource.Active.NONE, source.active());
        assertTrue(source.poll().isEmpty());
    }
}
