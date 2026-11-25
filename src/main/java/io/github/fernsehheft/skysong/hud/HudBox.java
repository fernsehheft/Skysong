package io.github.fernsehheft.skysong.hud;

/**
 * Where an element sits on screen, in GUI pixels. {@code x, y} is the top left corner
 * and {@code scale} how much the element's own drawing is scaled.
 */
record HudBox(int x, int y, int width, int height, float scale) {

    /**
     * Places an element of {@code width x height} unscaled pixels with its centre at the
     * given fractions of the screen, moved inwards if it would stick out.
     */
    static HudBox centeredAt(double fractionX, double fractionY, int width, int height, float scale,
                             int screenWidth, int screenHeight) {
        int scaledWidth = Math.round(width * scale);
        int scaledHeight = Math.round(height * scale);
        int x = (int) Math.round(fractionX * screenWidth - scaledWidth / 2.0);
        int y = (int) Math.round(fractionY * screenHeight - scaledHeight / 2.0);
        x = Math.max(0, Math.min(screenWidth - scaledWidth, x));
        y = Math.max(0, Math.min(screenHeight - scaledHeight, y));
        return new HudBox(x, y, scaledWidth, scaledHeight, scale);
    }

    boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }
}
