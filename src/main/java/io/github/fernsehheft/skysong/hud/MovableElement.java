package io.github.fernsehheft.skysong.hud;

import io.github.fernsehheft.skysong.lyrics.LyricsPlayback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A HUD element the player can move and resize in the HUD editor. */
interface MovableElement {
    String name();

    boolean enabled();

    /**
     * Where the element is drawn, or null if there's nothing to show.
     *
     * @param moment  what's playing, or null when nothing is
     * @param preview fill in sample content when there's nothing real to show, for the editor
     */
    HudBox box(Font font, int screenWidth, int screenHeight, LyricsPlayback.Moment moment, boolean preview);

    void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                LyricsPlayback.Moment moment, boolean preview);

    /** Moves the element's centre to these fractions of the screen size. */
    void moveTo(double fractionX, double fractionY);

    void resize(double scaleChange);

    void reset();
}
