package io.github.fernsehheft.skysong.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Small drawing helpers for the HUD elements. */
final class HudDraw {
    private HudDraw() {
    }

    /** A filled rectangle with rounded corners, drawn as one strip per pixel row near the corners. */
    static void roundedRect(GuiGraphics graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        int r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2);
        graphics.fill(x1, y1 + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            // How far the corner curve is indented on this row.
            double dy = r - row - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            graphics.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
            graphics.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }

    /** Draws text at a position that doesn't have to be a whole pixel, which centred text often isn't. */
    static void text(GuiGraphics graphics, Font font, String text, float x, float y, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    /** {@code rgb} with an alpha from 0 to 1. Minecraft draws text with an alpha below 4 fully opaque, so that's avoided. */
    static int withAlpha(int rgb, float alpha) {
        int alphaByte = Math.max(4, Math.min(255, Math.round(alpha * 255)));
        return (alphaByte << 24) | (rgb & 0xFFFFFF);
    }

    /** Cuts {@code text} to fit {@code maxWidth} pixels, ending it with "..." if needed. */
    static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, maxWidth - font.width(ellipsis)).stripTrailing() + ellipsis;
    }

    static String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
