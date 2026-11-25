package io.github.fernsehheft.skysong.render;

import io.github.fernsehheft.skysong.config.ColorMode;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LyricColorsTest {

    @Test
    void singleColourIsTheTextColour(@TempDir Path directory) {
        SkysongConfig config = SkysongConfig.load(directory.resolve("settings.json"));
        config.textColor = "#123456";
        assertEquals(0x123456, LyricColors.base(config, 3, 5, 12345));
    }

    @Test
    void gradientRunsBetweenItsColoursAndFlows(@TempDir Path directory) {
        SkysongConfig config = SkysongConfig.load(directory.resolve("settings.json"));
        config.colorMode = ColorMode.GRADIENT;
        config.gradientStart = "#000000";
        config.gradientEnd = "#FFFFFF";
        assertEquals(0x000000, LyricColors.base(config, 0, 5, 0));
        assertEquals(0xFFFFFF, LyricColors.base(config, 4, 5, 0));
        // Half a cycle later the colours have swapped places.
        assertEquals(0xFFFFFF, LyricColors.base(config, 0, 5, 4000));
    }

    @Test
    void rainbowChangesAlongTheLineAndOverTime(@TempDir Path directory) {
        SkysongConfig config = SkysongConfig.load(directory.resolve("settings.json"));
        config.colorMode = ColorMode.RAINBOW;
        int first = LyricColors.base(config, 0, 5, 0);
        assertNotEquals(first, LyricColors.base(config, 3, 5, 0));
        assertNotEquals(first, LyricColors.base(config, 0, 5, 2000));
    }

    @Test
    void sungWordsGoBackToTheirOwnColour() {
        assertEquals(0x000000, LyricColors.afterSinging(0x000000, 0xFFFFFF, -10));
        assertEquals(0xFFFFFF, LyricColors.afterSinging(0x000000, 0xFFFFFF, 0));
        assertEquals(0x000000, LyricColors.afterSinging(0x000000, 0xFFFFFF, 5000));
    }
}
