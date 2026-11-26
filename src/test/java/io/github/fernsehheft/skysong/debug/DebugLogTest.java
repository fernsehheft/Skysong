package io.github.fernsehheft.skysong.debug;

import io.github.fernsehheft.skysong.Skysong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugLogTest {

    @BeforeEach
    void startEmpty() {
        DebugLog.get().clear();
    }

    @Test
    void keepsTheNewestEntries() {
        DebugLog log = DebugLog.get();
        for (int i = 0; i < 400; i++) {
            log.add(DebugLog.Level.INFO, "message " + i);
        }
        List<DebugLog.Entry> latest = log.latest(3);
        assertEquals(List.of("message 397", "message 398", "message 399"),
                latest.stream().map(DebugLog.Entry::message).toList());
        assertTrue(log.asText().startsWith(log.latest(300).get(0).toString()));
        assertTrue(log.asText().contains("message 100"), "300 entries are kept");
    }

    @Test
    void picksUpWhatSkysongLogs() {
        DebugLogAppender.install();
        Skysong.LOGGER.info("Now playing: {} - {}", "Sia", "Snowman");
        Skysong.LOGGER.warn("Something odd");
        Skysong.LOGGER.debug("Too chatty to show");

        List<DebugLog.Entry> latest = DebugLog.get().latest(10);
        assertEquals(2, latest.size(), latest.toString());
        assertEquals("Now playing: Sia - Snowman", latest.get(0).message());
        assertEquals(DebugLog.Level.WARN, latest.get(1).level());
        assertTrue(latest.get(1).toString().contains("WARN Something odd"));
    }
}
