package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Everything Skysong draws on the screen (as opposed to in the world). */
public final class SkysongHud {
    private final LyricsPlayback playback;
    private final SkysongConfig config;
    private final CinematicBars cinematicBars;
    private final TitleIntro titleIntro;
    private final KaraokeBar karaokeBar;
    private final NowPlayingCard nowPlayingCard;
    private long lastFrame = System.nanoTime();

    public SkysongHud(LyricsPlayback playback, SkysongConfig config) {
        this.playback = playback;
        this.config = config;
        this.cinematicBars = new CinematicBars(config);
        this.titleIntro = new TitleIntro(config);
        this.karaokeBar = new KaraokeBar(config);
        this.nowPlayingCard = new NowPlayingCard(config);
    }

    /** Drawn together with the normal game HUD. */
    public void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        // The editor draws its own previews of the movable elements.
        boolean editing = minecraft.screen instanceof HudEditScreen;
        draw(graphics, !minecraft.options.renderDebug && !editing, !editing);
    }

    /** Drawn instead of the game HUD while it's hidden with F1, for recording videos. */
    public void renderWhileHidden(GuiGraphics graphics) {
        if (config.hudWhenHidden) {
            draw(graphics, false, true);
        }
    }

    private void draw(GuiGraphics graphics, boolean withCard, boolean withKaraoke) {
        long now = System.nanoTime();
        float seconds = Math.min(0.25f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        Font font = Minecraft.getInstance().font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        LyricsPlayback.Moment moment = playback.now().orElse(null);

        cinematicBars.render(graphics, width, height, moment, seconds);
        titleIntro.render(graphics, font, width, height, moment);
        if (withKaraoke && karaokeBar.enabled()) {
            karaokeBar.render(graphics, font, width, height, moment, false);
        }
        if (withCard && nowPlayingCard.enabled()) {
            nowPlayingCard.render(graphics, font, width, height, moment, false);
        }
    }

    List<MovableElement> movableElements() {
        return List.of(karaokeBar, nowPlayingCard);
    }

    LyricsPlayback playback() {
        return playback;
    }
}
