package io.github.fernsehheft.skysong.gui;

import io.github.fernsehheft.skysong.Skysong;
import io.github.fernsehheft.skysong.SkysongClient;
import io.github.fernsehheft.skysong.config.ColorMode;
import io.github.fernsehheft.skysong.config.LookPreset;
import io.github.fernsehheft.skysong.config.MusicSource;
import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.hud.HudEditScreen;
import io.github.fernsehheft.skysong.hud.SkysongHud;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import io.github.fernsehheft.skysong.render.Animation;
import io.github.fernsehheft.skysong.render.LyricFont;
import io.github.fernsehheft.skysong.render.Placement;
import io.github.fernsehheft.skysong.spotify.Spotify;
import io.github.fernsehheft.skysong.spotify.SpotifyAccount;
import io.github.fernsehheft.skysong.spotify.SpotifyApi;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Skysong's settings. They sit in a panel on the left so the world, with the lyrics and
 * the HUD, stays visible on the right and every change can be seen right away.
 */
public final class SkysongScreen extends Screen {
    private static final int MARGIN = 6;
    private static final int MAX_PANEL_WIDTH = 230;
    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int HEADER_HEIGHT = 16;
    private static final int TEXT_HEIGHT = 12;
    private static final int TABS_TOP = 26;
    private static final int CONTENT_TOP = 52;
    private static final int FOOTER_HEIGHT = 30;
    private static final int COLOR_FIELD_WIDTH = 64;
    private static final int SWATCH_SIZE = 16;
    private static final int SCROLL_SPEED = 16;
    private static final String SPOTIFY_DASHBOARD = "https://developer.spotify.com/dashboard";

    private static final int PANEL_COLOR = 0xD0101018;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int HEADER_COLOR = 0xFFFFD966;
    private static final int TEXT_COLOR = 0xFFAAAAAA;
    private static final int HINT_COLOR = 0xFFDDDDDD;

    private enum Tab {
        WORLD, LOOK, SCREEN, MUSIC;

