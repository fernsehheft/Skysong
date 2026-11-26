package io.github.fernsehheft.skysong.render;

import io.github.fernsehheft.skysong.Skysong;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The fonts the lyrics can be written in. Apart from Minecraft's own, they come with the
 * mod under the SIL Open Font License (see {@code licenses/fonts} in the jar); letters a
 * font lacks are taken from Minecraft's font.
 */
public enum LyricFont {
    MINECRAFT(null),
    /** Poppins */
    SMOOTH("smooth"),
    /** Bebas Neue */
    BOLD("bold"),
    /** Pacifico */
    SCRIPT("script"),
    /** DM Serif Display */
    ELEGANT("elegant"),
    /** Press Start 2P */
    RETRO("retro"),
    /** Space Mono */
    MONO("mono");

    private final Style style;

    LyricFont(String name) {
        this.style = name == null ? Style.EMPTY : Style.EMPTY.withFont(new ResourceLocation(Skysong.MOD_ID, name));
    }

    public Style style() {
        return style;
    }

    public FormattedCharSequence text(String text) {
        return FormattedCharSequence.forward(text, style);
    }

    public int width(Font font, String text) {
        return font.width(text(text));
    }
}
