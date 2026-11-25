package io.github.fernsehheft.skysong.media;

import java.io.IOException;
import java.util.Optional;

/** Something that can tell us which song is playing right now. */
public interface MediaSource extends AutoCloseable {
    String name();

    /**
     * Asks the player what's playing. Called from a background thread, never from the
     * render thread, so implementations are allowed to block for a short while.
     *
     * @return the current track, or empty when nothing is playing
     * @throws IOException when the player can't be reached at all
     */
    Optional<NowPlaying> poll() throws IOException;

    @Override
    default void close() {
    }
}
