package io.github.fernsehheft.skysong.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.fernsehheft.skysong.Skysong;
import io.github.fernsehheft.skysong.render.Animation;
import io.github.fernsehheft.skysong.render.Placement;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
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
    private static final String DEFAULT_GRADIENT_START = "#FF7EB3";
    private static final String DEFAULT_GRADIENT_END = "#7AD7FF";
    public static final double MIN_HUD_SCALE = 0.5;
    public static final double MAX_HUD_SCALE = 3.0;

    /** Where these settings are saved. Not part of the file itself. */
    private transient Path file;

    /** Where Skysong finds out what's playing. */
    public MusicSource musicSource = MusicSource.AUTO;

    /** Play the built-in demo song instead of listening to the music player. */
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
    /** Single colour, a flowing gradient, or a rainbow for the words that aren't lit up. */
    public ColorMode colorMode = ColorMode.SINGLE;
    public String gradientStart = DEFAULT_GRADIENT_START;
    public String gradientEnd = DEFAULT_GRADIENT_END;
    /** A soft glow in the highlight colour around the words in the world. */
    public boolean glow = false;
    /** Gives the words in the world some thickness, like letters cut out of a block. */
    public boolean depth3d = false;
    /** Little sparks fly off every word as it's sung. */
    public boolean sparkles = true;
    /** The view zooms in a tiny bit when a new line starts, like a cut in a music video. */
    public boolean beatZoom = true;

    /**
     * Most lyrics only have the time of each line. With this on, words light up one by one at
     * a guessed time; off, a line's words come together. Real word timing is always used.
     */
    public boolean estimateWordTiming = false;
    /** Shows the timing details on screen, to find out why lyrics are early or late. */
    public boolean debugOverlay = false;

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
        config.file = file;
        config.fixValues();
        // Write it back so new settings show up in the file and broken values get repaired.
        config.save();
        return config;
    }

    public void save() {
        if (file == null) {
            return;
        }
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
        gradientStart = validColor(gradientStart, DEFAULT_GRADIENT_START);
        gradientEnd = validColor(gradientEnd, DEFAULT_GRADIENT_END);
        if (colorMode == null) {
            colorMode = ColorMode.SINGLE;
        }
        offsetMs = Math.max(-5000, Math.min(5000, offsetMs));
        if (musicSource == null) {
            musicSource = MusicSource.AUTO;
        }
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

    /** Puts every setting back to its default, keeping this object so everything using it sees the change. */
    public void resetToDefaults() {
        SkysongConfig defaults = new SkysongConfig();
        for (Field field : SkysongConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }
            try {
                field.set(this, field.get(defaults));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Can't reset " + field.getName(), e);
            }
        }
    }

    /** {@code text} as a colour like "#FF8800" if it is one, with or without the "#". */
    public static Optional<String> parseColor(String text) {
        if (text == null || !HEX_COLOR.matcher(text.strip()).matches()) {
            return Optional.empty();
        }
        String hex = text.strip();
        return Optional.of((hex.startsWith("#") ? hex : "#" + hex).toUpperCase(Locale.ROOT));
    }

    public int textRgb() {
        return toRgb(textColor);
    }

    public int highlightRgb() {
        return toRgb(highlightColor);
    }

    public int gradientStartRgb() {
        return toRgb(gradientStart);
    }

    public int gradientEndRgb() {
        return toRgb(gradientEnd);
    }

    private static String validColor(String color, String fallback) {
        return parseColor(color).orElse(fallback);
    }

    private static int toRgb(String color) {
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
