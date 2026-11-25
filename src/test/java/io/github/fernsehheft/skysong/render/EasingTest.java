package io.github.fernsehheft.skysong.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingTest {

    @Test
    void progressStaysBetweenZeroAndOne() {
        assertEquals(0, Easing.progress(-50, 100));
        assertEquals(0.5f, Easing.progress(50, 100));
        assertEquals(1, Easing.progress(500, 100));
    }

    @Test
    void bounceStartsAtZeroAndLandsOnOne() {
        assertEquals(0, Easing.bounceOut(0), 1e-6);
        assertEquals(1, Easing.bounceOut(1), 1e-6);
        for (float t = 0; t <= 1; t += 0.01f) {
            float value = Easing.bounceOut(t);
            assertTrue(value >= 0 && value <= 1.0001f, "at " + t + ": " + value);
        }
    }

    @Test
    void coloursBlend() {
        assertEquals(0x000000, Easing.lerpColor(0x000000, 0xFFFFFF, 0));
        assertEquals(0xFFFFFF, Easing.lerpColor(0x000000, 0xFFFFFF, 1));
        assertEquals(0x808080, Easing.lerpColor(0x000000, 0xFFFFFF, 0.5f));
    }
}
