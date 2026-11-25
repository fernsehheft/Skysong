package io.github.fernsehheft.skysong.media;

import java.util.Locale;
import java.util.Optional;

public final class MediaSources {
    private MediaSources() {
    }

    /** The system media source for the OS we're running on, if we have one for it. */
    public static Optional<MediaSource> forThisSystem() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return Optional.of(new WindowsMediaSource());
        }
        if (os.startsWith("mac")) {
            return Optional.of(new AppleScriptSource());
        }
        if (os.contains("linux") || os.contains("bsd")) {
            return Optional.of(new PlayerctlSource());
        }
        return Optional.empty();
    }
}
