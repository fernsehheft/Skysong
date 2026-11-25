package io.github.fernsehheft.skysong.config;

import io.github.fernsehheft.skysong.render.Animation;
import io.github.fernsehheft.skysong.render.Placement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkysongConfigTest {

    @Test
    void createsTheFileWithDefaults(@TempDir Path directory) {
        Path file = directory.resolve("skysong/settings.json");
        SkysongConfig config = SkysongConfig.load(file);
        assertTrue(Files.isRegularFile(file));
        assertEquals(Animation.KARAOKE, config.animation);
    }

    @Test
    void readsAndRepairsHandEditedValues(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, """
                {
                  "placement": "SCATTERED",
                  "animation": "DANCING",
                  "distance": 500,
                  "textColor": "ff8800",
                  "highlightColor": "not a colour"
                }
                """);
        SkysongConfig config = SkysongConfig.load(file);
        assertEquals(Placement.SCATTERED, config.placement);
        assertEquals(Animation.KARAOKE, config.animation);
        assertEquals(12.0, config.distance);
        assertEquals("#FF8800", config.textColor);
        assertEquals(0xFF8800, config.textRgb());
        assertEquals(0xFFD966, config.highlightRgb());
        assertTrue(config.worldLyrics);
    }

    @Test
    void brokenFileFallsBackToDefaults(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{ this is not json");
        assertEquals(Placement.AHEAD, SkysongConfig.load(file).placement);
    }

    @Test
    void savesTidyNumbers(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("settings.json");
        SkysongConfig config = SkysongConfig.load(file);
        config.karaokeX = 0.5011709601873536;
        config.karaokeScale = 1.3000000000000003;
        config.save();
        String saved = Files.readString(file);
        assertTrue(saved.contains("\"karaokeX\": 0.501,"), saved);
        assertTrue(saved.contains("\"karaokeScale\": 1.3,"), saved);
    }

    @Test
    void resetKeepsTheFileButRestoresDefaults(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("settings.json");
        SkysongConfig config = SkysongConfig.load(file);
        config.animation = Animation.RISE;
        config.cinematicBars = true;
        config.resetToDefaults();
        assertEquals(Animation.KARAOKE, config.animation);
        assertEquals(false, config.cinematicBars);

        config.save();
        assertEquals(Animation.KARAOKE, SkysongConfig.load(file).animation);
    }

    @Test
    void presetsApplyAndAreRecognised(@TempDir Path directory) {
        SkysongConfig config = SkysongConfig.load(directory.resolve("settings.json"));
        assertEquals(LookPreset.CLASSIC, LookPreset.matching(config));

        LookPreset.NEON.applyTo(config);
        assertEquals(LookPreset.NEON, LookPreset.matching(config));

        config.highlightColor = "#123456";
        assertEquals(LookPreset.CUSTOM, LookPreset.matching(config));
    }

    @Test
    void parsesColours() {
        assertEquals("#FF8800", SkysongConfig.parseColor(" ff8800 ").orElseThrow());
        assertTrue(SkysongConfig.parseColor("#FF88").isEmpty());
    }

    @Test
    void thePresetButtonNeverGetsStuck() {
        // Cycling has to reach every real preset and come back round, skipping "Custom".
        LookPreset preset = LookPreset.BOLD;
        for (int i = 0; i < LookPreset.values().length - 1; i++) {
            preset = LookPreset.next(preset);
            assertTrue(preset != LookPreset.CUSTOM);
        }
        assertEquals(LookPreset.BOLD, preset);
        assertEquals(LookPreset.CLASSIC, LookPreset.next(LookPreset.CUSTOM));
    }
}