        Component label() {
            return Component.translatable("skysong.settings.tab." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** One line in the scrolling list of settings. */
    private sealed interface Row {
        int height();
    }

    private record WidgetRow(AbstractWidget widget) implements Row {
        @Override
        public int height() {
            return ROW_HEIGHT;
        }
    }

    /**
     * A text field. With a label it sits on the right next to it, without one it fills
     * the row. {@code rgb} adds a colour swatch for colour fields.
     */
    private record FieldRow(Component label, EditBox field, int fieldWidth, Supplier<Integer> rgb) implements Row {
        @Override
        public int height() {
            return ROW_HEIGHT;
        }
    }

    /** A longer explanation that wraps over several lines. */
    private record WrappedTextRow(List<FormattedCharSequence> lines, int lineHeight) implements Row {
        @Override
        public int height() {
            return lines.size() * lineHeight;
        }
    }

    private record HeaderRow(Component text) implements Row {
        @Override
        public int height() {
            return HEADER_HEIGHT;
        }
    }

    private record TextRow(Supplier<Component> text) implements Row {
        @Override
        public int height() {
            return TEXT_HEIGHT;
        }
    }

    // Kept across rebuilds, which happen whenever the screen is resized or a preset is picked.
    private static Tab lastTab = Tab.WORLD;

    private final Screen parent;
    private final SkysongConfig config;
    private final SkysongHud hud;
    private final Path lyricsFolder;
    private final Spotify spotify;
    private final List<Row> rows = new ArrayList<>();

    private Tab tab = lastTab;
    private double scroll;
    // Also set from the Spotify login's thread when the login is over.
    private volatile boolean rebuildNextTick;
    private Button presetButton;

    /**
     * @param parent the screen to go back to, or null to go back to the game
     */
    public SkysongScreen(Screen parent, SkysongConfig config, SkysongHud hud, Path lyricsFolder, Spotify spotify) {
        super(Component.translatable("skysong.settings.title"));
        this.parent = parent;
        this.config = config;
        this.hud = hud;
        this.lyricsFolder = lyricsFolder;
        this.spotify = spotify;
    }

    private int panelWidth() {
        return Math.min(MAX_PANEL_WIDTH, width / 2);
    }

    private int rowWidth() {
        // Leaves room for the scroll bar.
        return panelWidth() - PADDING * 2 - 4;
    }

    private int contentBottom() {
        return height - MARGIN - FOOTER_HEIGHT;
    }

    @Override
    protected void init() {
        rows.clear();
        presetButton = null;

        int tabWidth = (panelWidth() - PADDING * 2 - 6) / Tab.values().length;
        int x = MARGIN + PADDING;
        for (Tab each : Tab.values()) {
            Button button = addRenderableWidget(Button.builder(each.label(), b -> switchTo(each))
                    .bounds(x, TABS_TOP, tabWidth, ROW_HEIGHT)
                    .build());
            // The open tab is shown as a pressed, inactive button.
            button.active = each != tab;
            x += tabWidth + 2;
        }

        switch (tab) {
            case WORLD -> buildWorldTab();
            case LOOK -> buildLookTab();
            case SCREEN -> buildScreenTab();
            case MUSIC -> buildMusicTab();
        }

        int footerY = height - MARGIN - PADDING - ROW_HEIGHT + 2;
        int half = (panelWidth() - PADDING * 2 - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("skysong.settings.move_hud"), b -> openHudEditor())
                .bounds(MARGIN + PADDING, footerY, half, ROW_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(MARGIN + PADDING + half + 4, footerY, half, ROW_HEIGHT)
                .build());

        scroll = Math.min(scroll, maxScroll());
        layoutRows();
        PreviewArea.coverLeft(MARGIN + panelWidth());
    }

    private void buildWorldTab() {
        header("world");
        toggle("world_lyrics", config.worldLyrics, value -> config.worldLyrics = value);
        choice("placement", Placement.values(), config.placement, value -> config.placement = value);
        choice("animation", Animation.values(), config.animation, value -> {
            config.animation = value;
            updatePresetButton();
        });
        slider("distance", 1.5, 12, 0.5, config.distance, value -> config.distance = value,
                value -> Component.translatable("skysong.settings.unit.blocks", String.format(Locale.ROOT, "%.1f", value)));
        toggle("react_to_song", config.reactToSong, value -> config.reactToSong = value);
        slider("size", 0.25, 4, 0.05, config.size, value -> {
            config.size = value;
            updatePresetButton();
        }, SkysongScreen::percent);
    }

    private void buildLookTab() {
        header("look");
        // A plain button rather than a cycle button: the shown preset follows the settings,
        // so cycling through "Custom" would land right back on the preset that matches.
        presetButton = Button.builder(presetLabel(), b -> {
            LookPreset.next(LookPreset.matching(config)).applyTo(config);
            // Other buttons show values the preset just changed, so build them anew.
            rebuildNextTick = true;
        }).size(rowWidth(), ROW_HEIGHT).build();
        addRow("preset", presetButton);
        choice("font", LyricFont.values(), config.font, value -> config.font = value);
        choice("color_mode", ColorMode.values(), config.colorMode, value -> {
            config.colorMode = value;
            updatePresetButton();
        });
        color("text_color", config.textColor, value -> config.textColor = value, config::textRgb);
        color("gradient_start", config.gradientStart, value -> config.gradientStart = value, config::gradientStartRgb);
        color("gradient_end", config.gradientEnd, value -> config.gradientEnd = value, config::gradientEndRgb);
        color("highlight_color", config.highlightColor, value -> config.highlightColor = value, config::highlightRgb);
        choice("karaoke_style", SkysongConfig.KaraokeStyle.values(), config.karaokeStyle, value -> {
            config.karaokeStyle = value;
            updatePresetButton();
        });

        header("effects");
        toggle("glow", config.glow, value -> {
            config.glow = value;
            updatePresetButton();
        });
        toggle("depth3d", config.depth3d, value -> {
            config.depth3d = value;
            updatePresetButton();
        });
        toggle("sparkles", config.sparkles, value -> config.sparkles = value);
        toggle("beat_zoom", config.beatZoom, value -> config.beatZoom = value);
    }

    private void buildScreenTab() {
        header("screen");
        toggle("karaoke_bar", config.karaokeBar, value -> config.karaokeBar = value);
        toggle("now_playing_card", config.nowPlayingCard, value -> config.nowPlayingCard = value);
        toggle("title_intro", config.titleIntro, value -> config.titleIntro = value);
        toggle("cinematic_bars", config.cinematicBars, value -> config.cinematicBars = value);
        slider("cinematic_bar_size", 0.02, 0.25, 0.01, config.cinematicBarSize,
                value -> config.cinematicBarSize = value, SkysongScreen::percent);
        toggle("hud_when_hidden", config.hudWhenHidden, value -> config.hudWhenHidden = value);
    }

    private void buildMusicTab() {
        header("music");
        rows.add(new TextRow(this::playingText));
        rows.add(new TextRow(this::lyricsText));
        choice("music_source", MusicSource.values(), config.musicSource, value -> config.musicSource = value);
        toggle("demo_song", config.demoSong, value -> config.demoSong = value);
        toggle("estimate_word_timing", config.estimateWordTiming, value -> config.estimateWordTiming = value);
        toggle("debug_overlay", config.debugOverlay, value -> config.debugOverlay = value);
        slider("offset", -2000, 2000, 50, config.offsetMs, value -> config.offsetMs = Math.round(value),
                value -> Component.translatable("skysong.settings.unit.ms", String.format(Locale.ROOT, "%+d", Math.round(value))));
        button("open_lyrics_folder", () -> Util.getPlatform().openFile(lyricsFolder.toFile()));

        header("song_offset");
        rows.add(new TextRow(this::songOffsetText));
        button("song_offset.earlier", () -> nudgeSong(SkysongClient.SONG_OFFSET_STEP_MS));
        button("song_offset.later", () -> nudgeSong(-SkysongClient.SONG_OFFSET_STEP_MS));
        button("song_offset.reset", () -> hud.playback().now()
                .ifPresent(moment -> hud.playback().songOffsets().reset(moment.track())));

        buildSpotifySection();

        header("other");
        button("reset", this::confirmReset);
    }

    private void buildSpotifySection() {
        header("spotify");
        rows.add(new TextRow(this::spotifyStatusText));
        Spotify.LoginStatus status = spotify.status();
        if (status == Spotify.LoginStatus.LOGGED_IN) {
            button("spotify.log_out", () -> {
                spotify.logOut();
                rebuildNextTick = true;
            });
            return;
        }

        wrappedText("spotify.help");
        button("spotify.open_dashboard", () -> Util.getPlatform().openUri(SPOTIFY_DASHBOARD));
        Button copy = Button.builder(label("spotify.copy_redirect"), b -> {
            minecraft.keyboardHandler.setClipboard(SpotifyApi.REDIRECT_URI);
            b.setMessage(label("spotify.copied"));
        }).size(rowWidth(), ROW_HEIGHT).build();
        addRow("spotify.copy_redirect", copy);
        rows.add(new TextRow(() -> Component.literal(SpotifyApi.REDIRECT_URI)));

        rows.add(new TextRow(() -> label("spotify.client_id")));
        Button logIn = Button.builder(label("spotify.log_in"), b -> logIn())
                .size(rowWidth(), ROW_HEIGHT).build();
        EditBox clientId = new EditBox(font, 0, 0, rowWidth(), ROW_HEIGHT, label("spotify.client_id"));
        clientId.setMaxLength(64);
        clientId.setValue(spotify.account().clientId());
        clientId.setHint(label("spotify.client_id.hint"));
        clientId.setResponder(text -> {
            boolean valid = SpotifyAccount.looksLikeClientId(text);
            if (valid) {
                spotify.account().setClientId(text);
            }
            logIn.active = valid && spotify.status() != Spotify.LoginStatus.WAITING;
        });
        logIn.active = SpotifyAccount.looksLikeClientId(clientId.getValue()) && status != Spotify.LoginStatus.WAITING;
        addRenderableWidget(clientId);
        rows.add(new FieldRow(null, clientId, rowWidth(), null));
        addRow("spotify.log_in", logIn);
        if (status == Spotify.LoginStatus.WAITING) {
            button("spotify.cancel", () -> {
                spotify.cancelLogin();
                rebuildNextTick = true;
            });
        }
    }

    private void logIn() {
        spotify.logIn(url -> Util.getPlatform().openUri(url), () -> rebuildNextTick = true);
        rebuildNextTick = true;
    }

    private Component spotifyStatusText() {
        return switch (spotify.status()) {
            case LOGGED_OUT -> label("spotify.status.logged_out");
            case WAITING -> label("spotify.status.waiting");
            case LOGGED_IN -> label("spotify.status.logged_in");
            case FAILED -> Component.translatable("skysong.settings.spotify.status.failed", spotify.problem());
        };
    }

    private void nudgeSong(long changeMs) {
        hud.playback().now().ifPresent(moment -> hud.playback().songOffsets().adjust(moment.track(), changeMs));
    }

    private Component songOffsetText() {
        return hud.playback().now()
                .map(moment -> SkysongClient.describeSongOffset(hud.playback().songOffsets().get(moment.track())))
                .orElse(label("song_offset.nothing_playing"));
    }

    private Component playingText() {
        return hud.playback().now()
                .map(moment -> (Component) Component.translatable("skysong.settings.playing",
                        moment.track().title(), moment.track().player()))
                .orElse(Component.translatable("skysong.settings.nothing_playing"));
    }

    private Component lyricsText() {
        LyricsPlayback.Moment moment = hud.playback().now().orElse(null);
        if (moment == null) {
            return Component.empty();
        }
        String status = moment.status().name().toLowerCase(Locale.ROOT);
        return Component.translatable("skysong.settings.lyrics_status." + status,
                moment.source() == null ? "" : moment.source());
    }

    // Row builders. Every setting's label comes from "skysong.settings.<key>", and its
    // tooltip from "skysong.settings.<key>.tooltip" if the language file has one.

    private void header(String key) {
        rows.add(new HeaderRow(Component.translatable("skysong.settings.header." + key)));
    }

    private void toggle(String key, boolean current, Consumer<Boolean> setter) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(current)
                .create(0, 0, rowWidth(), ROW_HEIGHT, label(key), (b, value) -> setter.accept(value));
        addRow(key, button);
    }

    private <T extends Enum<T>> CycleButton<T> choice(String key, T[] values, T current, Consumer<T> setter) {
        Function<T, Component> name = value ->
                Component.translatable("skysong.settings." + key + "." + value.name().toLowerCase(Locale.ROOT));
        CycleButton<T> button = CycleButton.builder(name)
                .withValues(values)
                .withInitialValue(current)
                .create(0, 0, rowWidth(), ROW_HEIGHT, label(key), (b, value) -> setter.accept(value));
        addRow(key, button);
        return button;
    }

    private void slider(String key, double min, double max, double step, double current,
                        DoubleConsumer setter, DoubleFunction<Component> format) {
        addRow(key, new SettingSlider(rowWidth(), label(key), min, max, step, current, setter, format));
    }

    private void wrappedText(String key) {
        List<FormattedCharSequence> lines = font.split(label(key), rowWidth());
        rows.add(new WrappedTextRow(lines, font.lineHeight + 1));
    }

    private void color(String key, String current, Consumer<String> setter, Supplier<Integer> rgb) {
        EditBox field = new EditBox(font, 0, 0, COLOR_FIELD_WIDTH, ROW_HEIGHT, label(key));
        field.setMaxLength(7);
        field.setValue(current);
        field.setFilter(text -> text.matches("#?[0-9a-fA-F]{0,6}"));
        field.setResponder(text -> SkysongConfig.parseColor(text).ifPresent(color -> {
            setter.accept(color);
            updatePresetButton();
        }));
        addTooltip(key, field);
        addRenderableWidget(field);
        rows.add(new FieldRow(label(key), field, COLOR_FIELD_WIDTH, rgb));
    }

    private void button(String key, Runnable action) {
        addRow(key, Button.builder(label(key), b -> action.run()).size(rowWidth(), ROW_HEIGHT).build());
    }

    private void addRow(String key, AbstractWidget widget) {
        addTooltip(key, widget);
        addRenderableWidget(widget);
        rows.add(new WidgetRow(widget));
    }

    private static void addTooltip(String key, AbstractWidget widget) {
        String tooltipKey = "skysong.settings." + key + ".tooltip";
        if (I18n.exists(tooltipKey)) {
            widget.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
    }

    private static Component label(String key) {
        return Component.translatable("skysong.settings." + key);
    }

    private static Component percent(double value) {
        return Component.literal(Math.round(value * 100) + "%");
    }

    private Component presetLabel() {
        String preset = LookPreset.matching(config).name().toLowerCase(Locale.ROOT);
        return Component.translatable("skysong.settings.value", label("preset"), label("preset." + preset));
    }

    private void updatePresetButton() {
        if (presetButton != null) {
            presetButton.setMessage(presetLabel());
        }
    }

    private void switchTo(Tab next) {
        tab = next;
        lastTab = next;
        scroll = 0;
        // Not right away: we're inside the click handler of a button that is about to be replaced.
        rebuildNextTick = true;
    }

    private void openHudEditor() {
        minecraft.setScreen(new HudEditScreen(this, hud, config));
    }

    private void confirmReset() {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                config.resetToDefaults();
                config.save();
                Skysong.LOGGER.info("Settings were reset to their defaults");
            }
            minecraft.setScreen(this);
        }, Component.translatable("skysong.settings.reset.confirm"), Component.translatable("skysong.settings.reset.confirm_details")));
    }

    // Scrolling

    private int contentHeight() {
        int total = 0;
        for (Row row : rows) {
            total += row.height() + ROW_GAP;
        }
        return total;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - (contentBottom() - CONTENT_TOP));
    }

    /** Puts every widget at its row's place and hides the ones that are scrolled out of the panel. */
    private void layoutRows() {
        int x = MARGIN + PADDING;
        int y = CONTENT_TOP - (int) Math.round(scroll);
        for (Row row : rows) {
            boolean inside = y >= CONTENT_TOP && y + row.height() <= contentBottom();
            if (row instanceof WidgetRow widgetRow) {
                widgetRow.widget().setPosition(x, y);
                widgetRow.widget().visible = inside;
            } else if (row instanceof FieldRow fieldRow) {
                int swatchSpace = fieldRow.rgb() == null ? 0 : SWATCH_SIZE + 4;
                fieldRow.field().setPosition(x + rowWidth() - fieldRow.fieldWidth() - swatchSpace, y);
                fieldRow.field().visible = inside;
            }
            y += row.height() + ROW_GAP;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < MARGIN + panelWidth()) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * SCROLL_SPEED));
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        if (rebuildNextTick) {
            rebuildNextTick = false;
            rebuildWidgets();
        }
        for (Row row : rows) {
            if (row instanceof FieldRow fieldRow) {
                fieldRow.field().tick();
            }
        }
    }

    // Drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            // Opened from the title screen (through Mod Menu): no world behind us to show.
            renderBackground(graphics);
        }
        int panelRight = MARGIN + panelWidth();
        graphics.fill(MARGIN, MARGIN, panelRight, height - MARGIN, PANEL_COLOR);
        graphics.drawString(font, title, MARGIN + PADDING, MARGIN + 6, TITLE_COLOR);
        String version = Skysong.version();
        graphics.drawString(font, version, panelRight - PADDING - font.width(version), MARGIN + 6, TEXT_COLOR);

        drawRows(graphics);
        drawScrollBar(graphics, panelRight);
        drawPreviewHint(graphics, panelRight);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRows(GuiGraphics graphics) {
        int x = MARGIN + PADDING;
        int y = CONTENT_TOP - (int) Math.round(scroll);
        for (Row row : rows) {
            boolean inside = y >= CONTENT_TOP && y + row.height() <= contentBottom();
            if (row instanceof WrappedTextRow wrapped) {
                // Long texts can be taller than the panel, so each line is shown on its own.
                int lineY = y;
                for (FormattedCharSequence line : wrapped.lines()) {
                    if (lineY >= CONTENT_TOP && lineY + wrapped.lineHeight() <= contentBottom()) {
                        graphics.drawString(font, line, x, lineY, TEXT_COLOR);
                    }
                    lineY += wrapped.lineHeight();
                }
            } else if (inside) {
                if (row instanceof HeaderRow header) {
                    graphics.drawString(font, header.text(), x, y + 4, HEADER_COLOR);
                } else if (row instanceof TextRow text) {
                    graphics.drawString(font, fit(text.text().get(), rowWidth()), x, y + 2, TEXT_COLOR);
                } else if (row instanceof FieldRow color && color.rgb() != null) {
                    graphics.drawString(font, color.label(), x, y + 6, HINT_COLOR);
                    int swatchX = x + rowWidth() - SWATCH_SIZE;
                    int swatchY = y + (ROW_HEIGHT - SWATCH_SIZE) / 2;
                    graphics.fill(swatchX - 1, swatchY - 1, swatchX + SWATCH_SIZE + 1, swatchY + SWATCH_SIZE + 1, 0xFFFFFFFF);
                    graphics.fill(swatchX, swatchY, swatchX + SWATCH_SIZE, swatchY + SWATCH_SIZE, 0xFF000000 | color.rgb().get());
                }
            }
            y += row.height() + ROW_GAP;
        }
    }

    private String fit(Component text, int maxWidth) {
        String plain = text.getString();
        if (font.width(plain) <= maxWidth) {
            return plain;
        }
        return font.plainSubstrByWidth(plain, maxWidth - font.width("...")) + "...";
    }

    private void drawScrollBar(GuiGraphics graphics, int panelRight) {
        double max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackTop = CONTENT_TOP;
        int trackHeight = contentBottom() - CONTENT_TOP;
        int thumbHeight = Math.max(12, (int) (trackHeight * trackHeight / (double) contentHeight()));
        int thumbTop = trackTop + (int) ((trackHeight - thumbHeight) * (scroll / max));
        int x = panelRight - PADDING / 2 - 2;
        graphics.fill(x, trackTop, x + 2, trackTop + trackHeight, 0x40FFFFFF);
        graphics.fill(x, thumbTop, x + 2, thumbTop + thumbHeight, 0xC0FFFFFF);
    }

    /** Explains how to see the changes when there's nothing to show on the right. */
    private void drawPreviewHint(GuiGraphics graphics, int panelRight) {
        Component hint = null;
        if (minecraft.level == null) {
            hint = Component.translatable("skysong.settings.preview.no_world");
        } else if (hud.playback().now().isEmpty()) {
            hint = Component.translatable("skysong.settings.preview.no_music");
        }
        if (hint == null) {
            return;
        }
        int areaX = panelRight + PADDING;
        int areaWidth = width - areaX - PADDING;
        List<FormattedCharSequence> lines = font.split(hint, Math.max(60, areaWidth));
        int y = height / 2 - lines.size() * font.lineHeight / 2;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, areaX + (areaWidth - font.width(line)) / 2, y, HINT_COLOR);
            y += font.lineHeight;
        }
    }

    @Override
    public void removed() {
        PreviewArea.clear();
        config.save();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        // Keep the world running behind the settings, so the preview stays alive.
        return false;
    }
}
