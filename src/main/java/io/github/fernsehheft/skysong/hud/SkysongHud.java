package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.config.SkysongConfig;
import io.github.fernsehheft.skysong.gui.PreviewArea;
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
    private final DebugOverlay debugOverlay;
    private long lastFrame = System.nanoTime();
    private static final float PAUSE_FADE_SECONDS = 0.4f;

    public SkysongHud(LyricsPlayback playback, SkysongConfig config) {
        this.playback = playback;
        this.config = config;
        this.cinematicBars = new CinematicBars(config);
        this.titleIntro = new TitleIntro(config);
        this.karaokeBar = new KaraokeBar(config);
        this.nowPlayingCard = new NowPlayingCard(config);
        this.debugOverlay = new DebugOverlay(config, playback);
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
        int height = graphics.guiHeight();
        LyricsPlayback.Moment moment = playback.now().orElse(null);
        // The karaoke bar fades away while the music is paused and comes back with it.
        boolean playing = moment != null && moment.track().playing();
        float step = seconds / PAUSE_FADE_SECONDS;
        karaokeBar.visibility = playing ? Math.min(1, karaokeBar.visibility + step) : Math.max(0, karaokeBar.visibility - step);
        cinematicBars.render(graphics, graphics.guiWidth(), height, moment, seconds);

        // With the settings open, lay everything out in the part of the screen they don't cover.
        int left = PreviewArea.left();
        int width = graphics.guiWidth() - left;
        graphics.pose().pushPose();
        graphics.pose().translate(left, 0, 0);
        titleIntro.render(graphics, font, width, height, moment);
        if (withKaraoke && karaokeBar.enabled()) {
            karaokeBar.render(graphics, font, width, height, moment, false);
        }
        if (withCard && nowPlayingCard.enabled()) {
            nowPlayingCard.render(graphics, font, width, height, moment, false);
        }
        graphics.pose().popPose();
        if (withCard) {
            debugOverlay.render(graphics, font, moment);
        }
    }

    List<MovableElement> movableElements() {
        return List.of(karaokeBar, nowPlayingCard);
    }

    public LyricsPlayback playback() {
        return playback;
    }
}
