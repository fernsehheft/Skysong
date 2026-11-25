package io.github.fernsehheft.skysong.gui;

/**
 * While the settings are open they cover the left part of the screen. The HUD and new
 * lyrics in the world then move into the part that's still visible, so the preview
 * isn't hidden behind the settings.
 */
public final class PreviewArea {
    /** Left edge of the visible part in GUI pixels, or 0 when nothing covers the screen. */
    private static volatile int left;

    private PreviewArea() {
    }

    static void coverLeft(int pixels) {
        left = Math.max(0, pixels);
    }

    static void clear() {
        left = 0;
    }

    public static int left() {
        return left;
    }

    /**
     * How far right of the screen's centre the centre of the visible part is, from 0
     * (not moved) towards 1 (at the right edge).
     */
    public static float centerShift(int screenWidth) {
        if (left <= 0 || screenWidth <= 0) {
            return 0;
        }
        float visibleCenter = left + (screenWidth - left) / 2f;
        return (visibleCenter - screenWidth / 2f) / (screenWidth / 2f);
    }
}
