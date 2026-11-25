package io.github.fernsehheft.skysong.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.fernsehheft.skysong.Skysong;
import io.github.fernsehheft.skysong.render.Animation;
import io.github.fernsehheft.skysong.render.Placement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Everything the player can change, saved as {@code settings.json}. Field names are
 * the keys in that file, so renaming one resets it for everybody.
 */
public final class SkysongConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern HEX_COLOR = Pattern.compile("#?[0-9a-fA-F]{6}");
    private static final String DEFAULT_TEXT_COLOR = "#FFFFFF";
    private static final String DEFAULT_HIGHLIGHT_COLOR = "#FFD966";

    /** Play the built-in demo song instead of listening to the music player. Needs a restart. */
    public boolean demoSong = false;
    /** Show the lyrics in the world at all. */
    public boolean worldLyrics = true;
    public Placement placement = Placement.AHEAD;
    public Animation animation = Animation.KARAOKE;
    /** How far away the lyrics appear, in blocks. */
    public double distance = 4.0;
    /** 1.0 is the normal size. */
    public double size = 1.0;
    /** Colour of words that haven't been sung yet, like "#FFFFFF". */
    public String textColor = DEFAULT_TEXT_COLOR;
    /** Colour of the word being sung. */
    public String highlightColor = DEFAULT_HIGHLIGHT_COLOR;
    /** Shows the lyrics this many milliseconds earlier (or later, if negative). */
    public long offsetMs = 0;

    public static SkysongConfig load(Path file) {
        SkysongConfig config = null;
        if (Files.isRegularFile(file)) {
            try {
                config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SkysongConfig.class);
            } catch (IOException | JsonParseException e) {
                Skysong.LOGGER.warn("Couldn't read {}, using the default settings", file, e);
            }
        }
        if (config == null) {
            config = new SkysongConfig();
        }
        config.fixValues();
        // Write it back so new settings show up in the file and broken values get repaired.
        config.save(file);
        return config;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Skysong.LOGGER.warn("Couldn't save the settings to {}", file, e);
        }
    }

    /** Puts hand-edited values back into a range that works. */
    void fixValues() {
        if (placement == null) {
            placement = Placement.AHEAD;
        }
        if (animation == null) {
            animation = Animation.KARAOKE;
        }
        distance = clamp(distance, 1.5, 12.0);
        size = clamp(size, 0.25, 4.0);
        textColor = validColor(textColor, DEFAULT_TEXT_COLOR);
        highlightColor = validColor(highlightColor, DEFAULT_HIGHLIGHT_COLOR);
        offsetMs = Math.max(-5000, Math.min(5000, offsetMs));
    }

    public int textRgb() {
        return parseColor(textColor);
    }

    public int highlightRgb() {
        return parseColor(highlightColor);
    }

    private static String validColor(String color, String fallback) {
        if (color == null || !HEX_COLOR.matcher(color.strip()).matches()) {
            return fallback;
        }
        String hex = color.strip();
        return (hex.startsWith("#") ? hex : "#" + hex).toUpperCase(Locale.ROOT);
    }

    private static int parseColor(String color) {
        return Integer.parseInt(color.substring(1), 16);
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }
}
