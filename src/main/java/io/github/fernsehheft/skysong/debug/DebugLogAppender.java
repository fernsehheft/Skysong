package io.github.fernsehheft.skysong.debug;

import io.github.fernsehheft.skysong.Skysong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Copies what Skysong writes to the game log into the {@link DebugLog}, so every message
 * shows up in the game without touching the places that write them.
 */
public final class DebugLogAppender extends AbstractAppender {
    private DebugLogAppender() {
        super("SkysongDebugLog", null, null, true, Property.EMPTY_ARRAY);
    }

    private static boolean installed;

    /** Starts copying; does nothing if the game doesn't log through Log4j as expected. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        if (LogManager.getLogger(Skysong.LOGGER.getName()) instanceof Logger logger) {
            DebugLogAppender appender = new DebugLogAppender();
            appender.start();
            logger.addAppender(appender);
        }
    }

    @Override
    public void append(LogEvent event) {
        DebugLog.Level level;
        if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.ERROR)) {
            level = DebugLog.Level.ERROR;
        } else if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.WARN)) {
            level = DebugLog.Level.WARN;
        } else if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.INFO)) {
            level = DebugLog.Level.INFO;
        } else {
            // Debug messages are too chatty for the screen.
            return;
        }
        String message = event.getMessage().getFormattedMessage();
        if (event.getThrown() != null) {
            message += ": " + event.getThrown();
        }
        DebugLog.get().add(level, message);
    }
}
