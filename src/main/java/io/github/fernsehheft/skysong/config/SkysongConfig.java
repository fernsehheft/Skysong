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
    public static final double MIN_HUD_SCALE = 0.5;
    public static final double MAX_HUD_SCALE = 3.0;

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

    // Positions on screen are fractions of the screen size (0 is left/top, 1 right/bottom)
    // and mark the centre of the element. The HUD editor (K) changes them.

    /** The current line as a subtitle on screen. */
    public boolean karaokeBar = true;
    public KaraokeStyle karaokeStyle = KaraokeStyle.PILL;
    public double karaokeX = DEFAULT_KARAOKE_X;
    public double karaokeY = DEFAULT_KARAOKE_Y;
    public double karaokeScale = 1.0;

    /** A small card with the song, the time and whether lyrics were found. */
    public boolean nowPlayingCard = true;
    public double cardX = DEFAULT_CARD_X;
    public double cardY = DEFAULT_CARD_Y;
    public double cardScale = 1.0;

    /** Black bars at the top and bottom of the screen while music plays. */
    public boolean cinematicBars = false;
    /** Height of each bar as a share of the screen height. */
    public double cinematicBarSize = 0.09;

    /** Big song title in the middle of the screen when a new song starts. */
    public boolean titleIntro = true;

    /** Keep the karaoke bar, cinematic bars and title intro when the game HUD is hidden with F1. */
    public boolean hudWhenHidden = true;

    public enum KaraokeStyle {
        /** On a dark rounded background. */
        PILL,
        /** Just the text with a shadow. */
        MINIMAL
    }

    public static final double DEFAULT_KARAOKE_X = 0.5;
    public static final double DEFAULT_KARAOKE_Y = 0.22;
    public static final double DEFAULT_CARD_X = 0.86;
    public static final double DEFAULT_CARD_Y = 0.09;

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
        fixValues();
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
        if (karaokeStyle == null) {
            karaokeStyle = KaraokeStyle.PILL;
        }
        // Rounded, because dragging in the HUD editor gives values like 0.5011709601873536.
        karaokeX = round(clamp(karaokeX, 0, 1), 3);
        karaokeY = round(clamp(karaokeY, 0, 1), 3);
        karaokeScale = round(clamp(karaokeScale, MIN_HUD_SCALE, MAX_HUD_SCALE), 2);
        cardX = round(clamp(cardX, 0, 1), 3);
        cardY = round(clamp(cardY, 0, 1), 3);
        cardScale = round(clamp(cardScale, MIN_HUD_SCALE, MAX_HUD_SCALE), 2);
        cinematicBarSize = clamp(cinematicBarSize, 0.02, 0.25);
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

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }
}
