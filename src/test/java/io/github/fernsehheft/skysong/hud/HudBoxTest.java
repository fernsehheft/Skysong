package io.github.fernsehheft.skysong.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudBoxTest {

    @Test
    void centresOnTheGivenSpot() {
        HudBox box = HudBox.centeredAt(0.5, 0.5, 100, 20, 1f, 400, 200);
        assertEquals(new HudBox(150, 90, 100, 20, 1f), box);
    }

    @Test
    void scaleMakesTheBoxBigger() {
        HudBox box = HudBox.centeredAt(0.5, 0.5, 100, 20, 2f, 400, 200);
        assertEquals(200, box.width());
        assertEquals(40, box.height());
        assertEquals(100, box.x());
    }

    @Test
    void staysOnScreenNearTheEdges() {
        HudBox topLeft = HudBox.centeredAt(0, 0, 100, 20, 1f, 400, 200);
        assertEquals(0, topLeft.x());
        assertEquals(0, topLeft.y());

        HudBox bottomRight = HudBox.centeredAt(1, 1, 100, 20, 1f, 400, 200);
        assertEquals(300, bottomRight.x());
        assertEquals(180, bottomRight.y());
    }

    @Test
    void containsPoints() {
        HudBox box = new HudBox(10, 10, 20, 20, 1f);
        assertTrue(box.contains(10, 10));
        assertTrue(box.contains(29.9, 29.9));
        assertFalse(box.contains(30, 15));
    }
}
